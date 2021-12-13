package com.endrawan.auscultationmonitoring.configs

import com.endrawan.auscultationmonitoring.models.Audio

object Dummy {
    val audioDummies = listOf(
        Audio("Swimming pools.pcm", "00:00:18", "12-02-2021"),
        Audio("All of the lights.pcm", "00:03:18", "12-03-2021"),
        Audio("New Slaves.pcm", "00:04:18", "12-04-2021"),
        Audio("Devil in a new dress.pcm", "00:09:18", "10-12-2021")
    )
}