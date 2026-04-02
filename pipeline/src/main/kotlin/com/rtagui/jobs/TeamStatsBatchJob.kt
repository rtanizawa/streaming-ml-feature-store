package com.rtagui.jobs

import com.rtagui.config.AppConfigLoader
import com.rtagui.model.TeamMatchRecord
import com.rtagui.model.TeamStats
import com.rtagui.producer.SoccerMatchEvent
import com.rtagui.serialization.SoccerMatchEventDeserializationSchema
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

    val schema: Schema = SchemaBuilder.record("TeamStats").fields()
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

    val env = StreamExecutionEnvironment.getExecutionEnvironment()

    val source = KafkaSource.builder<SoccerMatchEvent>()
        .setBootstrapServers(config.kafka.bootstrapServers)
        .setTopics("bra-serie-a-matches")
        .setGroupId("flink-batch-job-consumer")
        .setStartingOffsets(OffsetsInitializer.earliest())
        .setBounded(OffsetsInitializer.latest())
        .setDeserializer(SoccerMatchEventDeserializationSchema())
        .build()

    env.fromSource(source, WatermarkStrategy.noWatermarks(), "KafkaSource")
        .flatMap(MatchToTeamRecordFlatMap())
        .keyBy(TeamMatchRecord::teamName)
        .process(TeamStatsFunction())
        .setParallelism(1)
        .sinkTo(ParquetSink(config.feast.offlineStorePath, schema))

    env.execute("TeamStatsBatchJob")
}

private class ParquetSink(
    private val outputDir: String,
    private val schema: Schema,
) : Sink<TeamStats> {
    override fun createWriter(context: WriterInitContext): SinkWriter<TeamStats> =
        ParquetWriter(outputDir, schema)
}

private class ParquetWriter(outputDir: String, private val schema: Schema) : SinkWriter<TeamStats> {

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
