package com.endrawan.auscultationmonitoring.utils

class CircularDoubleBuffer(val capacity: Int) {
//    val buffer = DoubleArray(capacity) { 0.0 }
//    var bufIdx: Int = 0
//
//    fun add(value: Double) {
//        buffer[bufIdx++] = value
//        if(bufIdx == capacity) {
//            bufIdx = 0
//        }
//    }
//
//    fun getSequence(): DoubleArray {
//        val result = DoubleArray(capacity) {0.0}
//        for (i in 0 until capacity) {
//            result[i] = buffer[(bufIdx + i) % capacity]
//        }
//        return result
//    }
//
//    fun getRecent(): Double {
//        return buffer[(bufIdx - 1).mod(capacity)]
//    }

    val buffer = mutableListOf<Double>()

    fun add(value: Double) {
        buffer.add(value)
        if(buffer.size > capacity) {
            buffer.removeFirst()
        }
    }

    fun getRecent(): Double {
        return buffer[buffer.lastIndex]
    }
}