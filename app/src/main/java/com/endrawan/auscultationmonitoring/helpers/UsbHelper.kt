package com.endrawan.auscultationmonitoring.helpers

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.endrawan.auscultationmonitoring.configs.Config.ACTION_USB_PERMISSION
import com.endrawan.auscultationmonitoring.configs.Config.BAUD_RATE
import com.endrawan.auscultationmonitoring.configs.Config.DATA_BITS
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager

class UsbHelper(val activity: AppCompatActivity) {

    private val TAG = "UsbHelper"
    var port: UsbSerialPort? = null
    lateinit var usbIoManager: SerialInputOutputManager

    fun connectToUSB(onNewData: SerialInputOutputManager.Listener): Boolean {
        val manager = activity.getSystemService(Context.USB_SERVICE) as UsbManager
        val availableDrivers: List<UsbSerialDriver> = UsbSerialProber.getDefaultProber()
            .findAllDrivers(manager)
        if (availableDrivers.isEmpty()) {
            Log.d(TAG, "No drivers detected!")
            toast("Driver/USB tidak ditemukan!")
            return false
        }

        val driver = availableDrivers[0]
        val device = driver.device
        val connection = manager.openDevice(driver.device)
        if (connection == null) {
            requestUsbPermission(manager, driver)
            return false
        } else {
            val information = "Device id: ${device.deviceId}\nDevice Name: ${device.deviceName}"
            Log.d(TAG, "Driver connected!")
            Log.d(TAG, information)
        }

        // Create a connection
        port = driver.ports[0]
        port?.open(connection)
        port?.setParameters(
            BAUD_RATE, DATA_BITS, UsbSerialPort.STOPBITS_1,
            UsbSerialPort.PARITY_NONE)

        // Trying to read from Arduino
        usbIoManager = SerialInputOutputManager(port, onNewData)
        return true
    }


    fun requestUsbPermission(manager: UsbManager, driver: UsbSerialDriver) {
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
                            toast("Permission tidak diberikan, tolong restart aplikasi!")
                        }

                    }
                }
            }
        }
        val permissionIntent =
            PendingIntent.getBroadcast(activity, 0, Intent(ACTION_USB_PERMISSION), 0)
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        activity.registerReceiver(usbReceiver, filter)
        manager.requestPermission(driver.device, permissionIntent)
    }

    private fun toast(text: String) {
        Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()
    }

}