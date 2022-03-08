package com.endrawan.auscultationmonitoring.models

import com.endrawan.auscultationmonitoring.configs.Config

data class ZScoreResult(
    val peak: Int,
    val average: Double,
    val deviation: Double
) {
    fun getPositiveDeviation(threshold: Float): Double {
        return average + threshold * deviation
    }

    fun getNegativeDeviation(threshold: Float): Double {
        return average - threshold * deviation
    }
}
