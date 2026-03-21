# Plan: BRA Serie A CSV → Kafka Producer (Kotlin)

## Context
The project has a BRA.csv file (5386 rows, Brazilian Serie A 2012-2025) and an existing Kotlin pipeline module with
Kafka infrastructure via Docker Compose. The goal is to parse the CSV and produce each match as a structured JSON event
to a dedicated Kafka topic, simulating real-time delivery by throttling based on match date progression.

---

## Files to modify / create

| File | Action |
|---|---|
| `pipeline/build.gradle.kts` | Add Commons CSV + Jackson Kotlin dependencies + `runProducer` task |
| `pipeline/src/main/kotlin/com/rtagui/producer/SoccerMatchEvent.kt` | New data class |
| `pipeline/src/main/kotlin/com/rtagui/producer/BraSerieAProducer.kt` | New producer main |

---

## Event Schema (`SoccerMatchEvent.kt`)

```kotlin
data class SoccerMatchEvent(
    val eventId: String,           // UUID generated per row
    val matchDate: String,         // "2012-05-19"
    val matchTime: String,         // "22:30"
    val season: Int,
    val country: String,
    val league: String,
    val homeTeam: String,
    val awayTeam: String,
    val homeGoals: Int?,
    val awayGoals: Int?,
    val result: String?,           // "H" | "D" | "A" | null
    val oddsPinnacleHome: Double?,
    val oddsPinnacleDraw: Double?,
    val oddsPinnacleAway: Double?,
    val oddsMaxHome: Double?,
    val oddsMaxDraw: Double?,
    val oddsMaxAway: Double?,
    val oddsAvgHome: Double?,
    val oddsAvgDraw: Double?,
    val oddsAvgAway: Double?,
)
```

Annotated with `@JsonInclude(NON_NULL)` so absent odds do not produce `null` JSON fields.

---

## Producer Logic (`BraSerieAProducer.kt`)

**Entry point:** `fun main(args: Array<String>)`

### Arguments (positional)
| # | Name | Default |
|---|---|---|
| 0 | CSV path | `BRA.csv` |
| 1 | Kafka bootstrap servers | `localhost:29092` |
| 2 | ms per day of real time (scale factor) | `10` |

### Steps

1. **Parse CSV** with Apache Commons CSV (`withFirstRecordAsHeader()`).
2. **Sort rows by date** (`Date` column, `dd/MM/yyyy` format).
3. **Group rows by date** into a `Map<LocalDate, List<CSVRecord>>`.
4. **Produce per date group:**
   - For each record in the group: map to `SoccerMatchEvent`, serialize to JSON (Jackson), produce to `bra-serie-a-matches`
     with `homeTeam` as the Kafka message key.
   - After processing all records for a date, compute `daysDiff` to the next date and sleep
     `daysDiff * scaleMs` milliseconds.
5. **Flush and close** the KafkaProducer.

### Kafka producer config
```
bootstrap.servers = <arg>
key.serializer   = StringSerializer
value.serializer = StringSerializer
acks             = all
```

### Throttling example
Rows from 2012-05-19 → produce all → sleep (2012-05-20 − 2012-05-19) × 10ms = 10ms → next batch...

---

## Gradle changes (`build.gradle.kts`)

Add dependencies:
```kotlin
implementation("org.apache.commons:commons-csv:1.10.0")
implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.0")
```

Add custom task so existing `run`/`mainClass` is unchanged:
```kotlin
tasks.register<JavaExec>("runProducer") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.rtagui.producer.BraSerieAProducerKt")
    args = listOf("/Users/rafaeltanizawa/Downloads/BRA.csv")
}
```

---

## Verification

1. Start Docker services: `docker compose up -d kafka kafka-ui`
2. Run producer: `./gradlew :pipeline:runProducer`
3. Open Kafka UI at `http://localhost:8082` → topic `bra-serie-a-matches` → confirm messages arrive with correct
   JSON schema and home-team key.
