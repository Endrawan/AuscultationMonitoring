package com.endrawan.auscultationmonitoring.configs

object Config {
    val AUSCULTATION_IDLE = 0
    val AUSCULTATION_RECORDING = 1
    val AUSCULTATION_PAUSED = 2

    val WRITE_WAIT_MILLIS = 2000

    val OPTION_UNFILTERED = 100
    val OPTION_HEART = 200
    val OPTION_LUNG = 300

    val COMMAND_FILTER_UNFILTERED = "0".toByteArray()
    val COMMAND_FILTER_HEART = "1".toByteArray()
    val COMMAND_FILTER_LUNG = "2".toByteArray()

    val BAUD_RATE = 500000
}