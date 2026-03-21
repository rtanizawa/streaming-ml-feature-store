# Plan: Flink Consumer for `bra-serie-a-matches`

## Context

The project has a Kafka topic `bra-serie-a-matches` fed by `BraSerieAProducer`, which replays Brazilian Serie A
historical CSV data as `SoccerMatchEvent` JSON messages. The `VelocityFeatureJob` Flink job exists as a skeleton with
TODOs. The goal is to implement the full consumer pipeline: read from Kafka, compute per-team statistics, and write
feature vectors to Redis for downstream model serving.

---

## Data Flow

```
Kafka (bra-serie-a-matches)
  └─ KafkaSource<SoccerMatchEvent>
       └─ FlatMap → TeamMatchRecord  (1 match → 2 records: home team + away team)
            └─ KeyBy(teamName)
                 └─ TeamStatsFunction  (KeyedProcessFunction + ValueState<TeamStats>)
                      └─ RedisSink → Redis hash  team_stats:{teamName}
```

---

## New Types

### `model/TeamMatchRecord.kt`

Intermediate event keyed by team. Emitted twice per match (once for home, once for away).

```kotlin
data class TeamMatchRecord(
    val teamName: String,
    val isHome: Boolean,
    val goalsFor: Int?,
    val goalsAgainst: Int?,
    val result: String?,        // "W" | "D" | "L" (from team perspective) | null if unknown
    val matchTimestamp: Long,   // epoch millis derived from matchDate "YYYY-MM-DD"
)
```

### `model/TeamStats.kt`

Accumulated output state stored in Redis.

```kotlin
data class TeamStats(
    val teamName: String,
    val matchesPlayed: Int,
    val wins: Int,
    val draws: Int,
    val losses: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
)
```

---

## Processing Logic

### `transforms/TeamStatsFunction.kt` — `KeyedProcessFunction<String, TeamMatchRecord, TeamStats>`

- Maintains `ValueState<TeamStats>` keyed by `teamName`.
- On each `TeamMatchRecord`:
  1. Load state or initialize to zeroed `TeamStats`.
  2. Increment `matchesPlayed` always.
  3. If `result != null`: increment `wins`, `draws`, or `losses`.
  4. If `goalsFor != null`: add to `goalsFor` accumulator.
  5. If `goalsAgainst != null`: add to `goalsAgainst` accumulator.
  6. Store updated state and `collect` it downstream.

### `sink/RedisSink.kt` — `SinkFunction<TeamStats>`

- Opens a Lettuce `RedisClient` on first call (`@Transient lateinit var`).
- Key: `team_stats:{teamName}` (lowercase, spaces replaced with `_`).
- Stores all fields as a Redis hash via `hset`.
- No TTL (persistent historical stats).

### `jobs/VelocityFeatureJob.kt` — updated pipeline

```kotlin
val source = KafkaSource.builder<SoccerMatchEvent>()
    .setBootstrapServers(bootstrapServers)
    .setTopics("bra-serie-a-matches")
    .setGroupId("flink-team-stats-consumer")
    .setStartingOffsets(OffsetsInitializer.earliest())
    .setDeserializer(SoccerMatchEventDeserializationSchema())
    .build()

env.fromSource(source, WatermarkStrategy.forBoundedOutOfOrderness(Duration.ZERO), "KafkaSource")
    .flatMap(MatchToTeamRecordFlatMap())
    .keyBy { it.teamName }
    .process(TeamStatsFunction())
    .addSink(RedisSink())

env.execute("TeamStatsConsumerJob")
```

### `transforms/MatchToTeamRecordFlatMap.kt` — `FlatMapFunction<SoccerMatchEvent, TeamMatchRecord>`

Emits two `TeamMatchRecord`s per match:
- Home team: `result = if (event.result == "H") "W" else if (event.result == "D") "D" else if (event.result == "A") "L" else null`
- Away team: inverse mapping.

### `serialization/SoccerMatchEventDeserializationSchema.kt`

Implements `KafkaRecordDeserializationSchema<SoccerMatchEvent>`.
Uses Jackson `ObjectMapper` with `KotlinModule` to deserialize the JSON value. Returns `null` on parse errors
(logged via SLF4J) so the job does not fail on malformed messages.

---

## Files to Create / Modify

| File | Action |
|---|---|
| `jobs/VelocityFeatureJob.kt` | Modify: implement full Kafka → Redis pipeline |
| `model/TeamMatchRecord.kt` | Create |
| `model/TeamStats.kt` | Create |
| `transforms/MatchToTeamRecordFlatMap.kt` | Create |
| `transforms/TeamStatsFunction.kt` | Create |
| `serialization/SoccerMatchEventDeserializationSchema.kt` | Create |
| `sink/RedisSink.kt` | Modify: implement concrete `SinkFunction<TeamStats>` |
| `transforms/VelocityAggregator.kt` | Delete (replaced by `TeamStatsFunction`) |
| `build.gradle.kts` | Add `flink-test-utils` test dependency |

---

## Tests

### Unit Tests

| Test class | What it covers |
|---|---|
| `TeamStatsFunctionTest` | Uses Flink `KeyedOneInputStreamOperatorTestHarness` to assert state accumulation across multiple records, null result handling, win/draw/loss counting |
| `MatchToTeamRecordFlatMapTest` | Asserts 2 records emitted per match, correct result inversion (H→W for home / H→L for away), null result propagation |
| `SoccerMatchEventDeserializationSchemaTest` | Valid JSON → correct object; malformed JSON → null without exception |

### Integration Test

| Test class | What it covers |
|---|---|
| `RedisSinkIT` | Starts an embedded Redis (via Testcontainers `redis:7-alpine`) and asserts that after `invoke()`, the correct hash keys and values are present |

### New Gradle Dependency

```kotlin
testImplementation("org.apache.flink:flink-test-utils:$flinkVersion")
testImplementation("org.testcontainers:testcontainers:1.19.8")
testImplementation("org.testcontainers:kafka:1.19.8")
```

---

## Verification Steps

1. Start infrastructure: `docker compose up -d kafka redis flink-jobmanager flink-taskmanager`
2. Run producer: `./gradlew :pipeline:runProducer`
3. Build fat JAR: `./gradlew :pipeline:shadowJar` (need `shadow` plugin — add to `build.gradle.kts`)
4. Submit job via Flink UI at `http://localhost:8081` or `flink run` CLI.
5. Inspect Redis: `docker exec redis redis-cli hgetall "team_stats:flamengo"`
6. Expected output example:
   ```
   matchesPlayed → "38"
   wins          → "20"
   draws         → "10"
   losses        → "8"
   goalsFor      → "65"
   goalsAgainst  → "40"
   ```

---

## Open Questions / Decisions

| # | Question | Default |
|---|---|---|
| 1 | Redis key casing: lowercase + underscore vs original team name? | lowercase + `_` |
| 2 | Should `RedisSink` open/close connection per record or hold a persistent connection? | Persistent (opened in `open()`, closed in `close()`) |
| 3 | Should the `ShadowJar` (fat JAR) task be added to `build.gradle.kts` now? | Yes, needed for Flink cluster submission |
| 4 | Should the `flink-connector-base` dependency be added explicitly? | Only if build fails without it |
