package com.endrawan.auscultationmonitoring.utils

class CircularIntBuffer(val capacity: Int) {
    val buffer = IntArray(capacity) { 0 }
    var bufIdx: Int = 0

    fun add(value: Int) {
        buffer[bufIdx++] = value
        if(bufIdx == capacity) {
            bufIdx = 0
        }
    }

    fun getSequence(): IntArray {
        val result = IntArray(capacity) {0}
        for (i in 0 until capacity) {
            result[i] = buffer[(bufIdx + i) % capacity]
        }
        return result
    }

    fun getRecent(): Int {
        return buffer[(bufIdx - 1).mod(capacity)]
    }
}