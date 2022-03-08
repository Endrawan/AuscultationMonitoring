package com.endrawan.auscultationmonitoring.helpers

import com.endrawan.auscultationmonitoring.configs.Config.OPTION_HEART
import com.endrawan.auscultationmonitoring.configs.Config.OPTION_LUNG
import com.endrawan.auscultationmonitoring.models.Sample
import com.endrawan.auscultationmonitoring.models.ZScoreResult
import com.endrawan.auscultationmonitoring.utils.SmoothedZScore
import java.io.DataOutputStream

class ParameterHelper(
    val heartZScore: SmoothedZScore,
    val lungZScore: SmoothedZScore,
    val heartCounterHelper: CounterHelper,
    val lungCounterHelper: CounterHelper,
) {
    companion object {
        val DIAGNOSTIC_ENABLED = true
        val DIAGNOSTIC_DISABLED = false
    }

    private val results = mutableListOf<ZScoreResult>()

    fun findParameter(sample: Sample, filterOption: Int) {
        when(filterOption) {
            OPTION_HEART -> {
                val zScoreResult = heartZScore.update(sample.value)
                heartCounterHelper.update(zScoreResult.peak)
//                if (diagnosticStatus == DIAGNOSTIC_ENABLED) storeParameter(zScoreResult)
            }
            OPTION_LUNG -> {
                val zScoreResult = lungZScore.update(sample.value)
                lungCounterHelper.update(zScoreResult.peak)
//                if (diagnosticStatus == DIAGNOSTIC_ENABLED) storeParameter(zScoreResult)
            }
        }
    }

    fun findParameter(signal: List<Sample>, filterOption: Int) {
        val runnable = Runnable {
            when(filterOption) {
                OPTION_HEART -> findHeartParameter(signal)
                OPTION_LUNG -> findLungParameter(signal)
            }
        }
        Thread(runnable).start()
    }

    private fun findHeartParameter(signal: List<Sample>) {
        signal.forEach { s ->
            val zScoreResult = heartZScore.update(s.value)
            heartCounterHelper.update(zScoreResult.peak)
        }
    }

    private fun findLungParameter(signal: List<Sample>) {
        signal.forEach { s ->
            val zScoreResult = lungZScore.update(s.value)
            lungCounterHelper.update(zScoreResult.peak)
        }
    }

//    fun storeParameter(result: ZScoreResult) {
//        results.add(result)
//        if (results.size == minBufferSize) writeResultToFile()
//    }

//    fun writeResultToFile() {
//        val runnable = Runnable {
//            for(r in results) {
//                val littleEndian = ByteArray(2)
//                littleEndian[0] = (a.toInt() and 0xFF).toByte()
//                littleEndian[1] = ((a.toInt() shr 8) and 0xFF).toByte()
//                dataOutputStream.write(littleEndian)
//            }
//        }
//        Thread(runnable).start()
//    }

    fun getHeartRate(): Int = heartCounterHelper.getHeartBpm()

    fun getHRV(): Double = heartCounterHelper.getHrv()

    fun getRespirationRate(): Int = lungCounterHelper.getRespirationRate()

    fun reset() {
        heartZScore.reset()
        lungZScore.reset()
        heartCounterHelper.reset()
        lungCounterHelper.reset()
    }

//    private fun convertToLittleEndian()
}