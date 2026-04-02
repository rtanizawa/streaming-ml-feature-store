package com.rtagui.sink

import com.rtagui.model.TeamStats
import org.apache.flink.api.connector.sink2.Sink
import org.apache.flink.api.connector.sink2.SinkWriter
import org.apache.flink.api.connector.sink2.WriterInitContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant

class FeastHttpPushSink(private val feastUrl: String) : Sink<TeamStats> {
    override fun createWriter(context: WriterInitContext): SinkWriter<TeamStats> =
        FeastWriter(feastUrl)
}

internal class FeastWriter(private val feastUrl: String) : SinkWriter<TeamStats> {

    private val httpClient: HttpClient = HttpClient.newHttpClient()

    override fun write(stats: TeamStats, context: SinkWriter.Context) {
        val teamName = stats.teamName.lowercase().replace(" ", "_")
        val eventTimestamp = Instant.ofEpochMilli(stats.eventTimestamp).toString()

        val payload = """
            {
              "push_source_name": "team_stats_push_source",
              "df": {
                "team_name":       ["$teamName"],
                "matches_played":  [${stats.matchesPlayed}],
                "wins":            [${stats.wins}],
                "draws":           [${stats.draws}],
                "losses":          [${stats.losses}],
                "goals_for":       [${stats.goalsFor}],
                "goals_against":   [${stats.goalsAgainst}],
                "event_timestamp": ["$eventTimestamp"]
              },
              "to": "online_and_offline"
            }
        """.trimIndent()

        val request = HttpRequest.newBuilder()
            .uri(URI.create("$feastUrl/push"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build()

        httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    override fun flush(endOfInput: Boolean) {}
    override fun close() {}
}
