package com.endrawan.auscultationmonitoring.callbacks

interface GraphCallbacks {
    fun drawZScore(output: MutableList<Int>)
    fun drawEnvelope(envelope: DoubleArray)
    fun plotPeaks(sampleIndex: IntArray)
}