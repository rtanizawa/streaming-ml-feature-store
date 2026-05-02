# AI Agent Instructions

## Project Overview

Streaming ML Feature Store is a learning project for feature engineering and XGBoost-based prediction with a
Kafka/Flink streaming pipeline, Feast/Redis feature serving, model training, and FastAPI serving.

## Ground Rules

- Write tests for new use cases, including unit and integration coverage where appropriate.
- Keep `plans.md` files wrapped at 120 characters per line.
- Do not add unnecessary functionality beyond the current use case.
- Do not add comments for future features or unused fields.
- Do not commit code without explicit authorization.

## Useful Commands

### Infrastructure

```bash
docker compose up -d
```

### Pipeline

```bash
cd pipeline
./gradlew test
./gradlew deployFlink
./gradlew runProducer
./gradlew deployBatchJob
```

### Training

```bash
cd training
pip install -r requirements.txt
pytest
python train.py
```

### Serving

```bash
cd serving
pip install -r requirements.txt
pytest
uvicorn main:app --reload
```

## Verification

```bash
docker exec redis redis-cli hgetall team_stats:sao_paulo
```

```bash
curl -X POST http://localhost:8000/predict-match \
  -H "Content-Type: application/json" \
  -d '{
  "home_team": "sao_paulo",
  "away_team": "santos",
  "odds_pinnacle_home": 1.5,
  "odds_pinnacle_draw": 4.0,
  "odds_pinnacle_away": 6.0
}'
```

Expected response shape:

```json
{
  "home_team": "sao_paulo",
  "away_team": "santos",
  "probabilities": {
    "H": 0.42,
    "D": 0.31,
    "A": 0.27
  },
  "prediction": "H"
}
```
