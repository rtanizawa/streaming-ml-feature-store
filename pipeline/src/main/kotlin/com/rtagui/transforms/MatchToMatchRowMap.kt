package com.rtagui.transforms

import com.rtagui.model.MatchRow
import com.rtagui.producer.SoccerMatchEvent
import org.apache.flink.api.common.functions.MapFunction
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class MatchToMatchRowMap : MapFunction<SoccerMatchEvent, MatchRow> {

    override fun map(event: SoccerMatchEvent): MatchRow {
        val timestamp = LocalDate.parse(event.matchDate, DateTimeFormatter.ISO_LOCAL_DATE)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()

        return MatchRow(
            eventId = event.eventId,
            matchTimestamp = timestamp,
            homeTeam = TeamNames.normalize(event.homeTeam),
            awayTeam = TeamNames.normalize(event.awayTeam),
            homeGoals = event.homeGoals,
            awayGoals = event.awayGoals,
            result = event.result,
            oddsPinnacleHome = event.oddsPinnacleHome,
            oddsPinnacleDraw = event.oddsPinnacleDraw,
            oddsPinnacleAway = event.oddsPinnacleAway,
            oddsAvgHome = event.oddsAvgHome,
            oddsAvgDraw = event.oddsAvgDraw,
            oddsAvgAway = event.oddsAvgAway,
        )
    }
}
