package com.rtagui.serialization

import com.rtagui.producer.SoccerMatchEvent
import org.apache.flink.util.Collector
import org.apache.kafka.clients.consumer.ConsumerRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SoccerMatchEventDeserializationSchemaTest {

    private val schema = SoccerMatchEventDeserializationSchema()

    private fun deserialize(json: String): List<SoccerMatchEvent> {
        val output = mutableListOf<SoccerMatchEvent>()
        val record = ConsumerRecord<ByteArray, ByteArray>("bra-serie-a-matches", 0, 0L, null, json.toByteArray())
        schema.deserialize(record, object : Collector<SoccerMatchEvent> {
            override fun collect(event: SoccerMatchEvent) { output.add(event) }
            override fun close() {}
        })
        return output
    }

    @Test
    fun `valid JSON deserializes to correct SoccerMatchEvent`() {
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
        assertEquals(1, events.size)
        val event = events.first()
        assertEquals("abc123", event.eventId)
        assertEquals("Flamengo", event.homeTeam)
        assertEquals("Santos", event.awayTeam)
        assertEquals(3, event.homeGoals)
        assertEquals(1, event.awayGoals)
        assertEquals("H", event.result)
    }

    @Test
    fun `malformed JSON produces no output without throwing`() {
        val events = deserialize("{not valid json}")
        assertTrue(events.isEmpty())
    }

    @Test
    fun `empty bytes produce no output without throwing`() {
        val events = deserialize("")
        assertTrue(events.isEmpty())
    }
}
