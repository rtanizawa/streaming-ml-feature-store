package com.yourorg.transforms

// TODO: define input event and output feature types
// class VelocityAggregator : AggregateFunction<InputEvent, Accumulator, VelocityFeatures> {
//
//     override fun createAccumulator() = Accumulator()
//
//     override fun add(value: InputEvent, acc: Accumulator): Accumulator {
//         acc.count++
//         acc.sum += value.amount
//         return acc
//     }
//
//     override fun getResult(acc: Accumulator) = VelocityFeatures(acc.count, acc.sum)
//
//     override fun merge(a: Accumulator, b: Accumulator) =
//         Accumulator(a.count + b.count, a.sum + b.sum)
// }
