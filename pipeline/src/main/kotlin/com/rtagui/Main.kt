package com.rtagui

import com.rtagui.config.AppConfigLoader
import com.rtagui.jobs.VelocityFeatureJob

fun main() {
    val config = AppConfigLoader.load()
    VelocityFeatureJob(
        bootstrapServers = config.kafka.bootstrapServers,
        redisUri = config.redis.uri,
    ).run()
}
