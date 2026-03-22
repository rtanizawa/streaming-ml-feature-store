package com.rtagui.sink

import com.rtagui.model.TeamStats
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import org.apache.flink.api.common.functions.OpenContext
import org.apache.flink.streaming.api.functions.sink.legacy.RichSinkFunction
import org.apache.flink.streaming.api.functions.sink.legacy.SinkFunction

class RedisSink(private val redisUri: String = "redis://localhost:6379") : RichSinkFunction<TeamStats>() {

    @Transient
    private lateinit var client: RedisClient

    @Transient
    private lateinit var connection: StatefulRedisConnection<String, String>

    override fun open(openContext: OpenContext) {
        client = RedisClient.create(redisUri)
        connection = client.connect()
    }

    override fun invoke(stats: TeamStats, context: SinkFunction.Context) {
        val key = "team_stats:${stats.teamName.lowercase().replace(" ", "_")}"
        val commands = connection.sync()
        commands.hset(
            key,
            mapOf(
                "matchesPlayed" to stats.matchesPlayed.toString(),
                "wins" to stats.wins.toString(),
                "draws" to stats.draws.toString(),
                "losses" to stats.losses.toString(),
                "goalsFor" to stats.goalsFor.toString(),
                "goalsAgainst" to stats.goalsAgainst.toString(),
            )
        )
    }

    override fun close() {
        if (::connection.isInitialized) connection.close()
        if (::client.isInitialized) client.shutdown()
    }
}
