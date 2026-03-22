package com.rtagui.jobs

import com.rtagui.model.TeamMatchRecord
import com.rtagui.producer.SoccerMatchEvent
import com.rtagui.serialization.SoccerMatchEventDeserializationSchema
import com.rtagui.sink.RedisSink
import com.rtagui.transforms.MatchToTeamRecordFlatMap
import com.rtagui.transforms.TeamStatsFunction
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.connector.kafka.source.KafkaSource
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import java.time.Duration

class VelocityFeatureJob(
    private val bootstrapServers: String = "localhost:9092",
    private val redisUri: String = "redis://localhost:6379",
) {

    fun run() {
        val env = StreamExecutionEnvironment.getExecutionEnvironment()
        env.enableCheckpointing(Duration.ofSeconds(10).toMillis())

        val source = KafkaSource.builder<SoccerMatchEvent>()
            .setBootstrapServers(bootstrapServers)
            .setTopics("bra-serie-a-matches")
            .setGroupId("flink-team-stats-consumer")
            .setStartingOffsets(OffsetsInitializer.committedOffsets(OffsetResetStrategy.EARLIEST))
            .setDeserializer(SoccerMatchEventDeserializationSchema())
            .build()

        env.fromSource(source, WatermarkStrategy.noWatermarks(), "KafkaSource")
            .flatMap(MatchToTeamRecordFlatMap())
            .keyBy(TeamMatchRecord::teamName)
            .process(TeamStatsFunction())
            .addSink(RedisSink(redisUri))

        env.execute("TeamStatsConsumerJob")
    }
}
