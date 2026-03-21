package com.rtagui.transforms

import com.rtagui.model.TeamMatchRecord
import com.rtagui.producer.SoccerMatchEvent
import org.apache.flink.api.common.functions.FlatMapFunction
import org.apache.flink.util.Collector
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class MatchToTeamRecordFlatMap : FlatMapFunction<SoccerMatchEvent, TeamMatchRecord> {

    override fun flatMap(event: SoccerMatchEvent, out: Collector<TeamMatchRecord>) {
        val timestamp = LocalDate.parse(event.matchDate, DateTimeFormatter.ISO_LOCAL_DATE)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()

        val homeResult = when (event.result) {
            "H" -> "W"
            "D" -> "D"
            "A" -> "L"
            else -> null
        }
        val awayResult = when (event.result) {
            "H" -> "L"
            "D" -> "D"
            "A" -> "W"
            else -> null
        }

        out.collect(
            TeamMatchRecord(
                teamName = event.homeTeam,
                isHome = true,
                goalsFor = event.homeGoals,
                goalsAgainst = event.awayGoals,
                result = homeResult,
                matchTimestamp = timestamp,
            )
        )
        out.collect(
            TeamMatchRecord(
                teamName = event.awayTeam,
                isHome = false,
                goalsFor = event.awayGoals,
                goalsAgainst = event.homeGoals,
                result = awayResult,
                matchTimestamp = timestamp,
            )
        )
    }
}
