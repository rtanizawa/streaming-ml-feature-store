package com.rtagui.transforms

import com.rtagui.model.TeamMatchRecord
import com.rtagui.producer.SoccerMatchEvent
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.apache.flink.util.Collector

class MatchToTeamRecordFlatMapTest : FunSpec({

    val flatMap = MatchToTeamRecordFlatMap()

    fun collect(event: SoccerMatchEvent): List<TeamMatchRecord> {
        val output = mutableListOf<TeamMatchRecord>()
        flatMap.flatMap(event, object : Collector<TeamMatchRecord> {
            override fun collect(record: TeamMatchRecord) { output.add(record) }
            override fun close() {}
        })
        return output
    }

    fun makeEvent(homeTeam: String, awayTeam: String, result: String?,
                  homeGoals: Int?, awayGoals: Int?) = SoccerMatchEvent(
        eventId = "1", matchDate = "2023-05-14", matchTime = "16:00",
        season = 2023, country = "Brazil", league = "Serie A",
        homeTeam = homeTeam, awayTeam = awayTeam,
        homeGoals = homeGoals, awayGoals = awayGoals, result = result,
        oddsPinnacleHome = null, oddsPinnacleDraw = null, oddsPinnacleAway = null,
        oddsMaxHome = null, oddsMaxDraw = null, oddsMaxAway = null,
        oddsAvgHome = null, oddsAvgDraw = null, oddsAvgAway = null,
    )

    test("emits exactly two records per match") {
        val records = collect(makeEvent("Flamengo", "Santos", "H", 3, 1))
        records.size shouldBe 2
    }

    test("home win maps to W for home and L for away") {
        val records = collect(makeEvent("Flamengo", "Santos", "H", 3, 1))
        val home = records.first { it.teamName == "Flamengo" }
        val away = records.first { it.teamName == "Santos" }
        home.result shouldBe "W"
        away.result shouldBe "L"
    }

    test("away win maps to L for home and W for away") {
        val records = collect(makeEvent("Flamengo", "Santos", "A", 0, 2))
        records.first { it.teamName == "Flamengo" }.result shouldBe "L"
        records.first { it.teamName == "Santos" }.result shouldBe "W"
    }

    test("draw maps to D for both teams") {
        val records = collect(makeEvent("Flamengo", "Santos", "D", 1, 1))
        records.first { it.teamName == "Flamengo" }.result shouldBe "D"
        records.first { it.teamName == "Santos" }.result shouldBe "D"
    }

    test("null result propagates as null for both teams") {
        val records = collect(makeEvent("Flamengo", "Santos", null, null, null))
        records.first { it.teamName == "Flamengo" }.result.shouldBeNull()
        records.first { it.teamName == "Santos" }.result.shouldBeNull()
    }

    test("goals are correctly swapped between home and away perspective") {
        val records = collect(makeEvent("Flamengo", "Santos", "H", 3, 1))
        val home = records.first { it.teamName == "Flamengo" }
        val away = records.first { it.teamName == "Santos" }
        home.goalsFor shouldBe 3
        home.goalsAgainst shouldBe 1
        away.goalsFor shouldBe 1
        away.goalsAgainst shouldBe 3
    }
})
