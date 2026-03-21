package com.yourorg.sink

import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection

// TODO: define VelocityFeatures type
// class RedisSink : SinkFunction<VelocityFeatures> {
//
//     @Transient
//     private lateinit var client: RedisClient
//
//     @Transient
//     private lateinit var connection: StatefulRedisConnection<String, String>
//
//     override fun invoke(features: VelocityFeatures, context: Context) {
//         val commands = connection.async()
//         commands.hset("velocity:user:${features.userId}", mapOf(
//             "txn_count_1h" to features.txnCount1h.toString(),
//             "txn_sum_1h" to features.txnSum1h.toString(),
//         ))
//         commands.expire("velocity:user:${features.userId}", 86400)
//     }
// }
