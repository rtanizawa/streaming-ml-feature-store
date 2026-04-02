package com.rtagui.sink

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.rtagui.model.TeamStats
import io.kotest.core.spec.style.FunSpec
import org.apache.flink.api.connector.sink2.SinkWriter

class FeastHttpPushSinkIT : FunSpec({

    val wireMock = WireMockServer(wireMockConfig().dynamicPort())

    beforeSpec { wireMock.start() }
    afterSpec { wireMock.stop() }

    fun noOpContext() = object : SinkWriter.Context {
        override fun currentWatermark() = 0L
        override fun timestamp(): Long? = null
    }

    beforeTest {
        wireMock.stubFor(
            post(urlEqualTo("/push"))
                .willReturn(aResponse().withStatus(200).withBody("{}"))
        )
    }

    afterTest {
        wireMock.resetAll()
    }

    test("POSTs correct JSON payload to Feast /push endpoint") {
        val writer = FeastWriter("http://localhost:${wireMock.port()}")

        val stats = TeamStats(
            teamName = "Flamengo",
            matchesPlayed = 38,
            wins = 20,
            draws = 10,
            losses = 8,
            goalsFor = 65,
            goalsAgainst = 40,
            eventTimestamp = 1731196800000L,
        )
        writer.write(stats, noOpContext())

        wireMock.verify(
            postRequestedFor(urlEqualTo("/push"))
                .withRequestBody(containing("\"push_source_name\": \"team_stats_push_source\""))
                .withRequestBody(containing("\"team_name\":       [\"flamengo\"]"))
                .withRequestBody(containing("\"matches_played\":  [38]"))
                .withRequestBody(containing("\"wins\":            [20]"))
                .withRequestBody(containing("\"draws\":           [10]"))
                .withRequestBody(containing("\"losses\":          [8]"))
                .withRequestBody(containing("\"goals_for\":       [65]"))
                .withRequestBody(containing("\"goals_against\":   [40]"))
                .withRequestBody(containing("\"to\": \"online_and_offline\""))
        )
    }

    test("normalises team name with spaces to underscores") {
        val writer = FeastWriter("http://localhost:${wireMock.port()}")

        val stats = TeamStats(
            teamName = "Atletico Mineiro",
            matchesPlayed = 1, wins = 1, draws = 0, losses = 0,
            goalsFor = 2, goalsAgainst = 0,
            eventTimestamp = 1731196800000L,
        )
        writer.write(stats, noOpContext())

        wireMock.verify(
            postRequestedFor(urlEqualTo("/push"))
                .withRequestBody(containing("\"team_name\":       [\"atletico_mineiro\"]"))
        )
    }
})
