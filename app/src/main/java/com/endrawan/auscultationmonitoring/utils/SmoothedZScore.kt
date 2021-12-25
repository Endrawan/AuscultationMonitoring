package com.endrawan.auscultationmonitoring.utils

import android.util.Log
import org.apache.commons.math3.stat.descriptive.SummaryStatistics
import java.util.*
import kotlin.math.abs
import kotlin.math.sqrt

class SmoothedZScore(private val lag: Int, private val threshold: Double, private val influence: Double) {
    val TAG = "SmoothedZScore"
    val stats = SummaryStatistics()
//    val rawBuffer = CircularDoubleBuffer(lag)
    var filteredBuffer = CircularDoubleBuffer(lag)
    var prevAvgFilter = 0.0
    var prevStdFilter = 0.0
    var count = 0

    fun update(value: Double): Triple<Int, Double, Double> {
//        Log.d(TAG, "newData: $value, prevAvgFilter:$prevAvgFilter, prevStdFilter: $prevStdFilter, count: $count")

        val outputPeak: Int
        val outputAvgFilter: Double
        val outputStdFilter: Double

        if (count < lag) {
//            rawBuffer.add(value)
            filteredBuffer.add(value)
            count++
            if(count == lag) {
//                filteredBuffer.getSequence().forEach { stats.addValue(it) }
                filteredBuffer.buffer.forEach { stats.addValue(it) }
                prevAvgFilter = stats.mean
                prevStdFilter = sqrt(stats.populationVariance)
                stats.clear()
            }
            outputPeak = 0
            outputAvgFilter = 0.0
            outputStdFilter = 0.0
        } else {

            outputAvgFilter = prevAvgFilter
            outputStdFilter = prevStdFilter

            if (abs(value - prevAvgFilter) > (threshold * prevStdFilter)) {
                outputPeak = if (value > prevAvgFilter) 1 else -1
                val newFiltered = (influence * value) + ((1 - influence) * filteredBuffer.getRecent())
//                Log.d(TAG, "NewFiltered: $newFiltered")
                filteredBuffer.add(newFiltered)
            } else {
                outputPeak = 0
//                Log.d(TAG, "value: $value")
                filteredBuffer.add(value)
            }
//            filteredBuffer.getSequence().forEach { stats.addValue(it) }
            filteredBuffer.buffer.forEach { stats.addValue(it) }
            prevAvgFilter = stats.mean
            prevStdFilter = sqrt(stats.populationVariance)
            stats.clear()
        }

//        val arrayString = filteredBuffer.getSequence().contentToString()
//        Log.d(TAG, "buffer: $arrayString")

        return Triple(outputPeak, outputAvgFilter, outputStdFilter)
    }

    fun reset() {
        stats.clear()
        filteredBuffer = CircularDoubleBuffer(lag)
        prevAvgFilter = 0.0
        prevStdFilter = 0.0
        count = 0
    }
}