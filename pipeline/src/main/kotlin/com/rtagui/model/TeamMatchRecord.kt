package com.rtagui.model

data class TeamMatchRecord(
    val teamName: String,
    val isHome: Boolean,
    val goalsFor: Int?,
    val goalsAgainst: Int?,
    val result: String?,
    val matchTimestamp: Long,
)
