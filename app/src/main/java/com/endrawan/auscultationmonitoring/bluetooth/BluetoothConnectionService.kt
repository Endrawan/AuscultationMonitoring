package com.endrawan.auscultationmonitoring.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import com.endrawan.auscultationmonitoring.callbacks.BluetoothCallbacks
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.*
import kotlin.experimental.and

class BluetoothConnectionService(val bluetoothAdapter: BluetoothAdapter, val bluetoothCallbacks: BluetoothCallbacks) {
    private val TAG = "BluetoothConnectionServ"
    private val appName = "Auscultation Monitoring"
    private val appUUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")

    private lateinit var mmDevice: BluetoothDevice
//    private lateinit var deviceSocket: BluetoothSocket
    private lateinit var deviceUUID: UUID
    private var connectThread: ConnectThread? = null
    private var connectedThread: ConnectedThread? = null

    private var IS_LISTENING = false

    private var NL_active = false
    private var CR_active = false
    private var tempBuffer = ""

    /**
     * This thread runs while attempting to make an outgoing connectoin
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */

    private inner class ConnectThread(device: BluetoothDevice, uuid: UUID) : Thread() {
        private lateinit var mmSocket: BluetoothSocket

        init {
            Log.d(TAG, "ConnectThread: Initialized")
            mmDevice = device
            deviceUUID = uuid
        }

        @SuppressLint("MissingPermission")
        override fun run() {
            var tmp: BluetoothSocket? = null
            Log.i(TAG, "run: RUN mConnectThread")

            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice

            try {
                Log.d(
                    TAG, "ConnectThread: Trying to create " +
                            "InsecureRfcommSocket using UUID: $appUUID"
                )
                tmp = mmDevice.createRfcommSocketToServiceRecord(appUUID)
            } catch (e: IOException) {
                Log.d(TAG, "ConnectThread: Could not create InsecureRfcommSocket ${e.message}")
            }

            mmSocket = tmp!!
//            deviceSocket = tmp!!

            // Always cancel discovery because it will slow down a connection
            bluetoothAdapter.cancelDiscovery()

            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mmSocket.connect()
//                deviceSocket.connect()

                Log.d(TAG, "run: ConnectThread connected.")
                bluetoothCallbacks.onDeviceConnected()
            } catch (e: IOException) {
                // Close the socket
                try {
                    mmSocket.close()
//                    deviceSocket.close()
                    Log.d(TAG, "run: Closed socket.")
                } catch (e1: IOException) {
                    Log.e(
                        TAG,
                        "mConnectThread: run: Unable to close connection in socket ${e1.message}")
                }
                Log.d(TAG, "run: ConnectThread: Could not connect to UUID: $appUUID")
            }

            connected(mmSocket, mmDevice)
        }
        
        fun cancel() {
            try {
                Log.d(TAG, "cancel: Closing Client Socket.")
                mmSocket.close()
//                deviceSocket.close()
            } catch (e: IOException) {
                Log.e(TAG, "cancel: close() of mmSocket in ConnectThread is failed. ${e.message}")
            }
        }
    }

    fun startClient(device: BluetoothDevice, uuid: UUID) {
        Log.d(TAG, "startClient: Started.")

        connectThread = ConnectThread(device, uuid)
        connectThread?.start()
    }

    fun startListening() {
        IS_LISTENING = true
    }

    fun stopListening() {
        IS_LISTENING = false
    }

    /**
     * Stop all running bluetooth thread
     */
    @Synchronized
    fun stop() {
        Log.d(TAG, "stop: Stop thread")

        if(connectThread != null) {
            connectThread?.cancel()
            connectThread = null
        }

        if(connectedThread != null) {
            connectedThread?.cancel()
            connectedThread = null
        }
    }

    private inner class ConnectedThread(val mmSocket: BluetoothSocket) : Thread() {

        private var inputStream: InputStream
        private var outputStream: OutputStream

        init {
            Log.d(TAG, "ConnectedThread: Starting.")

            var tmpIn: InputStream? = null
            var tmpOut: OutputStream? = null

            Log.d(TAG, "ConnectedThread: init: Bluetooth connected")

            try {
                tmpIn = mmSocket.inputStream
                tmpOut = mmSocket.outputStream
            } catch (e: IOException) {
                e.printStackTrace()
            }

            inputStream = tmpIn!!
            outputStream = tmpOut!!
        }

        override fun run() {
            val buffer = ByteArray(1024)

            var bytes = 0

            while (true) {
                try {
                    if(inputStream.available() > 0) {
                        val data = ByteArray(inputStream.available())
                        val len = inputStream.read(data)
                        if(IS_LISTENING) {
                            processRawData(data)
                        }
//                        onNewDataDiagnostic(data)
                    }

//                    val len = inputStream.read(buffer)
//                    val data = buffer.copyOf(len)

//                    bytes = inputStream.read(buffer)
//                    val incomingMessage = String(buffer, 0, bytes)
//                    if(IS_LISTENING) {
//                        processRawData(data)
//                    }
                } catch (e: IOException) {
                    Log.e(TAG, "write: Error reading Input Stream. ${e.message}" )
                    break
                }
            }
        }

        fun write(bytes: ByteArray) {
            val text = String(bytes, Charset.defaultCharset())
            Log.d(TAG, "write: Writing to outputstream: $text")
            try {
                outputStream.write(bytes)
            } catch (e: IOException) {
                Log.e(TAG, "write: Error writing to output stream. ${e.message}")
            }
        }

        fun cancel() {
            try {
                mmSocket.close()
            } catch (e: IOException) { }
        }
    }

    private fun connected(mmSocket: BluetoothSocket, mmDevice: BluetoothDevice) {
        Log.d(TAG, "connected: Started.")

        connectedThread = ConnectedThread(mmSocket)
        connectedThread?.start()
    }

    fun writeText(text: String) {
        val bytes = text.toByteArray(Charset.defaultCharset())
        write(bytes)
    }

    /**
     * Write to the ConnectedThread in an unsynchronized manner
     *
     * @param out The bytes to write
     * @see ConnectedThread#write(byte[])
     */
    fun write(out: ByteArray) {
        // Create temporary object
        var r: ConnectedThread

        // Synchronize a copy of the ConnectedThread
        Log.d(TAG, "write: Write Called.")

        //perform the write
        connectedThread?.write(out)
    }

    private fun processRawData(data: ByteArray) {
        for (i in data.indices) {
            when (val intVal = data[i].toInt()) {
                10 -> NL_active = true
                13 -> CR_active = true
                else -> {
                    val c = intVal.toChar()
                    tempBuffer += c
                }
            }

            if (NL_active && CR_active) {
                var serialValue = tempBuffer.toShortOrNull()
                tempBuffer = ""
                CR_active = false
                NL_active = false

                if (serialValue == null) continue

                serialValue = serialValue and 1023

                bluetoothCallbacks.onNewData(serialValue)
            }
        }
    }

    private fun onNewDataDiagnostic(data: ByteArray) {
        Log.d(TAG, "Data size: ${data.size}")
        Log.d(TAG, "Received data: ${data.toString(Charsets.US_ASCII)}")
    }

    private fun onNewDataDiagnostic(data: Byte) {
        Log.d(TAG, "Data size: 1")
        Log.d(TAG, "Received data: $data")
    }
}