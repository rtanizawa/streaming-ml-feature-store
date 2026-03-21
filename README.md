# Decision Tree PoC

Real-time ML feature store and scoring pipeline using Kafka, Flink, Redis, and XGBoost.

## Architecture

```
Kafka → Flink (Kotlin) → Redis
                              ↘
              User Request → FastAPI → XGBoost → Score
```

- **Kafka + Flink (Kotlin)** — ingest events, compute velocity features, write to Redis
- **Redis** — feature store, serves pre-computed features at inference time
- **FastAPI (Python)** — model serving layer, reads features from Redis
- **XGBoost** — real-time scoring

## Stack

| Layer | Technology | Language |
|-------|-----------|----------|
| Streaming pipeline | Kafka + Flink | Kotlin |
| Feature store | Redis | - |
| Model serving | FastAPI | Python |
| ML model | XGBoost | Python |

## Getting Started

### Prerequisites

- Docker + Docker Compose
- Kotlin / JVM (for Flink pipeline)
- Python 3.9+ (for model serving)

### Start Redis

```bash
docker compose up -d
```

This starts:
- **Redis** on `localhost:6379`
- **RedisInsight** (GUI) on `http://localhost:5540`

#### RedisInsight connection settings
- Host: `redis`
- Port: `6379`

### Redis Feature Schema

Features are stored as Hashes with TTL for freshness control.

**Key convention:**
```
{namespace}:{entity_type}:{id}

features:user:123
velocity:user:123
```

**Velocity features** (computed by Flink, written to Redis):
```bash
HSET velocity:user:123 \
  txn_count_1h 12 \
  txn_sum_1h 340.50 \
  txn_count_24h 45 \
  txn_sum_24h 1200.00

EXPIRE velocity:user:123 86400
```

### Flink Pipeline (Kotlin)

Uses [Lettuce](https://lettuce.io/) as the async Redis client:

```kotlin
val client = RedisClient.create("redis://localhost:6379")
val connection = client.connect()
val commands = connection.async()

commands.hset("velocity:user:123", mapOf(
    "txn_count_1h" to "12",
    "txn_sum_1h" to "340.50"
))
```

### Model Serving (Python)

Install dependencies:

```bash
pip install fastapi uvicorn redis xgboost numpy
```

Run the API:

```bash
uvicorn main:app --reload
```

#### Prediction endpoint

```
GET /predict/{user_id}
```

Response:
```json
{
  "user_id": "123",
  "score": 0.87
}
```

Features are read from Redis and passed directly to the XGBoost model. If no features are found for a user, the API returns a cold-start response:

```json
{
  "score": 0.0,
  "cold_start": true
}
```

#### Redis connection pooling

```python
r = redis.Redis(
    host="localhost",
    port=6379,
    decode_responses=True,
    max_connections=20
)
```

### Model Artifact

For this PoC, the model file lives in the repo:

```
models/model.ubj
```

Loaded once at startup:

```python
@asynccontextmanager
async def lifespan(app: FastAPI):
    app.state.model = xgb.Booster()
    app.state.model.load_model("models/model.ubj")
    yield
```

When moving to production, replace with S3:

```python
import boto3, tempfile

s3 = boto3.client("s3")
with tempfile.NamedTemporaryFile() as f:
    s3.download_fileobj("your-bucket", "models/model.ubj", f)
    model.load_model(f.name)
```
