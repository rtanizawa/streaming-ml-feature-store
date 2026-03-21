package com.rtagui.sink

import com.rtagui.model.TeamStats
import io.lettuce.core.RedisClient
import org.apache.flink.api.common.functions.OpenContext
import org.apache.flink.configuration.Configuration
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals

@Testcontainers
class RedisSinkIT {

    companion object {
        @Container
        @JvmStatic
        val redis = GenericContainer<Nothing>("redis:7-alpine").apply {
            withExposedPorts(6379)
        }
    }

    private lateinit var sink: RedisSink

    @BeforeEach
    fun setUp() {
        val uri = "redis://${redis.host}:${redis.getMappedPort(6379)}"
        sink = RedisSink(uri)
        sink.open(object : OpenContext {})
    }

    @AfterEach
    fun tearDown() {
        sink.close()
    }

    private fun noOpContext() = object : org.apache.flink.streaming.api.functions.sink.legacy.SinkFunction.Context {
        override fun currentProcessingTime() = 0L
        override fun currentWatermark() = 0L
        override fun timestamp() = null
    }

    @Test
    fun `writes team stats as hash to Redis`() {
        val stats = TeamStats(
            teamName = "Flamengo",
            matchesPlayed = 38,
            wins = 20,
            draws = 10,
            losses = 8,
            goalsFor = 65,
            goalsAgainst = 40,
        )
        sink.invoke(stats, noOpContext())

        val uri = "redis://${redis.host}:${redis.getMappedPort(6379)}"
        RedisClient.create(uri).connect().use { conn ->
            val hash = conn.sync().hgetall("team_stats:flamengo")
            assertEquals("38", hash["matchesPlayed"])
            assertEquals("20", hash["wins"])
            assertEquals("10", hash["draws"])
            assertEquals("8", hash["losses"])
            assertEquals("65", hash["goalsFor"])
            assertEquals("40", hash["goalsAgainst"])
        }
    }

    @Test
    fun `replaces spaces with underscores in key`() {
        val stats = TeamStats(
            teamName = "Atletico Mineiro",
            matchesPlayed = 1, wins = 1, draws = 0, losses = 0,
            goalsFor = 2, goalsAgainst = 0,
        )
        sink.invoke(stats, noOpContext())

        val uri = "redis://${redis.host}:${redis.getMappedPort(6379)}"
        RedisClient.create(uri).connect().use { conn ->
            val hash = conn.sync().hgetall("team_stats:atletico_mineiro")
            assertEquals("1", hash["matchesPlayed"])
        }
    }
}
