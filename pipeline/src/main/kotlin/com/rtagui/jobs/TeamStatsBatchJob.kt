package com.rtagui.jobs

import com.rtagui.config.AppConfigLoader
import com.rtagui.model.MatchRow
import com.rtagui.model.TeamMatchRecord
import com.rtagui.model.TeamStats
import com.rtagui.producer.SoccerMatchEvent
import com.rtagui.serialization.SoccerMatchEventDeserializationSchema
import com.rtagui.transforms.MatchToMatchRowMap
import com.rtagui.transforms.MatchToTeamRecordFlatMap
import com.rtagui.transforms.TeamStatsFunction
import org.apache.avro.LogicalTypes
import org.apache.avro.Schema
import org.apache.avro.SchemaBuilder
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericRecord
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.connector.sink2.Sink
import org.apache.flink.api.connector.sink2.SinkWriter
import org.apache.flink.api.connector.sink2.WriterInitContext
import org.apache.flink.connector.kafka.source.KafkaSource
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.parquet.avro.AvroParquetWriter
import org.apache.parquet.conf.PlainParquetConfiguration
import org.apache.parquet.hadoop.metadata.CompressionCodecName
import org.apache.parquet.io.LocalOutputFile
import java.io.File
import java.nio.file.Paths

fun main() {
    val config = AppConfigLoader.load()

    val teamStatsSchema: Schema = SchemaBuilder.record("TeamStats").fields()
        .name("team_name").type().stringType().noDefault()
        .name("matches_played").type().intType().noDefault()
        .name("wins").type().intType().noDefault()
        .name("draws").type().intType().noDefault()
        .name("losses").type().intType().noDefault()
        .name("goals_for").type().intType().noDefault()
        .name("goals_against").type().intType().noDefault()
        .name("event_timestamp")
            .type(LogicalTypes.timestampMillis().addToSchema(Schema.create(Schema.Type.LONG)))
            .noDefault()
        .endRecord()

    val matchSchema: Schema = SchemaBuilder.record("MatchRow").fields()
        .requiredString("event_id")
        .name("event_timestamp")
            .type(LogicalTypes.timestampMillis().addToSchema(Schema.create(Schema.Type.LONG)))
            .noDefault()
        .requiredString("home_team")
        .requiredString("away_team")
        .optionalInt("home_goals")
        .optionalInt("away_goals")
        .optionalString("result")
        .optionalDouble("odds_pinnacle_home")
        .optionalDouble("odds_pinnacle_draw")
        .optionalDouble("odds_pinnacle_away")
        .optionalDouble("odds_avg_home")
        .optionalDouble("odds_avg_draw")
        .optionalDouble("odds_avg_away")
        .endRecord()

    val env = StreamExecutionEnvironment.getExecutionEnvironment()

    val source = KafkaSource.builder<SoccerMatchEvent>()
        .setBootstrapServers(config.kafka.bootstrapServers)
        .setTopics("bra-serie-a-matches")
        .setGroupId("flink-batch-job-consumer")
        .setStartingOffsets(OffsetsInitializer.earliest())
        .setBounded(OffsetsInitializer.latest())
        .setDeserializer(SoccerMatchEventDeserializationSchema())
        .build()

    val events = env.fromSource(source, WatermarkStrategy.noWatermarks(), "KafkaSource")

    events.flatMap(MatchToTeamRecordFlatMap())
        .keyBy(TeamMatchRecord::teamName)
        .process(TeamStatsFunction())
        .setParallelism(1)
        .sinkTo(TeamStatsParquetSink(config.feast.offlineStorePath, teamStatsSchema))

    events.map(MatchToMatchRowMap())
        .setParallelism(1)
        .sinkTo(MatchParquetSink(config.feast.matchesOfflinePath, matchSchema))

    env.execute("TeamStatsBatchJob")
}

private class TeamStatsParquetSink(
    private val outputDir: String,
    private val schema: Schema,
) : Sink<TeamStats> {
    override fun createWriter(context: WriterInitContext): SinkWriter<TeamStats> =
        TeamStatsParquetWriter(outputDir, schema)
}

private class TeamStatsParquetWriter(outputDir: String, private val schema: Schema) : SinkWriter<TeamStats> {

    private val writer: org.apache.parquet.hadoop.ParquetWriter<GenericRecord>

    init {
        File(outputDir).mkdirs()
        val outputFile = LocalOutputFile(Paths.get(outputDir, "team_stats.parquet"))
        writer = AvroParquetWriter.builder<GenericRecord>(outputFile)
            .withSchema(schema)
            .withConf(PlainParquetConfiguration())
            .withCompressionCodec(CompressionCodecName.SNAPPY)
            .build()
    }

    override fun write(stats: TeamStats, context: SinkWriter.Context) {
        val record = GenericData.Record(schema)
        record.put("team_name", stats.teamName.lowercase().replace(" ", "_"))
        record.put("matches_played", stats.matchesPlayed)
        record.put("wins", stats.wins)
        record.put("draws", stats.draws)
        record.put("losses", stats.losses)
        record.put("goals_for", stats.goalsFor)
        record.put("goals_against", stats.goalsAgainst)
        record.put("event_timestamp", stats.eventTimestamp)
        writer.write(record)
    }

    override fun flush(endOfInput: Boolean) {}

    override fun close() {
        writer.close()
    }
}

private class MatchParquetSink(
    private val outputDir: String,
    private val schema: Schema,
) : Sink<MatchRow> {
    override fun createWriter(context: WriterInitContext): SinkWriter<MatchRow> =
        MatchParquetWriter(outputDir, schema)
}

private class MatchParquetWriter(outputDir: String, private val schema: Schema) : SinkWriter<MatchRow> {

    private val writer: org.apache.parquet.hadoop.ParquetWriter<GenericRecord>

    init {
        File(outputDir).mkdirs()
        val outputFile = LocalOutputFile(Paths.get(outputDir, "matches.parquet"))
        writer = AvroParquetWriter.builder<GenericRecord>(outputFile)
            .withSchema(schema)
            .withConf(PlainParquetConfiguration())
            .withCompressionCodec(CompressionCodecName.SNAPPY)
            .build()
    }

    override fun write(row: MatchRow, context: SinkWriter.Context) {
        val record = GenericData.Record(schema)
        record.put("event_id", row.eventId)
        record.put("event_timestamp", row.matchTimestamp)
        record.put("home_team", row.homeTeam)
        record.put("away_team", row.awayTeam)
        record.put("home_goals", row.homeGoals)
        record.put("away_goals", row.awayGoals)
        record.put("result", row.result)
        record.put("odds_pinnacle_home", row.oddsPinnacleHome)
        record.put("odds_pinnacle_draw", row.oddsPinnacleDraw)
        record.put("odds_pinnacle_away", row.oddsPinnacleAway)
        record.put("odds_avg_home", row.oddsAvgHome)
        record.put("odds_avg_draw", row.oddsAvgDraw)
        record.put("odds_avg_away", row.oddsAvgAway)
        writer.write(record)
    }

    override fun flush(endOfInput: Boolean) {}

    override fun close() {
        writer.close()
    }
}
