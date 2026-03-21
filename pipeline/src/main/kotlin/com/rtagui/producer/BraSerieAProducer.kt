package com.rtagui.producer

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.commons.csv.CSVFormat
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Properties
import java.util.UUID

private val logger = LoggerFactory.getLogger("com.rtagui.producer.BraSerieAProducer")

private fun bomStrippedReader(path: String): BufferedReader {
    val reader = BufferedReader(InputStreamReader(FileInputStream(path), Charsets.UTF_8))
    reader.mark(1)
    if (reader.read() != '\uFEFF'.code) reader.reset()
    return reader
}

fun main(args: Array<String>) {
    val csvPath = args.getOrElse(0) { "BRA.csv" }
    val bootstrapServers = args.getOrElse(1) { "localhost:29092" }
    val scaleMs = args.getOrElse(2) { "10" }.toLong()

    val mapper = jacksonObjectMapper()
    val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val topic = "bra-serie-a-matches"

    val props = Properties().apply {
        put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
        put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
        put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
        put(ProducerConfig.ACKS_CONFIG, "all")
    }

    val records = CSVFormat.DEFAULT.builder()
        .setHeader()
        .setSkipHeaderRecord(true)
        .build()
        .parse(bomStrippedReader(csvPath))
        .records

    val grouped: Map<LocalDate, List<org.apache.commons.csv.CSVRecord>> = records
        .sortedBy { LocalDate.parse(it["Date"], dateFormatter) }
        .groupBy { LocalDate.parse(it["Date"], dateFormatter) }

    val dates = grouped.keys.sorted()

    KafkaProducer<String, String>(props).use { producer ->
        dates.forEachIndexed { index, date ->
            val batch = grouped[date] ?: return@forEachIndexed

            for (csv in batch) {
                val event = SoccerMatchEvent(
                    eventId = UUID.randomUUID().toString(),
                    matchDate = date.toString(),
                    matchTime = csv.get("Time").orEmpty(),
                    season = csv.get("Season").trim().toIntOrNull() ?: 0,
                    country = csv.get("Country").orEmpty(),
                    league = csv.get("League").orEmpty(),
                    homeTeam = csv.get("Home").orEmpty(),
                    awayTeam = csv.get("Away").orEmpty(),
                    homeGoals = csv.get("HG").trim().toIntOrNull(),
                    awayGoals = csv.get("AG").trim().toIntOrNull(),
                    result = csv.get("Res").trim().takeIf { it.isNotEmpty() },
                    oddsPinnacleHome = csv.get("PSCH").trim().toDoubleOrNull(),
                    oddsPinnacleDraw = csv.get("PSCD").trim().toDoubleOrNull(),
                    oddsPinnacleAway = csv.get("PSCA").trim().toDoubleOrNull(),
                    oddsMaxHome = csv.get("MaxCH").trim().toDoubleOrNull(),
                    oddsMaxDraw = csv.get("MaxCD").trim().toDoubleOrNull(),
                    oddsMaxAway = csv.get("MaxCA").trim().toDoubleOrNull(),
                    oddsAvgHome = csv.get("AvgCH").trim().toDoubleOrNull(),
                    oddsAvgDraw = csv.get("AvgCD").trim().toDoubleOrNull(),
                    oddsAvgAway = csv.get("AvgCA").trim().toDoubleOrNull(),
                )
                val json = mapper.writeValueAsString(event)
                producer.send(ProducerRecord(topic, event.homeTeam, json))
            }

            if (index < dates.size - 1) {
                val nextDate = dates[index + 1]
                val daysDiff = ChronoUnit.DAYS.between(date, nextDate)
                if (daysDiff > 0) Thread.sleep(daysDiff * scaleMs)
            }
        }
        producer.flush()
    }

    logger.info("Done producing {} events to topic '{}'.", records.size, topic)
}
