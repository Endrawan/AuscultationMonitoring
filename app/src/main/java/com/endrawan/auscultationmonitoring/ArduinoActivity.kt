package com.endrawan.auscultationmonitoring

import android.R.attr
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.lang.Exception
import android.R.attr.data




class ArduinoActivity : AppCompatActivity(), SerialInputOutputManager.Listener {

    private val TAG = "ArduinoActivity"
    private val ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION"
    private lateinit var port: UsbSerialPort

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_USB_PERMISSION == intent.action) {
                synchronized(this) {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        device?.apply {
                            //call method to set up device communication
                            Log.d(TAG, "permission granted for device $device")
                        }
                    } else {
                        Log.d(TAG, "permission denied for device $device")
                    }

                }
            }
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_arduino)

        // Find all available drivers from attached devices/
        val manager = getSystemService(Context.USB_SERVICE) as UsbManager

        val permissionIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), 0)
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        registerReceiver(usbReceiver, filter)

        val availableDrivers: List<UsbSerialDriver> = UsbSerialProber.getDefaultProber()
            .findAllDrivers(manager)
        if (availableDrivers.isEmpty()) {
            Log.d(TAG, "No drivers detected!")
            return
        } else {
            Log.d(TAG, "Available drivers:\n $availableDrivers")
        }

        // Open a connection to the first available driver
        val driver = availableDrivers[0]
        val device = driver.device
        val connection = manager.openDevice(driver.device)
        if (connection == null) {
            manager.requestPermission(driver.device, permissionIntent)
            return
        } else {
            Log.d(TAG, "Driver connected!")
        }

        // Create a connection
        port = driver.ports[0]
        port.open(connection)
        port.setParameters(9600, 8, UsbSerialPort.STOPBITS_1,
            UsbSerialPort.PARITY_NONE)

        // Trying to read from Arduino
        val usbIoManager = SerialInputOutputManager(port, this)
        usbIoManager.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        port.close()
    }

    override fun onNewData(data: ByteArray?) {
//        runOnUiThread { textView.append(String(attr.data)) }
        val dataString = String(data!!)
        Log.d(TAG, "Data received: $dataString")
    }

    override fun onRunError(e: Exception?) {
        Log.d(TAG, "Exception found: ${e?.message}")
    }


}