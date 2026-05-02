package com.rtagui.transforms

import com.rtagui.producer.SoccerMatchEvent
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.ZoneOffset

class MatchToMatchRowMapTest : FunSpec({

    val map = MatchToMatchRowMap()

    fun makeEvent(
        homeTeam: String = "Flamengo",
        awayTeam: String = "Santos",
        result: String? = "H",
        homeGoals: Int? = 3,
        awayGoals: Int? = 1,
        matchDate: String = "2023-05-14",
    ) = SoccerMatchEvent(
        eventId = "evt-1", matchDate = matchDate, matchTime = "16:00",
        season = 2023, country = "Brazil", league = "Serie A",
        homeTeam = homeTeam, awayTeam = awayTeam,
        homeGoals = homeGoals, awayGoals = awayGoals, result = result,
        oddsPinnacleHome = 1.5, oddsPinnacleDraw = 4.0, oddsPinnacleAway = 6.0,
        oddsMaxHome = 1.6, oddsMaxDraw = 4.2, oddsMaxAway = 6.5,
        oddsAvgHome = 1.55, oddsAvgDraw = 4.1, oddsAvgAway = 6.2,
    )

    test("normalises team names to lowercase with underscores") {
        val row = map.map(makeEvent(homeTeam = "Atletico MG", awayTeam = "Sao Paulo"))
        row.homeTeam shouldBe "atletico_mg"
        row.awayTeam shouldBe "sao_paulo"
    }

    test("matchTimestamp is UTC midnight epoch millis of matchDate") {
        val row = map.map(makeEvent(matchDate = "2023-05-14"))
        val expected = LocalDate.of(2023, 5, 14).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        row.matchTimestamp shouldBe expected
    }

    test("copies result and goals through untouched") {
        val row = map.map(makeEvent(result = "A", homeGoals = 0, awayGoals = 2))
        row.result shouldBe "A"
        row.homeGoals shouldBe 0
        row.awayGoals shouldBe 2
    }

    test("null result and goals propagate") {
        val row = map.map(makeEvent(result = null, homeGoals = null, awayGoals = null))
        row.result.shouldBeNull()
        row.homeGoals.shouldBeNull()
        row.awayGoals.shouldBeNull()
    }

    test("copies pinnacle and average odds through, drops max odds") {
        val row = map.map(makeEvent())
        row.oddsPinnacleHome shouldBe 1.5
        row.oddsPinnacleDraw shouldBe 4.0
        row.oddsPinnacleAway shouldBe 6.0
        row.oddsAvgHome shouldBe 1.55
        row.oddsAvgDraw shouldBe 4.1
        row.oddsAvgAway shouldBe 6.2
    }

    test("preserves eventId") {
        val row = map.map(makeEvent())
        row.eventId shouldBe "evt-1"
    }
})
