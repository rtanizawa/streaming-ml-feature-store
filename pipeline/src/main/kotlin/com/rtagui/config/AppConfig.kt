package com.rtagui.config

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

@JsonIgnoreProperties(ignoreUnknown = true)
data class AppConfig(
    val kafka: KafkaConfig = KafkaConfig(),
    val redis: RedisConfig = RedisConfig(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class KafkaConfig(
    val bootstrapServers: String = "localhost:9092"
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RedisConfig(
    val uri: String = "redis://localhost:6379"
)

object AppConfigLoader {
    fun load(): AppConfig {
        val mapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
        val resource = AppConfigLoader::class.java.classLoader.getResourceAsStream("application.yml")
            ?: return AppConfig()
        return mapper.readValue(resource, AppConfig::class.java)
    }
}
