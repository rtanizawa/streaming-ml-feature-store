package com.rtagui.transforms

object TeamNames {
    fun normalize(team: String): String = team.lowercase().replace(" ", "_")
}
