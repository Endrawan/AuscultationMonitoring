package com.endrawan.auscultationmonitoring.helpers

import com.endrawan.auscultationmonitoring.configs.Config.ACTUAL_MAX
import kotlin.experimental.and

class DataHelper(val listener: DataHelperListener) {

    private val recentPacket = mutableListOf<Short>()

    private var tempBuffer = ""
    private var openBracketReceived = false
    private var closeBracketReceived = false
    private var commaReceived = false

    fun update(data: ByteArray) {
        for (d in data) {
            when(val decimalAscii = d.toInt()) {
                91 -> openBracketReceived = true
                93 -> closeBracketReceived = true
                44 -> commaReceived = true
                else -> {
                    if (openBracketReceived) tempBuffer += decimalAscii.toChar()
                }
            }

            if(commaReceived) {
                newDataHandler()
            }

            if(openBracketReceived && closeBracketReceived) {
                newDataHandler()

                openBracketReceived = false
                closeBracketReceived = false

                listener.onPacketReceived(recentPacket)
                recentPacket.clear()
            }
        }
    }

    private fun newDataHandler() {
        var newSample = tempBuffer.toShortOrNull()
        tempBuffer = ""
        commaReceived = false

        if(newSample != null) {
            newSample = newSample and ACTUAL_MAX.toShort()
            recentPacket.add(newSample)
            listener.onNewSample(newSample)
        }
    }

    interface DataHelperListener {
        fun onNewSample(sample: Short)
        fun onPacketReceived(packet: List<Short>)
    }
}