package com.endrawan.auscultationmonitoring

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.lang.Exception

class MainActivity : AppCompatActivity(), SerialInputOutputManager.Listener{

    private val BAUD_RATE = 9600
    private val DATA_BITS = 8
    private val TAG = "MainActivity"
    private val ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION"
    private lateinit var port: UsbSerialPort

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        usbConnection()
    }

    private fun usbConnection() {
        val manager = getSystemService(Context.USB_SERVICE) as UsbManager
        val availableDrivers: List<UsbSerialDriver> = UsbSerialProber.getDefaultProber()
            .findAllDrivers(manager)
        if (availableDrivers.isEmpty()) {
            Log.d(TAG, "No drivers detected!")
            return
        }

        val driver = availableDrivers[0]
        val connection = manager.openDevice(driver.device)
        if (connection == null) {
            requestUsbPermission(manager, driver)
            return
        } else {
            Log.d(TAG, "Driver connected!")
        }

        // Create a connection
        port = driver.ports[0]
        port.open(connection)
        port.setParameters(BAUD_RATE, DATA_BITS, UsbSerialPort.STOPBITS_1,
            UsbSerialPort.PARITY_NONE)

        // Trying to read from Arduino
        val usbIoManager = SerialInputOutputManager(port, this)
        usbIoManager.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        port.close()
    }

    private fun requestUsbPermission(manager: UsbManager, driver: UsbSerialDriver) {
        val usbReceiver = object : BroadcastReceiver() {
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
        val permissionIntent =
            PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), 0)
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        registerReceiver(usbReceiver, filter)
        manager.requestPermission(driver.device, permissionIntent)
    }

    override fun onNewData(data: ByteArray?) {
        val dataString = String(data!!)
        Log.d(TAG, "Data received: $dataString")
    }

    override fun onRunError(e: Exception?) {
        Log.d(TAG, "Exception found: ${e?.message}")
    }
}