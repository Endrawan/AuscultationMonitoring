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
    val DATA_BITS = 8
    val AUDIO_FREQUENCY = 4000

    val ACTUAL_MIN = 0
    val ACTUAL_MAX = 1023

    val TEMP_AUDIO_FILENAME = "temp_audio.pcm"
    val TEMP_AVERAGE_FILENAME = "temp_average.pcm"
    val TEMP_DEVIATION_FILENAME = "temp_deviation.pcm"
    val TEMP_PEAK_FILENAME = "temp_peak.pcm"

    val ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION"

    //@formatter:off
    val dummySignal = listOf(1.0, 1.0, 1.1, 1.0, 0.9, 1.0, 1.0, 1.1, 1.0, 0.9, 1.0, 1.1, 1.0, 1.0, 0.9, 1.0,
        1.0, 1.1,1.0, 1.0,1.0, 1.0, 1.1, 0.9, 1.0, 1.1, 1.0, 1.0, 0.9, 1.0, 1.1, 1.0, 1.0, 1.1, 1.0, 0.8,
        0.9, 1.0, 1.2, 0.9, 1.0,1.0, 1.1, 1.2, 1.0, 1.5, 1.0, 3.0, 2.0, 5.0, 3.0, 2.0, 1.0, 1.0, 1.0,
        0.9, 1.0,1.0, 3.0, 2.6, 4.0, 3.0, 3.2, 2.0, 1.0, 1.0, 0.8, 4.0, 4.0, 2.0, 2.5, 1.0, 1.0, 1.0)
    //@formatter:on

//    val ran = Random
//    val freq = 100
//    val freq_sampling = 4000
//    var dummySignal  = (1..40000).mapIndexed { index, _ ->
//        ran.nextInt(1023) * sin(2 * PI * freq * index / freq_sampling)
//    }

    val DEFAULT_BOUND = 30
    val DEFAULT_MIN_SCOPE = 60 / DEFAULT_BOUND * AUDIO_FREQUENCY

    val HEART_Z_SCORE_LAG = 500
    val HEART_Z_SCORE_THRESHOLD = 8f
    val HEART_Z_SCORE_INFLUENCE = 0.0

    val LUNG_Z_SCORE_LAG = 200
    val LUNG_Z_SCORE_THRESHOLD = 6f
    val LUNG_Z_SCORE_INFLUENCE = 0.0
}