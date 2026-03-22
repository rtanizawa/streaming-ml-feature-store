# Glossary

## Data & Pipelines

| Term | Definition |
|------|------------|
| **Source** | Where data comes from — Kafka topic, CSV, database |
| **Sink** | Where processed data is written to — Redis, S3, database |
| **Stream processing** | Handling data continuously as it arrives (what Flink does) |
| **Batch processing** | Handling a fixed dataset all at once, then stopping |
| **Backfill** | Reprocessing historical data to populate a new feature or fix a bug |
| **Offset** | A pointer to a specific position in a Kafka topic |
| **Checkpoint** | A snapshot of Flink's internal state, used to recover from failures |
| **State** | Data Flink keeps in memory between events (e.g. running win count per team) |

## ML Lifecycle

| Term | Definition |
|------|------------|
| **Training** | The process of fitting a model on historical data to learn patterns |
| **Inference / Serving** | Using a trained model to make predictions on new data |
| **Feature** | An input variable used by the model — e.g. `wins`, `goalsFor` |
| **Feature engineering** | Transforming raw data into meaningful features for the model |
| **Feature store** | A system that stores features consistently for both training and serving (Redis in our case) |
| **Training/serving skew** | When features are computed differently at training vs inference time — the #1 source of silent model bugs |
| **Model artifact** | The serialized trained model file (e.g. `model.ubj`) |
| **Model registry** | Versioned storage for model artifacts — like Git but for models |
| **Offline pipeline** | Batch processing of historical data for training |
| **Online pipeline** | Real-time processing of live data for serving |
| **Cold start** | When there's no historical data for a new entity (e.g. a newly promoted team) |

## Model Quality

| Term | Definition |
|------|------------|
| **Overfitting** | Model learns the training data too well and performs poorly on new data |
| **Underfitting** | Model is too simple to capture patterns in the data |
| **Data drift** | The statistical distribution of incoming data changes over time |
| **Concept drift** | The relationship between features and the target changes over time (e.g. football tactics evolve) |
| **Feedback loop** | Capturing real outcomes to evaluate and improve the model over time |
| **Retraining** | Re-running the training pipeline with newer data to refresh the model |

## Prediction

| Term | Definition |
|------|------------|
| **Label / Target** | What the model is trying to predict — e.g. match result (H/D/A) |
| **Score / Prediction** | The model's output — e.g. probability of home win |
| **Confidence** | How certain the model is about its prediction |
| **Baseline** | The simplest possible prediction to beat — e.g. "always predict home win" |
| **A/B testing** | Running two model versions simultaneously to compare performance in production |
