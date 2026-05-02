# Match-Result E2E — Follow-ups

Captured after a full end-to-end run on `match-result` branch (2026-05-02): Flink batch job → train → uvicorn →
`/predict-match`. The flow works, but several issues turned up that are worth fixing or revisiting before this becomes
anything more than a PoC.

---

## 1. Batch job can't overwrite Parquet output

**Symptom.** `./gradlew deployBatchJob` fails with `java.nio.file.FileAlreadyExistsException` when
`data/offline_store/team_stats/team_stats.parquet` (or `matches/matches.parquet`) already exists from a prior run.

**Where.** `pipeline/.../TeamStatsBatchJob.kt` — both `TeamStatsParquetWriter` and `MatchParquetWriter` build a
`LocalOutputFile` at a fixed filename and let Parquet's default "fail if exists" mode bubble up.

**Workaround used.** Manual `rm` of the parquet files before each rerun.

**Options for a real fix.**
- Delete the file in the writer's `init` block (simplest, matches "single-file PoC sink" intent).
- Switch to a timestamped filename per run (`team_stats-<ts>.parquet`) — slightly closer to a real partitioned layout.
- Use Flink's `FileSink` with rolling policy (proper fix; bigger change).

**Note.** The original plan explicitly accepted this for a one-shot backfill (Section 9, "Risks & Open Questions").
Fix only becomes necessary once the job is run more than ad-hoc.

---

## 2. Docker volumes bind to a fixed checkout, not the active worktree

**Symptom.** Containers were started from `/Users/.../workspace/streaming-ml-feature-store` (the main checkout) — the
`flink-taskmanager` mount resolves to that directory's `./data`. Running the batch job from a git worktree writes
parquet to the **main checkout**, not the worktree's `data/`. Training from the worktree then sees an empty
`data/offline_store/matches/`.

**Workaround used.** Copied `matches.parquet` and `team_stats.parquet` from the main checkout into the worktree.

**Options for a real fix.**
- Document the constraint in `README.md` ("docker compose must be (re)started from the worktree you're running the
  pipeline against").
- Or make the bind path configurable (`DATA_DIR` env var → `${DATA_DIR:-./data}:/data`) so worktrees can override
  without restarting infra.
- Or restructure to a named volume + a one-shot copy step into the worktree.

---

## 3. Baseline log-loss returns `NaN`

**Symptom.** `train_match_result.py` prints `Baseline (Pinnacle implied) log-loss: nan`.

**Cause.** Some rows in `matches.parquet` have null Pinnacle odds. `1.0 / odds` produces NaN, which propagates through
`probs / probs.sum(...)` and the final mean.

**Where.** `training/train_match_result.py` — `odds_implied_logloss()`.

**Fix sketch.**
```python
def odds_implied_logloss(matches: pd.DataFrame) -> float:
    df = matches.dropna(subset=ODDS)
    odds = df[ODDS].values.astype(np.float64)
    inv = 1.0 / odds
    probs = inv / inv.sum(axis=1, keepdims=True)
    y = df["result"].map(LABEL_MAP).values
    eps = 1e-15
    picked = probs[np.arange(len(y)), y]
    return float(-np.log(np.clip(picked, eps, 1.0)).mean())
```

The training matrix itself is fine — XGBoost handles NaN natively, and the trainer doesn't filter on odds presence
intentionally (more rows = more training signal).

---

## 4. Model isn't actually learning yet

**Symptom.** First trained model has test mlogloss **1.116**, which is *worse* than a uniform-random 3-class baseline
(`-ln(1/3) ≈ 1.099`). Train mlogloss drops to 0.850 — clearly overfitting.

**Likely contributors.**
- 100 boosting rounds with no early stopping.
- Time-split cutoff at 80% lands across multiple seasons; the train and test distributions probably differ
  (team strengths shift season to season, new teams appear via promotion).
- `team_stats` features are *cumulative* and never reset between seasons, so by late seasons every team looks
  similar (everyone has hundreds of matches played).
- Only 6 team-stats features per side + 3 odds — lean.

**Things to try (in order of cheapness).**
- Add `early_stopping_rounds=10` and let the test-loss curve pick the round count.
- Per-season reset of `team_stats` (changes the Flink job; bigger).
- Switch from cumulative counts to **rolling window** stats (last N matches, last K days) — almost certainly the right
  long-term feature shape.
- Add form features (recent form, head-to-head).
- Compare against the (fixed) odds-implied baseline as the bar to beat.

This is a feature-engineering problem more than a hyperparameter one — punt until the rolling-window features are in.

---

## 5. Team-name normalisation only matches what the producer wrote

**Symptom.** First `/predict-match` call with `home_team=flamengo` returned 404. Redis actually stores `flamengo_rj`
because the source CSV uses suffixed names (`Flamengo RJ`, `Botafogo RJ`, `Atletico MG`, etc.).

**Where.** `MatchToMatchRowMap` and `MatchToTeamRecordFlatMap` both use `TeamNames.normalize` (lowercase + underscore),
which preserves whatever the CSV provided. So `Flamengo RJ` → `flamengo_rj`.

**Implications.**
- The serving endpoint requires the caller to know the producer-side spelling. That's leaky.
- `matches.parquet` and `team_stats` agree with each other (good — the Feast join works), but anyone hitting the API
  has to look up team names in the CSV.

**Options.**
- Add an alias map (`flamengo` → `flamengo_rj`) in the serving feature_store layer — quick, but creates a parallel
  source of truth.
- Strip `_rj`/`_mg`/`_sc` suffixes during normalisation — risky, because some teams genuinely share base names
  (`atletico_mg` vs `atletico_go` vs `atletico-pr`).
- Document the canonical names alongside the API and accept that as the contract.

The 404 itself is the right behaviour ("team_stats not found for: ['home']"); only the discoverability is the issue.

---

## 6. Training environment isn't pinned

**Symptom.** Running `train_match_result.py` from a fresh conda env required ad-hoc installs:
`feast[redis]==0.40.0`, `xgboost`, `pyarrow`. There's no `training/requirements.txt`.

**Fix.** Add `training/requirements.txt`:
```
feast[redis]==0.40.0
xgboost>=2.0
pandas>=2.0
pyarrow>=14.0
numpy
```
And a one-line setup note in the README. The Feast version must match `feast/requirements.txt` (server side) to keep
registry compatibility — pin it explicitly.

Same situation in serving (`httpx` was missing for `TestClient`-based tests). Add `httpx` to `serving/requirements.txt`
under a `[test]` extra or a separate `requirements-dev.txt`.

---

## Summary table

| # | Issue                                          | Severity | Fix size |
|---|------------------------------------------------|----------|----------|
| 1 | Parquet writer fails on rerun                  | Medium   | Small    |
| 2 | Docker volume bound to main checkout           | Low      | Small (docs) or Medium (env var) |
| 3 | Baseline log-loss is NaN                       | Low      | Trivial  |
| 4 | Model loses to uniform-random baseline         | High     | Large (feature work) |
| 5 | Team-name spelling leaks producer convention   | Medium   | Small to Medium |
| 6 | Training/test deps not pinned                  | Low      | Trivial  |

Issues 1, 3, and 6 are quick wins. Issue 4 is the only one that actually blocks "useful predictions" and is also the
most interesting — it's about features, not plumbing.
