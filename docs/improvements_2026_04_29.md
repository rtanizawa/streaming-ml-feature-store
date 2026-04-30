What To Improve

  1. Create one feature/schema contract

     team_stats is duplicated in Feast, Kotlin, training, and serving:
     feast/features.py:27, pipeline/src/main/kotlin/com/rtagui/jobs/TeamStatsBatchJob.kt:32, training/train.py:15,
     serving/app/services/scorer.py:4.

     Add a shared contracts/team_stats.yaml or Avro/Protobuf schema and generate/validate the Python and Kotlin field
     lists from it. At minimum, add contract tests that compare Feast schema, Parquet schema, training features, and
     serving features.
  2. Pick a single offline-store writer

     The README says the stream push can write online and offline stores README.md:62, and the sink does "to":
     "online_and_offline" pipeline/src/main/kotlin/com/rtagui/sink/FeastHttpPushSink.kt:39. But there is also a separate
     batch job writing Parquet directly pipeline/src/main/kotlin/com/rtagui/jobs/TeamStatsBatchJob.kt:78.

     That creates two ownership paths for the same store. I’d either:
      - make Feast push the only online/offline writer for this learning project, or
      - make Parquet/table storage the source of truth and materialize Redis from Feast.
  3. Make streaming semantics explicit

     Both Flink jobs use WatermarkStrategy.noWatermarks() pipeline/src/main/kotlin/com/rtagui/jobs/
     VelocityFeatureJob.kt:33, and the state function increments counters in arrival order pipeline/src/main/kotlin/com/
     rtagui/transforms/TeamStatsFunction.kt:32. That is fine for a sorted CSV demo, but fragile for real streams.

     Add stable event IDs, deduplication, event-time watermarks, and a policy for late/out-of-order matches.
  4. Harden the Flink sink

     FeastHttpPushSink sends one synchronous HTTP request per update and ignores the response body/status beyond
     transport exceptions pipeline/src/main/kotlin/com/rtagui/sink/FeastHttpPushSink.kt:49. This is at-least-once and not
     idempotent.

     Add status validation, retries with backoff, metrics, structured JSON serialization, and either idempotent upserts
     or a more durable intermediate sink.
  5. Normalize entity IDs consistently

     The pipeline lowercases and underscore-normalizes team names before pushing pipeline/src/main/kotlin/com/rtagui/
     sink/FeastHttpPushSink.kt:23, but serving sends the raw path parameter to Feast serving/app/services/
     feature_store.py:17. /predict/Sao Paulo and /predict/sao_paulo will behave differently.

     Add one canonical TeamId normalization function and use it in producer, pipeline, training, and serving.
  6. Make training architecture more realistic

     Current training uses latest team stats and labels high performers with wins / matches_played >= 0.4 training/
     train.py:22. That means the model mostly learns a formula from the same fields used to define the label, over a tiny
     team-level dataset.

     Better architecture: build point-in-time training rows per team/match, label future outcome or future performance,
     retrieve historical features through Feast, and store model metadata with feature schema hash.
  7. Improve serving failure behavior

     Feature-store HTTP errors currently bubble up from raise_for_status() serving/app/services/feature_store.py:24. Cold
     starts return score 0.0 serving/app/routers/predict.py:9, which can be confused with a real prediction.

     Add typed request/response models, health checks, explicit 503 on feature-store failure, and return score: null for
     cold starts.

  Priority Order

  1. Shared feature contract.
  2. Single offline-store ownership path.
  3. Entity normalization.
  4. Sink reliability/idempotency.
  5. Point-in-time training dataset.
  6. Serving error handling and health checks.
  7. Observability: Flink metrics, bad-message DLQ, feature freshness, prediction logs.