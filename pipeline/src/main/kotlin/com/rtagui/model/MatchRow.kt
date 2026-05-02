package com.rtagui.model

data class MatchRow(
    val eventId: String,
    val matchTimestamp: Long,
    val homeTeam: String,
    val awayTeam: String,
    val homeGoals: Int?,
    val awayGoals: Int?,
    val result: String?,
    val oddsPinnacleHome: Double?,
    val oddsPinnacleDraw: Double?,
    val oddsPinnacleAway: Double?,
    val oddsAvgHome: Double?,
    val oddsAvgDraw: Double?,
    val oddsAvgAway: Double?,
)
