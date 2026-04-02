package com.rtagui.model

data class TeamStats(
    val teamName: String,
    val matchesPlayed: Int,
    val wins: Int,
    val draws: Int,
    val losses: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
    val eventTimestamp: Long = 0L,
)
