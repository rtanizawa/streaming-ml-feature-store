package com.rtagui.transforms

import com.rtagui.model.TeamMatchRecord
import com.rtagui.model.TeamStats
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.apache.flink.api.common.typeinfo.Types
import org.apache.flink.api.java.functions.KeySelector
import org.apache.flink.streaming.api.operators.KeyedProcessOperator
import org.apache.flink.streaming.util.KeyedOneInputStreamOperatorTestHarness

class TeamStatsFunctionTest : FunSpec({

    lateinit var harness: KeyedOneInputStreamOperatorTestHarness<String, TeamMatchRecord, TeamStats>

    beforeTest {
        harness = KeyedOneInputStreamOperatorTestHarness(
            KeyedProcessOperator(TeamStatsFunction()),
            KeySelector<TeamMatchRecord, String> { it.teamName },
            Types.STRING,
        )
        harness.open()
    }

    afterTest {
        harness.close()
    }

    fun record(
        team: String, result: String?, goalsFor: Int?, goalsAgainst: Int?,
    ) = TeamMatchRecord(
        teamName = team, isHome = true,
        result = result, goalsFor = goalsFor, goalsAgainst = goalsAgainst,
        matchTimestamp = 0L,
    )

    test("accumulates matches played across multiple records") {
        harness.processElement(record("Flamengo", "W", 2, 0), 1L)
        harness.processElement(record("Flamengo", "L", 0, 1), 2L)
        harness.processElement(record("Flamengo", "D", 1, 1), 3L)

        val last = harness.extractOutputStreamRecords().last().value
        last.matchesPlayed shouldBe 3
        last.wins shouldBe 1
        last.draws shouldBe 1
        last.losses shouldBe 1
    }

    test("accumulates goals correctly") {
        harness.processElement(record("Santos", "W", 3, 0), 1L)
        harness.processElement(record("Santos", "D", 1, 1), 2L)

        val last = harness.extractOutputStreamRecords().last().value
        last.goalsFor shouldBe 4
        last.goalsAgainst shouldBe 1
    }

    test("handles null result without throwing") {
        harness.processElement(record("Gremio", null, null, null), 1L)

        val last = harness.extractOutputStreamRecords().last().value
        last.matchesPlayed shouldBe 1
        last.wins shouldBe 0
        last.draws shouldBe 0
        last.losses shouldBe 0
        last.goalsFor shouldBe 0
        last.goalsAgainst shouldBe 0
    }

    test("tracks separate state per team") {
        harness.processElement(record("Flamengo", "W", 2, 0), 1L)
        harness.processElement(record("Santos", "L", 0, 2), 2L)

        val outputs = harness.extractOutputStreamRecords().map { it.value }
        outputs.first { it.teamName == "Flamengo" }.wins shouldBe 1
        outputs.first { it.teamName == "Santos" }.losses shouldBe 1
    }
})
