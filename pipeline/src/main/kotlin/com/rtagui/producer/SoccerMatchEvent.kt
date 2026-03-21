package com.rtagui.producer

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class SoccerMatchEvent(
    val eventId: String,
    val matchDate: String,
    val matchTime: String,
    val season: Int,
    val country: String,
    val league: String,
    val homeTeam: String,
    val awayTeam: String,
    val homeGoals: Int?,
    val awayGoals: Int?,
    val result: String?,
    val oddsPinnacleHome: Double?,
    val oddsPinnacleDraw: Double?,
    val oddsPinnacleAway: Double?,
    val oddsMaxHome: Double?,
    val oddsMaxDraw: Double?,
    val oddsMaxAway: Double?,
    val oddsAvgHome: Double?,
    val oddsAvgDraw: Double?,
    val oddsAvgAway: Double?,
)
