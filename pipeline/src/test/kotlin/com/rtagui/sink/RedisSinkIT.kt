package com.rtagui.sink

import com.rtagui.model.TeamStats
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.lettuce.core.RedisClient
import org.apache.flink.api.common.functions.OpenContext
import org.testcontainers.containers.GenericContainer

class RedisSinkIT : FunSpec({

    val redis = GenericContainer<Nothing>("redis:7-alpine").apply {
        withExposedPorts(6379)
    }

    beforeSpec { redis.start() }
    afterSpec { redis.stop() }

    lateinit var sink: RedisSink

    beforeTest {
        val uri = "redis://${redis.host}:${redis.getMappedPort(6379)}"
        sink = RedisSink(uri)
        sink.open(object : OpenContext {})
    }

    afterTest {
        sink.close()
    }

    fun noOpContext() = object : org.apache.flink.streaming.api.functions.sink.legacy.SinkFunction.Context {
        override fun currentProcessingTime() = 0L
        override fun currentWatermark() = 0L
        override fun timestamp() = null
    }

    test("writes team stats as hash to Redis") {
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
            hash["matchesPlayed"] shouldBe "38"
            hash["wins"] shouldBe "20"
            hash["draws"] shouldBe "10"
            hash["losses"] shouldBe "8"
            hash["goalsFor"] shouldBe "65"
            hash["goalsAgainst"] shouldBe "40"
        }
    }

    test("replaces spaces with underscores in key") {
        val stats = TeamStats(
            teamName = "Atletico Mineiro",
            matchesPlayed = 1, wins = 1, draws = 0, losses = 0,
            goalsFor = 2, goalsAgainst = 0,
        )
        sink.invoke(stats, noOpContext())

        val uri = "redis://${redis.host}:${redis.getMappedPort(6379)}"
        RedisClient.create(uri).connect().use { conn ->
            val hash = conn.sync().hgetall("team_stats:atletico_mineiro")
            hash["matchesPlayed"] shouldBe "1"
        }
    }
})
