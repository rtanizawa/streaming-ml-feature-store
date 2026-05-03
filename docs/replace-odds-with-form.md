# Plan: Replace Odds Features with Recent-Form Features

## Goal

Today the match-result classifier (`training/train_match_result.py`) leans heavily on closing Pinnacle odds. Those
odds are not available at inference time in any realistic deployment — they encode the market's view of the match,
which is itself a prediction. We want to remove the three `odds_pinnacle_*` features and replace them with
**point-in-time recent form** signals computed in the streaming pipeline.

Scope of this plan: add a `form_last_5` feature set (rolling W/D/L counts and goals over the last 5 matches per team)
to the existing `team_stats` feature view, retrain the classifier without odds, and update the serving path.

Out of scope (follow-ups, see end): Elo rating, home/away-split form, head-to-head, rest days.

---

## Target Data Flow

```
Kafka topic: bra-serie-a-matches
  └─ Flink Batch job (TeamStatsBatchJob)
       ├─ FlatMap → TeamMatchRecord → keyBy(team) → TeamStatsFunction
       │     ├─ existing cumulative stats (matches_played, wins, …)        ── unchanged
       │     └─ NEW: rolling last-5 window (form_wins, form_draws, …)      ── this plan
       └─ Map → MatchRow → ParquetSink (matches/)                          ── unchanged

Training: same point-in-time join via Feast, but feature list grows by 5 form fields per side
          and drops the 3 odds columns.

Serving: /predict-match request body no longer takes odds; the scorer reads the new form
         fields from the Feast online store.
```

---

## 1. Pipeline — extend `TeamStats` with a rolling window

### 1.1 Domain model

File: `pipeline/src/main/kotlin/com/rtagui/model/TeamStats.kt`

Add 5 fields for the last-5 window:

```kotlin
val formWins: Int = 0,
val formDraws: Int = 0,
val formLosses: Int = 0,
val formGoalsFor: Int = 0,
val formGoalsAgainst: Int = 0,
```

Keep the cumulative fields — we don't need to throw them away, the model just won't use them initially.

### 1.2 Aggregation logic

File: `pipeline/src/main/kotlin/com/rtagui/transforms/TeamStatsFunction.kt`

The current function only holds a single `ValueState<TeamStats>`. To compute a rolling window we need to remember
the last 5 `TeamMatchRecord`s per team. Add a `ListState<TeamMatchRecord>` (or a fixed-size deque encoded as a
`ValueState<List<TeamMatchRecord>>`):

1. In `open()`, register a second state descriptor for the recent-records list.
2. In `processElement`:
   - Read the existing list, append the incoming record, trim to the last 5.
   - Recompute `formWins`/`formDraws`/`formLosses`/`formGoalsFor`/`formGoalsAgainst` by summing over that list.
   - Update both states; emit the new `TeamStats`.
3. Note the existing function increments cumulative counts using the *current* record. The form window must
   include the current record too (so we stay consistent and the point-in-time `-1ms` trick in training already
   handles leakage).

### 1.3 Avro schema + Parquet sink

File: `pipeline/src/main/kotlin/com/rtagui/jobs/TeamStatsBatchJob.kt`

In the `teamStatsSchema` builder, add the 5 new int fields with sensible defaults of 0 (so old rows still parse
if you ever read them with the new schema):

```kotlin
.name("form_wins").type().intType().intDefault(0)
.name("form_draws").type().intType().intDefault(0)
.name("form_losses").type().intType().intDefault(0)
.name("form_goals_for").type().intType().intDefault(0)
.name("form_goals_against").type().intType().intDefault(0)
```

In `TeamStatsParquetWriter.write`, set the matching fields from the new `TeamStats` properties.

### 1.4 Tests

File: `pipeline/src/test/kotlin/com/rtagui/transforms/TeamStatsFunctionTest.kt`

Add cases:

- After 1, 3, 5, and 7 matches, `form_*` reflects only the last 5.
- Goals/results outside the window are excluded once a 6th match arrives.
- The first emission for a new team has form counts based on that single match (not zeros).

Run: `cd pipeline && ./gradlew test`.

---

## 2. Feast — register the new feature fields

File: `feast/features.py`

Add to the `team_stats_fv` schema:

```python
Field(name="form_wins",          dtype=Int32),
Field(name="form_draws",         dtype=Int32),
Field(name="form_losses",        dtype=Int32),
Field(name="form_goals_for",     dtype=Int32),
Field(name="form_goals_against", dtype=Int32),
```

After regenerating the offline parquet (step 4 below), apply the changes:

```bash
cd feast
feast apply
./materialize.sh   # if you want the new fields online for serving
```

---

## 3. Training — drop odds, add form

File: `training/train_match_result.py`

1. Replace the `FEATURES` constant:

   ```python
   FEATURES = [
       "matches_played", "wins", "draws", "losses", "goals_for", "goals_against",
       "form_wins", "form_draws", "form_losses", "form_goals_for", "form_goals_against",
   ]
   ```

2. Remove the `ODDS` constant and stop concatenating odds in `assemble_dataset`:

   ```python
   X_df = pd.concat([home, away], axis=1)
   ```

3. Keep `odds_implied_logloss` — it's still useful as a baseline to compare against, even though we no longer
   train on odds. The print line in `main()` stays.

4. Optional: bump `num_boost_round` modestly (e.g. 150) — the model now has weaker signals and may benefit from
   more rounds. Check the test log-loss curve to confirm it's not overfitting.

### Training tests

File: `training/tests/` — find the existing test for `assemble_dataset` / feature ordering and update it to
expect the new feature list (and no odds columns).

Run: `cd training && pytest`.

---

## 4. Regenerate offline data

The new form fields don't exist in the current `data/offline_store/team_stats/` parquet, so retrain requires a
fresh batch run.

```bash
docker compose up -d                    # Kafka + Redis + Feast feature server
cd pipeline
./gradlew runProducer                   # replays Brasileirão CSV into Kafka
./gradlew deployBatchJob                # writes new team_stats.parquet + matches.parquet
```

Verify the new columns landed:

```bash
python - <<'PY'
import pandas as pd
df = pd.read_parquet("data/offline_store/team_stats/team_stats.parquet")
print(df.columns.tolist())
print(df.tail(3))
PY
```

You should see the 5 `form_*` columns populated with non-trivial values for teams with ≥ 1 match played.

Then retrain:

```bash
cd training
python train_match_result.py
```

---

## 5. Serving — drop odds from the API

### 5.1 Scorer

File: `serving/app/services/match_scorer.py`

- Add the 5 form names to `TEAM_FEATURES` (mirror the training list exactly — order matters for `FEATURE_ORDER`).
- Delete `ODDS_FEATURES` and remove it from `FEATURE_ORDER`.

### 5.2 Online feature fetch

File: `serving/app/services/feature_store.py`

Extend `_FEATURE_REFS`:

```python
"team_stats:form_wins",
"team_stats:form_draws",
"team_stats:form_losses",
"team_stats:form_goals_for",
"team_stats:form_goals_against",
```

### 5.3 Request schema

File: `serving/app/routers/predict_match.py`

- Drop `odds_pinnacle_home` / `_draw` / `_away` from `MatchPredictRequest`.
- Remove the three `flat["odds_pinnacle_*"] = …` lines.

### 5.4 Tests

File: `serving/tests/` — update any test fixtures that send odds in the body and any mocked Feast responses
to include the new form fields.

Run: `cd serving && pytest`.

### 5.5 Update `AGENTS.md`

The `curl` example at the bottom of `AGENTS.md` still sends odds in the request body. Update it to drop those
fields and update the expected response if anything changes shape.

---

## 6. Rollout & verification

Local end-to-end smoke test:

```bash
docker compose up -d
cd pipeline && ./gradlew runProducer && ./gradlew deployBatchJob
cd ../feast && feast apply && ./materialize.sh
cd ../training && python train_match_result.py
cd ../serving && uvicorn main:app --reload
```

In another shell:

```bash
docker exec redis redis-cli hgetall team_stats:sao_paulo
# expect: form_wins, form_draws, … keys present alongside the cumulative ones

curl -X POST http://localhost:8000/predict-match \
  -H "Content-Type: application/json" \
  -d '{"home_team": "sao_paulo", "away_team": "santos"}'
# expect: 200 with a probabilities object summing to ~1.0
```

### Success criteria

- Train log-loss is materially better than `1.0986` (uniform 1/3 baseline).
- Test log-loss is reported alongside the Pinnacle baseline. Don't expect to *beat* the market — anything within
  ~0.05 of the Pinnacle baseline is a strong outcome for form-only features.
- `/predict-match` no longer requires odds in the request body.

### Failure modes to watch

- **Cold-start teams.** Early-season matches give form windows of size < 5 with mostly zeros. Confirm the
  training loss curve doesn't show the model collapsing onto "predict draw." If it does, consider filtering out
  the first 3 matchdays from the training set.
- **Materialisation lag.** `feast materialize` must run after the batch job; otherwise the online store will
  return the old schema and `feature_store.get_features` will return `None`, causing 404s from the API.
- **Feature ordering drift.** The scorer's `FEATURE_ORDER` *must* exactly match training-time column order. A
  unit test that asserts the list against a hard-coded golden value is the cheapest insurance.

---

## Follow-ups (not in this plan)

Once form-only is working, the next signals to add (in order of expected value):

1. **Home/away-split form** — separate `home_form_*` / `away_form_*` aggregations. Big change for what is
   probably the single biggest factor in match outcome.
2. **Elo or Glicko rating per team** — one float per team, updated after each match. Naturally encodes
   strength-of-opponent.
3. **Rest days / fixture congestion** — days since each team's last match, computable from `MatchRow`.
4. **Head-to-head** — last N results between the specific pair of teams.
