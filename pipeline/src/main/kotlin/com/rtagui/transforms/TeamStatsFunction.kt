package com.rtagui.transforms

import com.rtagui.model.TeamMatchRecord
import com.rtagui.model.TeamStats
import org.apache.flink.api.common.functions.OpenContext
import org.apache.flink.api.common.state.ValueState
import org.apache.flink.api.common.state.ValueStateDescriptor
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.util.Collector

class TeamStatsFunction : KeyedProcessFunction<String, TeamMatchRecord, TeamStats>() {

    private lateinit var statsState: ValueState<TeamStats>

    override fun open(openContext: OpenContext) {
        val descriptor = ValueStateDescriptor("team-stats", TypeInformation.of(TeamStats::class.java))
        statsState = runtimeContext.getState(descriptor)
    }

    override fun processElement(record: TeamMatchRecord, ctx: Context, out: Collector<TeamStats>) {
        val current = statsState.value() ?: TeamStats(
            teamName = record.teamName,
            matchesPlayed = 0,
            wins = 0,
            draws = 0,
            losses = 0,
            goalsFor = 0,
            goalsAgainst = 0,
        )

        val updated = current.copy(
            matchesPlayed = current.matchesPlayed + 1,
            wins = current.wins + if (record.result == "W") 1 else 0,
            draws = current.draws + if (record.result == "D") 1 else 0,
            losses = current.losses + if (record.result == "L") 1 else 0,
            goalsFor = current.goalsFor + (record.goalsFor ?: 0),
            goalsAgainst = current.goalsAgainst + (record.goalsAgainst ?: 0),
        )

        statsState.update(updated)
        out.collect(updated)
    }
}
