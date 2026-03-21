package com.rtagui.transforms

import com.rtagui.model.TeamMatchRecord
import com.rtagui.model.TeamStats
import org.apache.flink.api.common.typeinfo.Types
import org.apache.flink.api.java.functions.KeySelector
import org.apache.flink.streaming.api.operators.KeyedProcessOperator
import org.apache.flink.streaming.util.KeyedOneInputStreamOperatorTestHarness
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TeamStatsFunctionTest {

    private lateinit var harness: KeyedOneInputStreamOperatorTestHarness<String, TeamMatchRecord, TeamStats>

    @BeforeTest
    fun setUp() {
        harness = KeyedOneInputStreamOperatorTestHarness(
            KeyedProcessOperator(TeamStatsFunction()),
            KeySelector<TeamMatchRecord, String> { it.teamName },
            Types.STRING,
        )
        harness.open()
    }

    @AfterTest
    fun tearDown() {
        harness.close()
    }

    private fun record(
        team: String, result: String?, goalsFor: Int?, goalsAgainst: Int?,
    ) = TeamMatchRecord(
        teamName = team, isHome = true,
        result = result, goalsFor = goalsFor, goalsAgainst = goalsAgainst,
        matchTimestamp = 0L,
    )

    @Test
    fun `accumulates matches played across multiple records`() {
        harness.processElement(record("Flamengo", "W", 2, 0), 1L)
        harness.processElement(record("Flamengo", "L", 0, 1), 2L)
        harness.processElement(record("Flamengo", "D", 1, 1), 3L)

        val outputs = harness.extractOutputStreamRecords()
        val last = outputs.last().value
        assertEquals(3, last.matchesPlayed)
        assertEquals(1, last.wins)
        assertEquals(1, last.draws)
        assertEquals(1, last.losses)
    }

    @Test
    fun `accumulates goals correctly`() {
        harness.processElement(record("Santos", "W", 3, 0), 1L)
        harness.processElement(record("Santos", "D", 1, 1), 2L)

        val last = harness.extractOutputStreamRecords().last().value
        assertEquals(4, last.goalsFor)
        assertEquals(1, last.goalsAgainst)
    }

    @Test
    fun `handles null result without throwing`() {
        harness.processElement(record("Gremio", null, null, null), 1L)

        val last = harness.extractOutputStreamRecords().last().value
        assertEquals(1, last.matchesPlayed)
        assertEquals(0, last.wins)
        assertEquals(0, last.draws)
        assertEquals(0, last.losses)
        assertEquals(0, last.goalsFor)
        assertEquals(0, last.goalsAgainst)
    }

    @Test
    fun `tracks separate state per team`() {
        harness.processElement(record("Flamengo", "W", 2, 0), 1L)
        harness.processElement(record("Santos", "L", 0, 2), 2L)

        val outputs = harness.extractOutputStreamRecords().map { it.value }
        val flamengo = outputs.first { it.teamName == "Flamengo" }
        val santos = outputs.first { it.teamName == "Santos" }
        assertEquals(1, flamengo.wins)
        assertEquals(1, santos.losses)
    }
}
