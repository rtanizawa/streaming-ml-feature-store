package com.rtagui.serialization

import com.rtagui.producer.SoccerMatchEvent
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.apache.flink.util.Collector
import org.apache.kafka.clients.consumer.ConsumerRecord

class SoccerMatchEventDeserializationSchemaTest : FunSpec({

    val schema = SoccerMatchEventDeserializationSchema()

    fun deserialize(json: String): List<SoccerMatchEvent> {
        val output = mutableListOf<SoccerMatchEvent>()
        val record = ConsumerRecord<ByteArray, ByteArray>("bra-serie-a-matches", 0, 0L, null, json.toByteArray())
        schema.deserialize(record, object : Collector<SoccerMatchEvent> {
            override fun collect(event: SoccerMatchEvent) { output.add(event) }
            override fun close() {}
        })
        return output
    }

    test("valid JSON deserializes to correct SoccerMatchEvent") {
        val json = """
            {
              "eventId": "abc123",
              "matchDate": "2023-05-14",
              "matchTime": "16:00",
              "season": 2023,
              "country": "Brazil",
              "league": "Serie A",
              "homeTeam": "Flamengo",
              "awayTeam": "Santos",
              "homeGoals": 3,
              "awayGoals": 1,
              "result": "H"
            }
        """.trimIndent()

        val events = deserialize(json)
        events.size shouldBe 1
        val event = events.first()
        event.eventId shouldBe "abc123"
        event.homeTeam shouldBe "Flamengo"
        event.awayTeam shouldBe "Santos"
        event.homeGoals shouldBe 3
        event.awayGoals shouldBe 1
        event.result shouldBe "H"
    }

    test("malformed JSON produces no output without throwing") {
        deserialize("{not valid json}").shouldBeEmpty()
    }

    test("empty bytes produce no output without throwing") {
        deserialize("").shouldBeEmpty()
    }
})
