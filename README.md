# Decision Tree PoC

Learning project exploring feature engineering and ML model prediction using XGBoost,
with a real-time streaming pipeline built on Kafka and Flink.

---

## Current State

```
BRA.csv ──► Producer ──► Kafka ──► Flink (Kotlin) ──► Redis
                                                          │
                                              XGBoost ◄──┘
                                            (notebook)
```

A streaming pipeline that ingests Brazilian Série A match events, computes cumulative
team statistics, and stores them in Redis as a feature store.

### Components

| Layer | Technology | Language |
|-------|------------|----------|
| Event streaming | Kafka | - |
| Stream processing | Flink 2.2 | Kotlin |
| Feature store | Redis | - |
| ML model | XGBoost | Python |

### Feature Schema

Team stats are stored as Redis Hashes keyed by team name:

```
team_stats:{team_name}

team_stats:palmeiras  →  { matchesPlayed, wins, draws, losses, goalsFor, goalsAgainst }
```

---

## Target State

A full production ML lifecycle requires two distinct pipelines: **offline** for model
training and **online** for real-time inference. Feast acts as the feature store layer,
keeping both stores in sync through a single sink — eliminating training/serving skew.

```
                        OFFLINE PIPELINE (one-time backfill)
                        ─────────────────────────────────────────────────
  BRA.csv ──► Flink Batch ──► pre-computed features (Parquet)
              (aggregations)          │
                                      ▼
                                feast materialize ──► Parquet/S3 (offline store)
                                                 └──► Redis (online store)


                        ONLINE PIPELINE (continuous)
                        ─────────────────────────────────────────────────
  Kafka ──► Flink Stream ──► Feast push ──► Parquet/S3 (offline store)
            (aggregations)            └──► Redis (online store)


                        TRAINING (scheduled or drift-triggered)
                        ─────────────────────────────────────────────────
  feast get_historical_features() ──► XGBoost training ──► Model Registry


                        SERVING
                        ─────────────────────────────────────────────────
  Match event ──► API ──► Redis (online store) ──► XGBoost ──► Prediction
                                                         │
                                        Monitoring / Drift Detection
                                                         │
                                              Retrain Trigger
```

### Offline Pipeline

Runs **once** as a **Flink Batch job** (`RuntimeExecutionMode.BATCH`) to backfill
aggregated features from historical data (BRA.csv) into Parquet format. Feast then
materializes these pre-computed features into both the offline (Parquet/S3) and online
(Redis) stores via `feast materialize`.

This step is only needed when:
- Setting up the system for the first time
- Adding a new feature that requires historical recomputation

### Online Pipeline

Runs **continuously** as a **Flink Streaming job** (what we have today). As new match
events arrive via Kafka, Flink computes the same aggregations and pushes to Feast, which
writes to **both stores simultaneously**:

```kotlin
// RedisSink replaced by a single Feast push
store.push("match_stats_push_source", features, to = PushMode.ONLINE_AND_OFFLINE)
```

Feast's feature server (`feast serve`) exposes an HTTP endpoint that Flink calls,
replacing the direct Redis sink. This is the only sink needed — Feast handles the rest.

### Feature Store (Feast)

Feast manages two backing stores:

| Store | Technology | Purpose |
|-------|------------|---------|
| Online store | Redis | Low-latency feature serving at inference time |
| Offline store | Parquet / S3 | Historical features for model training |

Both stores are always in sync — written together on every Flink push.

### Retraining

Triggered on a schedule or when model drift is detected. Uses
`feast get_historical_features()` to read point-in-time correct features from the
offline store, guaranteeing the training dataset reflects what the model would have
seen at prediction time (no data leakage).

### Model Registry

Versioned storage for trained model artifacts (e.g. MLflow, SageMaker Model Registry).

- Each training run produces a versioned artifact
- Serving layer loads the current production version at startup
- Supports rollback and A/B testing between versions

### Feedback Loop

Captures actual match outcomes versus model predictions to:

- Measure model performance over time
- Detect concept drift (when match dynamics change)
- Trigger retraining when performance falls below a threshold

---

## Getting Started

### Prerequisites

- Docker + Docker Compose
- JDK 21+
- Gradle

### Start infrastructure

```bash
docker compose up -d
```

Starts: Kafka (`localhost:29092`), Flink (`localhost:8081`), Redis (`localhost:6379`),
RedisInsight (`localhost:5540`), Kafka UI (`localhost:8082`).

### Deploy the Flink job

```bash
cd pipeline
./gradlew deployFlink
```

### Run the producer

```bash
./gradlew runProducer
```

Reads `notebooks/bra_serie_a/BRA.csv` and publishes match events to Kafka.
Flink processes them and writes team stats to Redis.

### Verify

```bash
docker exec redis redis-cli hgetall team_stats:palmeiras
```
