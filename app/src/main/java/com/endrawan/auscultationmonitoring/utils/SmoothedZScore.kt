package com.endrawan.auscultationmonitoring.utils

import android.util.Log
import com.endrawan.auscultationmonitoring.models.ZScoreResult
import org.apache.commons.math3.stat.descriptive.SummaryStatistics
import java.util.*
import kotlin.math.abs
import kotlin.math.sqrt

class SmoothedZScore(private val lag: Int, private val threshold: Double, private val influence: Double) {
    val TAG = "SmoothedZScore"
    val stats = SummaryStatistics()
    var filteredBuffer = CircularDoubleBuffer(lag)
    var prevAvgFilter = 0.0
    var prevStdFilter = 0.0
    var count = 0

    fun update(value: Double): ZScoreResult {
        val outputPeak: Int
        val outputAvgFilter: Double
        val outputStdFilter: Double

        if (count < lag) {
            filteredBuffer.add(value)
            count++
            if(count == lag) {
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
                filteredBuffer.add(newFiltered)
            } else {
                outputPeak = 0
                filteredBuffer.add(value)
            }
            filteredBuffer.buffer.forEach { stats.addValue(it) }
            prevAvgFilter = stats.mean
            prevStdFilter = sqrt(stats.populationVariance)
            stats.clear()
        }
        return ZScoreResult(outputPeak, outputAvgFilter, outputStdFilter)
//        return Triple(outputPeak, outputAvgFilter, outputStdFilter)
    }

    fun reset() {
        stats.clear()
        filteredBuffer = CircularDoubleBuffer(lag)
        prevAvgFilter = 0.0
        prevStdFilter = 0.0
        count = 0
    }
}