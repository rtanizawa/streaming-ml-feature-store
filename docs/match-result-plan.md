# Plan: Match-Result Prediction (Option 2)

## Context

Today the pipeline reduces every `SoccerMatchEvent` into two `TeamMatchRecord`s
(`pipeline/src/main/kotlin/com/rtagui/transforms/MatchToTeamRecordFlatMap.kt`) and aggregates them into per-team
cumulative stats. By the time data lands in the offline Parquet store / Feast online store, the match-level context
(opponent, home/away pairing, closing odds, final result) is gone.

To train a 3-class match-result classifier (H / D / A) we need to keep that context. Option 2 adds a **second sink** to
the Flink job that writes one **match-level row** per Kafka event to a new offline table. Team-stats features are then
joined back at training time via `feast.get_historical_features()`, which guarantees a point-in-time-correct join.

---

## Target Data Flow

```
Kafka topic: bra-serie-a-matches
  └─ Flink Batch job (TeamStatsBatchJob — modified)
       ├─ FlatMap → TeamMatchRecord → keyBy(team) → TeamStatsFunction → ParquetSink (existing — team_stats/)
       └─ Map → MatchRow → ParquetSink (new — matches/)

Training (notebook / training/train_match_result.py):
  1. Read matches/*.parquet                       # one row per match: teams, odds, result, timestamp
  2. Build two entity_dfs (home + away) keyed by team_name with event_timestamp = match kickoff
  3. feast.get_historical_features(team_stats:*)   # point-in-time correct stats for each side
  4. Concatenate home_* / away_* feature columns + odds columns
  5. Label = result ∈ {H, D, A}; train xgb multi:softprob (num_class=3)

Online inference: same 6 team-stats features per side + current odds → predict.
```

The team-stats branch and the new match branch share the same source and run in the same job — no extra Kafka consumer,
no extra deployment.

---

## 1. New Domain Model — `MatchRow`

File: `pipeline/src/main/kotlin/com/rtagui/model/MatchRow.kt`

```kotlin
data class MatchRow(
    val eventId: String,
    val matchTimestamp: Long,        // epoch millis — Feast `event_timestamp` for entity joins
    val homeTeam: String,            // normalised: lowercase, underscores (matches team_stats entity key)
    val awayTeam: String,            // normalised
    val homeGoals: Int?,
    val awayGoals: Int?,
    val result: String?,             // "H" | "D" | "A" | null (future fixtures)
    val oddsPinnacleHome: Double?,
    val oddsPinnacleDraw: Double?,
    val oddsPinnacleAway: Double?,
    val oddsAvgHome: Double?,
    val oddsAvgDraw: Double?,
    val oddsAvgAway: Double?,
)
```

Reasoning:
- One row per match (not per team) — preserves the link the team-level pipeline destroys.
- We keep Pinnacle and market-average odds only; max odds rarely add signal beyond the average for a 3-class model.
- `eventId` is carried through for de-dup / debugging; not used as an entity key.
- Team names are pre-normalised in the mapper so they join cleanly to `team_stats.team_name`.

---

## 2. New Transform — `MatchToMatchRowMap`

File: `pipeline/src/main/kotlin/com/rtagui/transforms/MatchToMatchRowMap.kt`

`MapFunction<SoccerMatchEvent, MatchRow>` that:
- Parses `matchDate` (`YYYY-MM-DD`) → epoch millis at UTC midnight (same convention as `MatchToTeamRecordFlatMap`).
- Lowercases + underscore-normalises team names (one helper, reused by `MatchToTeamRecordFlatMap` if convenient).
- Copies odds + result through untouched.

No filtering: rows with `result == null` are still useful as inference targets / future fixtures, and Feast's offline
joins will simply not find features for events past the training cutoff. The trainer filters on `result IS NOT NULL`.

---

## 3. Pipeline Wiring

### 3a. `TeamStatsBatchJob.kt` — add a second sink branch

```kotlin
val source = env.fromSource(...)          // shared SoccerMatchEvent stream

// Existing branch — unchanged
source.flatMap(MatchToTeamRecordFlatMap())
      .keyBy(TeamMatchRecord::teamName)
      .process(TeamStatsFunction())
      .setParallelism(1)
      .sinkTo(ParquetSink(config.feast.offlineStorePath, teamStatsSchema))

// New branch
source.map(MatchToMatchRowMap())
      .setParallelism(1)
      .sinkTo(MatchParquetSink(config.feast.matchesOfflinePath, matchSchema))
```

Both branches consume from the same `KafkaSource` operator — Flink fans out automatically.

### 3b. `VelocityFeatureJob.kt` — out of scope for now

The streaming job currently pushes only team_stats to Feast. Match rows are needed *offline* for training; we don't
need a live HTTP push. If we later want a `current_match` online feature view (e.g. for live odds at kickoff), we add
it in a follow-up. **YAGNI for this task.**

---

## 4. Parquet Schema (matches table)

File path: `data/offline_store/matches/matches.parquet`

| Column                | Type                | Notes                                                             |
|-----------------------|---------------------|-------------------------------------------------------------------|
| `event_id`            | STRING              | de-dup key                                                        |
| `event_timestamp`     | TIMESTAMP_MILLIS    | match kickoff (UTC midnight on match date)                        |
| `home_team`           | STRING              | normalised — joins to `team_stats.team_name`                      |
| `away_team`           | STRING              | normalised                                                        |
| `home_goals`          | INT32 (nullable)    |                                                                   |
| `away_goals`          | INT32 (nullable)    |                                                                   |
| `result`              | STRING (nullable)   | "H" / "D" / "A"                                                   |
| `odds_pinnacle_home`  | DOUBLE (nullable)   |                                                                   |
| `odds_pinnacle_draw`  | DOUBLE (nullable)   |                                                                   |
| `odds_pinnacle_away`  | DOUBLE (nullable)   |                                                                   |
| `odds_avg_home`       | DOUBLE (nullable)   |                                                                   |
| `odds_avg_draw`       | DOUBLE (nullable)   |                                                                   |
| `odds_avg_away`       | DOUBLE (nullable)   |                                                                   |

Avro schema lives next to the team_stats one in `TeamStatsBatchJob.kt` (or factor both into a small `Schemas.kt` if it
gets cluttered — defer until the second is written and we can see the shape).

A new `MatchParquetSink` mirrors the existing `ParquetSink` but writes `MatchRow` and the matches schema. Most of the
boilerplate is identical; we'll copy first and DRY only if it gets noisy (YAGNI).

---

## 5. Feast Wiring (minimal)

For training we **don't need a new FeatureView** for matches. The matches Parquet is the *entity source* (label table),
not a feature table. Feast's `get_historical_features()` accepts an `entity_df` from any DataFrame.

Two options, in order of simplicity:

1. **No Feast change.** Trainer reads `matches.parquet` directly with pandas, builds two `entity_df`s from it, and
   calls `store.get_historical_features()` against the existing `team_stats` view. — **Recommended.**
2. Register a `match_results` FeatureView later if we want odds to be served online via Feast. Not needed for training.

We'll do (1). Zero changes to `feast/features.py` for this task.

---

## 6. Training — `training/train_match_result.py`

New script (the existing `training/train.py` keeps its current "high performer" toy model untouched).

```python
import pandas as pd
import xgboost as xgb
from feast import FeatureStore
from sklearn.model_selection import train_test_split

FEATURES = ["matches_played", "wins", "draws", "losses", "goals_for", "goals_against"]
ODDS = ["odds_pinnacle_home", "odds_pinnacle_draw", "odds_pinnacle_away"]
LABEL_MAP = {"H": 0, "D": 1, "A": 2}

matches = pd.read_parquet("data/offline_store/matches/")
matches = matches.dropna(subset=["result"])
matches["event_timestamp"] = pd.to_datetime(matches["event_timestamp"], unit="ms", utc=True)

store = FeatureStore(repo_path="feast")

def join(side: str) -> pd.DataFrame:
    entity_df = matches[[f"{side}_team", "event_timestamp"]].rename(columns={f"{side}_team": "team_name"})
    feats = store.get_historical_features(
        entity_df=entity_df,
        features=[f"team_stats:{f}" for f in FEATURES],
    ).to_df()
    return feats[FEATURES].add_prefix(f"{side}_")

X = pd.concat([join("home"), join("away"), matches[ODDS].reset_index(drop=True)], axis=1)
y = matches["result"].map(LABEL_MAP).values

# train_test_split with shuffle=False if we want time-respecting split — TBD
# multi:softprob, num_class=3, eval_metric=mlogloss
```

Open questions to settle when implementing:
- **Time-respecting split**: shuffle=False with a date cutoff is more honest than random split — pick one.
- **Baseline**: log-loss of "always pick implied probability from Pinnacle odds" — must beat this to be worth anything.

---

## 7. Files to Create / Modify

| File | Action | Reason |
|------|--------|--------|
| `pipeline/src/main/kotlin/com/rtagui/model/MatchRow.kt` | Create | New match-level domain model |
| `pipeline/src/main/kotlin/com/rtagui/transforms/MatchToMatchRowMap.kt` | Create | SoccerMatchEvent → MatchRow |
| `pipeline/src/main/kotlin/com/rtagui/jobs/TeamStatsBatchJob.kt` | Modify | Add second sink branch + matches schema |
| `pipeline/src/main/resources/application.yml` | Modify | Add `feast.matchesOfflinePath` |
| `pipeline/src/main/kotlin/com/rtagui/config/AppConfig.kt` | Modify | Add `matchesOfflinePath` field |
| `data/offline_store/matches/.gitkeep` | Create | Track empty dir |
| `pipeline/src/test/kotlin/com/rtagui/transforms/MatchToMatchRowMapTest.kt` | Create | Unit test — field mapping + normalisation |
| `pipeline/src/test/kotlin/com/rtagui/jobs/TeamStatsBatchJobIT.kt` (optional) | Create | E2E: Kafka → both Parquet files |
| `training/train_match_result.py` | Create | New trainer, multi-class softprob |
| `training/tests/test_train_match_result.py` | Create | Smoke: builds X/y from a tiny fixture matches.parquet |

The streaming `VelocityFeatureJob` and its `FeastHttpPushSink` are **not touched**.

---

## 8. Implementation Order

```
1. MatchRow model                              — domain type, no deps
2. MatchToMatchRowMap + unit test              — pure transform, easy to verify
3. Avro schema + MatchParquetSink              — copy from existing ParquetSink
4. AppConfig + application.yml                 — new path entry
5. Wire second branch in TeamStatsBatchJob     — verify both Parquet files appear
6. (Optional) IT covering both branches end-to-end
7. training/train_match_result.py              — wire Feast historical join
8. Trainer test + a baseline log-loss number   — confirm model > odds-implied baseline
```

---

## 9. Risks & Open Questions

| Risk / Question | Mitigation |
|-----------------|-----------|
| Team-name normalisation drift between match rows and team_stats rows breaks the join | Extract a single `normalize(team)` helper used by both `MatchToMatchRowMap` and `MatchToTeamRecordFlatMap` (small refactor — only do it once both call sites exist). |
| Point-in-time leakage: `team_stats.event_timestamp` for a team includes the *current* match if the team-stats sink processes it before the trainer's join | The team_stats `event_timestamp` is set to `record.matchTimestamp` *after* incrementing the counters. So at `t = match_timestamp` we already see the match's outcome. Fix: in the trainer, use `entity_df.event_timestamp = match_timestamp - 1ms`, OR change `TeamStatsFunction` to emit two records (pre/post). Decide before implementing — the `-1ms` trick is the YAGNI option. |
| Single-file Parquet writer (`matches.parquet`) is overwritten on each batch run | Same behaviour as the existing `team_stats.parquet` — acceptable for a PoC, run once per backfill. |
| 3-class label imbalance (draws are rarer) | Use `eval_metric=mlogloss`; consider `class_weight` only if the confusion matrix shows the model never predicts D. |
| Beating the bookies is hard | The honest goal here is "match the closing-odds implied distribution." If we're within ~1-2% log-loss of it, the pipeline works; beating it is a stretch goal. |

---

## 10. Out of Scope (Explicit YAGNI)

- Live `match_results` FeatureView in Feast — only matters if we want odds served via online store. Not needed for
  training.
- Streaming push of match rows to Feast — see above.
- Hyperparameter tuning, calibration, betting-strategy backtests — separate task.
- Removing the existing `training/train.py` toy model — leave it; add the new trainer alongside.
