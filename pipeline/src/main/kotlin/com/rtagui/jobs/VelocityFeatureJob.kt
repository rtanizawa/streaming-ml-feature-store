package com.rtagui.jobs

//import com.rtagui.sink.RedisSink
//import com.rtagui.transforms.VelocityAggregator
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment

class VelocityFeatureJob {

    fun run() {
        val env = StreamExecutionEnvironment.getExecutionEnvironment()

        // TODO: add Kafka source
        // val source = KafkaSource...

        // TODO: apply windowed aggregation and write to Redis
        // env.fromSource(source, ...)
        //    .keyBy { it.userId }
        //    .window(TumblingEventTimeWindows.of(Time.hours(1)))
        //    .aggregate(VelocityAggregator())
        //    .addSink(RedisSink())

        env.execute("VelocityFeatureJob")
    }
}
