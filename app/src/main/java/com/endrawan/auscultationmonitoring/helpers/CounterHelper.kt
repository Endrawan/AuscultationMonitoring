package com.endrawan.auscultationmonitoring.helpers

import kotlin.math.sqrt

class CounterHelper(private val sampleFrequency: Int) {

    private var peakStartIdx = 0
    private var peakEndIdx = 0
    private var lastPeakIdx = 0
    private var prevOutput = 0
    private var currentIndex = 0
    private val peaksIndexes = ArrayList<Int>()

    fun update(zScoreOutput: Int): ArrayList<Int> {
//        val result = smoothedZScore.update(sample)
        val output = zScoreOutput

        if(output == 1 && prevOutput < 1) {
            peakStartIdx = currentIndex
            peakEndIdx = currentIndex
        } else if (output == 1 && prevOutput == 1) {
            peakEndIdx = currentIndex
        } else if (output < 1 && prevOutput == 1) {
            lastPeakIdx = (peakStartIdx + peakEndIdx) / 2
            peaksIndexes.add(lastPeakIdx)
        }

        prevOutput = output
        currentIndex++

        return peaksIndexes
    }

    fun getHrv(): Double { // Calculate using RMSSD
        filterPeaksIndexesToLastMinute()
        if(peaksIndexes.size < 3) {
            return 0.0
        }

        var prevRrInterval = ((peaksIndexes[1] - peaksIndexes[0]).toDouble() / sampleFrequency) * 1000 // Convert to milisecond
        var rrInterval: Double
        var totalSquared = 0.0
        for(idx in 2 until peaksIndexes.size) {
            rrInterval = ((peaksIndexes[idx] - peaksIndexes[idx - 1]).toDouble() / sampleFrequency) * 1000 // Convert to milisecond
            val diff = rrInterval - prevRrInterval
            totalSquared += diff * diff

            prevRrInterval = rrInterval
        }

        val meanSquared = totalSquared / (peaksIndexes.size - 2)

        return sqrt(meanSquared)
    }

    fun getHeartBpm(): Int {
//        val totalSamplesInMinute = sampleFrequency * 60
//        val samplesThreshold = currentIndex - totalSamplesInMinute - 1
//        var peaksPerMinute = 0
//
//        peaksIndexes.forEach {
//            if(it >= samplesThreshold) {
//                peaksPerMinute++
//            }
//        }
//
//        return peaksPerMinute // / 2 // Because there are 2 dominant wave
        filterPeaksIndexesToLastMinute()
        return peaksIndexes.size
    }

    fun getRespirationRate(): Int {
        val totalSamplesInMinute = sampleFrequency * 60
        val samplesThreshold = currentIndex - totalSamplesInMinute - 1
        var peaksPerMinute = 0

        peaksIndexes.forEach {
            if(it >= samplesThreshold) {
                peaksPerMinute++
            }
        }

        return peaksPerMinute
    }

    fun reset() {
        peakStartIdx = 0
        peakEndIdx = 0
        lastPeakIdx = 0
        prevOutput = 0
        currentIndex = 0
        peaksIndexes.clear()
    }

    private fun filterPeaksIndexesToLastMinute() {
        val totalSamplesInMinute = sampleFrequency * 60
        val samplesThreshold = currentIndex - totalSamplesInMinute - 1
        var endIndex = -1

        for(i in peaksIndexes.indices) {
            if(peaksIndexes[i] < samplesThreshold) endIndex = i
            else break
        }

        for (i in endIndex downTo 0) {
            peaksIndexes.removeAt(i)
        }
    }
}