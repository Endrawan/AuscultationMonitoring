package com.endrawan.auscultationmonitoring.callbacks

interface BluetoothCallbacks {
    fun onDeviceConnected()
    fun onNewData(incomingData: Short)
}