package com.rtagui.serialization

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.rtagui.producer.SoccerMatchEvent
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.connector.kafka.source.reader.deserializer.KafkaRecordDeserializationSchema
import org.apache.flink.util.Collector
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory

class SoccerMatchEventDeserializationSchema : KafkaRecordDeserializationSchema<SoccerMatchEvent> {

    companion object {
        private val log = LoggerFactory.getLogger(SoccerMatchEventDeserializationSchema::class.java)
    }

    @delegate:Transient
    private val objectMapper: ObjectMapper by lazy {
        ObjectMapper().registerModule(KotlinModule.Builder().build())
    }

    override fun deserialize(record: ConsumerRecord<ByteArray, ByteArray>, out: Collector<SoccerMatchEvent>) {
        try {
            val event = objectMapper.readValue(record.value(), SoccerMatchEvent::class.java)
            out.collect(event)
        } catch (e: Exception) {
            log.warn("Failed to deserialize message at offset ${record.offset()}: ${e.message}")
        }
    }

    override fun getProducedType(): TypeInformation<SoccerMatchEvent> =
        TypeInformation.of(SoccerMatchEvent::class.java)
}
