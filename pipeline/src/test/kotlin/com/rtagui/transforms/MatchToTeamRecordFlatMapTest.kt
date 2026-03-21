package com.rtagui.transforms

import com.rtagui.model.TeamMatchRecord
import com.rtagui.producer.SoccerMatchEvent
import org.apache.flink.util.Collector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MatchToTeamRecordFlatMapTest {

    private val flatMap = MatchToTeamRecordFlatMap()

    private fun collect(event: SoccerMatchEvent): List<TeamMatchRecord> {
        val output = mutableListOf<TeamMatchRecord>()
        flatMap.flatMap(event, object : Collector<TeamMatchRecord> {
            override fun collect(record: TeamMatchRecord) { output.add(record) }
            override fun close() {}
        })
        return output
    }

    private fun makeEvent(homeTeam: String, awayTeam: String, result: String?,
                          homeGoals: Int?, awayGoals: Int?) = SoccerMatchEvent(
        eventId = "1", matchDate = "2023-05-14", matchTime = "16:00",
        season = 2023, country = "Brazil", league = "Serie A",
        homeTeam = homeTeam, awayTeam = awayTeam,
        homeGoals = homeGoals, awayGoals = awayGoals, result = result,
        oddsPinnacleHome = null, oddsPinnacleDraw = null, oddsPinnacleAway = null,
        oddsMaxHome = null, oddsMaxDraw = null, oddsMaxAway = null,
        oddsAvgHome = null, oddsAvgDraw = null, oddsAvgAway = null,
    )

    @Test
    fun `emits exactly two records per match`() {
        val records = collect(makeEvent("Flamengo", "Santos", "H", 3, 1))
        assertEquals(2, records.size)
    }

    @Test
    fun `home win maps to W for home and L for away`() {
        val records = collect(makeEvent("Flamengo", "Santos", "H", 3, 1))
        val home = records.first { it.teamName == "Flamengo" }
        val away = records.first { it.teamName == "Santos" }
        assertEquals("W", home.result)
        assertEquals("L", away.result)
    }

    @Test
    fun `away win maps to L for home and W for away`() {
        val records = collect(makeEvent("Flamengo", "Santos", "A", 0, 2))
        assertEquals("L", records.first { it.teamName == "Flamengo" }.result)
        assertEquals("W", records.first { it.teamName == "Santos" }.result)
    }

    @Test
    fun `draw maps to D for both teams`() {
        val records = collect(makeEvent("Flamengo", "Santos", "D", 1, 1))
        assertEquals("D", records.first { it.teamName == "Flamengo" }.result)
        assertEquals("D", records.first { it.teamName == "Santos" }.result)
    }

    @Test
    fun `null result propagates as null for both teams`() {
        val records = collect(makeEvent("Flamengo", "Santos", null, null, null))
        assertNull(records.first { it.teamName == "Flamengo" }.result)
        assertNull(records.first { it.teamName == "Santos" }.result)
    }

    @Test
    fun `goals are correctly swapped between home and away perspective`() {
        val records = collect(makeEvent("Flamengo", "Santos", "H", 3, 1))
        val home = records.first { it.teamName == "Flamengo" }
        val away = records.first { it.teamName == "Santos" }
        assertEquals(3, home.goalsFor)
        assertEquals(1, home.goalsAgainst)
        assertEquals(1, away.goalsFor)
        assertEquals(3, away.goalsAgainst)
    }
}
