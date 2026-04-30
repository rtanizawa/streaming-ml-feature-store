# Streaming ML Feature Store

Learning project exploring feature engineering and ML model prediction using XGBoost,
with a real-time streaming pipeline built on Kafka and Flink.

---

## Current State

```
OFFLINE PIPELINE (one-time backfill)
────────────────────────────────────────────────────────────────────────────────────────────────────────────
BRA.csv ──► Producer ──► Kafka ──► Flink Batch ──► Parquet
                                   (TeamStatsBatchJob)   (offline store)


ONLINE PIPELINE (continuous)
─────────────────────────────────────────────────────────────────────────────────────────────────────────────
BRA.csv ──► Producer ──► Kafka ──► Flink Stream ──► Feast feature server
                                   (VelocityFeatureJob)        │
                                                               ▼
                                                       Redis (online store)


TRAINING (manual)
─────────────────────────────────────────────────────────────────────────────────────────────────────────────
Parquet (offline store) ──► XGBoost training ──► model.ubj


SERVING
─────────────────────────────────────────────────────────────────────────────────────────────────────────────
HTTP request ──► FastAPI ──► Feast feature server ──► Redis ──► XGBoost ──► Prediction
```

Brazilian Série A match events flow through two parallel Flink jobs: a one-shot batch
job that backfills the Parquet offline store, and a streaming job that pushes features
to the Feast feature server, which writes to Redis. Training reads from Parquet;
serving reads from Redis through Feast.

### Components

| Layer | Technology | Language |
|-------|------------|----------|
| Event streaming | Kafka | - |
| Stream processing | Flink 2.2 | Kotlin |
| Feature store | Feast | Python |
| Online store | Redis | - |
| Offline store | Parquet (local file) | - |
| Model training | XGBoost | Python |
| Model serving | FastAPI + XGBoost | Python |

### Feature Schema

Features are managed by Feast as the `team_stats` feature view, keyed by `team_name`.
Online reads go through the Feast feature server (`POST /get-online-features`), which
fetches from Redis; offline reads come from Parquet under
`data/offline_store/team_stats/`.

Fields: `matches_played`, `wins`, `draws`, `losses`, `goals_for`, `goals_against`.

The streaming job pushes features through Feast's HTTP push API rather than writing to
Redis directly, so the same code path that updates the online store can also append to
the offline store — eliminating training/serving skew.

---

## What's Missing

The pieces below are still aspirational — everything else in the diagram is wired up
end to end.

### Model Registry

Versioned storage for trained model artifacts (e.g. MLflow, SageMaker Model Registry).
Today, training writes directly to `serving/models/model.ubj` and the serving layer
loads that single file. A registry would enable rollback, A/B testing, and a clear
"current production version" pointer.

### Feedback Loop

Capture actual match outcomes alongside the predictions the model made, so we can
measure performance over time and detect concept drift.

### Drift Detection & Retraining Trigger

Today retraining is manual (`python training/train.py`). The target is a scheduled or
drift-triggered job that reads point-in-time correct features via
`feast get_historical_features()`, retrains, and publishes a new model version to the
registry.

---

## Getting Started

### Prerequisites

- Docker + Docker Compose
- JDK 21+
- Gradle
- Python 3.12+ (serving layer only)

### Configure environment

```bash
cp .env.example .env
```

| Variable | Default | Description |
|----------|---------|-------------|
| `REDIS_HOST` | `localhost` | Redis host for the serving layer |
| `REDIS_PORT` | `6379` | Redis port |
| `REDIS_MAX_CONNECTIONS` | `20` | Redis connection pool size |
| `MODEL_PATH` | `models/model.ubj` | Path to the trained XGBoost model artifact |

### Start infrastructure

```bash
docker compose up -d
```

Starts: Kafka (`localhost:29092`), Flink (`localhost:8081`), Redis (`localhost:6379`),
RedisInsight (`localhost:5540`), Kafka UI (`localhost:8082`), Feast feature server
(`localhost:6566`), Feast UI (`localhost:8888`).

### Replicating the four flows

The four flows in the diagram are wired up but each requires a separate command,
and they must run in this order. The Kafka topic `bra-serie-a-matches` is
auto-created on first produce.

#### 1. Online pipeline — deploy the streaming job

```bash
cd pipeline
./gradlew deployFlink
```

Uploads the shadow JAR and runs `VelocityFeatureJob`. It consumes from Kafka and
pushes features to the Feast feature server (which writes to Redis).

#### 2. Run the producer

```bash
./gradlew runProducer
```

Reads `notebooks/bra_serie_a/BRA.csv` and publishes match events to Kafka. The
streaming job picks them up live.

#### 3. Offline pipeline — backfill the Parquet store

```bash
./gradlew deployBatchJob
```

Runs `TeamStatsBatchJob`, which reads Kafka from earliest to latest (bounded) and
writes `data/offline_store/team_stats/*.parquet`. **Must run after the producer
finishes** — the job stops at the latest offset at submission time.

#### 4. Training — produce the model artifact

```bash
cd ../training
pip install -r requirements.txt
python train.py
```

Reads the Parquet offline store and writes `serving/models/model.ubj`.

#### 5. Serving — load model + features and predict

```bash
cd ../serving
pip install -r requirements.txt
source ../.env  # or export variables manually
uvicorn main:app --reload
```

### Verify

```bash
# Check features were written to Redis via Feast
docker exec redis redis-cli hgetall team_stats:palmeiras

# Hit the prediction endpoint
curl http://localhost:8000/predict/palmeiras
```

---

## Utility UIs

Once `docker compose up -d` is running, the following web UIs are available for
inspecting and debugging the pipeline:

| UI | URL | Purpose |
|----|-----|---------|
| Kafka UI | <http://localhost:8082> | Browse topics, partitions, and messages |
| Flink UI | <http://localhost:8081> | Monitor jobs, task managers, and checkpoints |
| Feast UI | <http://localhost:8888> | Explore feature views, entities, and registry |
| RedisInsight | <http://localhost:5540> | Inspect keys and values in the online store |
