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
training and **online** for real-time inference. Both must compute features identically
to avoid training/serving skew.

```
                   ┌─────────────────────────────────────────────┐
                   │                OFFLINE PIPELINE              │
                   │                                             │
  Data Lake  ──────►  Flink Batch  ──►  Feature Export  ──►  Model Training
  (CSV/S3)   │    │  (same transforms)       │              (XGBoost)
             │    │                          │                    │
             │    └──────────────────────────┼────────────────────┼──┘
             │                               │                    │
             │                         Training Dataset    Model Registry
             │                                                    │
             │    ┌───────────────────────────────────────────────┼──┐
             │    │                ONLINE PIPELINE                │  │
             │    │                                               ▼  │
             └────►  Kafka  ──►  Flink Stream  ──►  Redis  ──►  Serve
                  │  (live        (same              (feature    (API)
                  │  events)      transforms)         store)      │
                  │                                               │
                  │              Monitoring / Drift Detection ◄───┘
                  │                        │
                  └────────── Retrain Trigger ──► Offline Pipeline
                  └────────────────────────────────────────────────┘
```

### Offline Pipeline

Processes **bounded, historical data** (CSV, data lake) to:

- Compute features over the full match history
- Export a training dataset with consistent feature definitions
- Train and register a new model version

Runs as a **Flink Batch job** (`RuntimeExecutionMode.BATCH`) using the same transform
code as the streaming pipeline, guaranteeing feature consistency.

**Triggers:**
- Scheduled (e.g. weekly retrain)
- On-demand when model performance degrades or data drift is detected

### Online Pipeline

Processes **unbounded, live event data** (Kafka) to:

- Compute features incrementally as match events arrive
- Write updated features to Redis in real time
- Serve features to the inference API at prediction time

Runs as a **Flink Streaming job** (what we have today) with checkpointing enabled so
it resumes correctly after restarts without reprocessing old events.

### Feature Store (Redis)

Sits at the intersection of both pipelines — the single source of features for inference.

- **Offline** writes the historical baseline at retrain time
- **Online** overwrites with live updates as new events arrive
- **Serving layer** reads from Redis at prediction time

Using the same feature store for training and serving eliminates training/serving skew.

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
