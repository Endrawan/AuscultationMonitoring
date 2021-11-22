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
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.endrawan.auscultationmonitoring.databinding.ActivityMainBinding
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.lang.Exception
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.experimental.and
import kotlin.experimental.or
import android.R.color.holo_orange_light

import androidx.core.content.ContextCompat
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.formatter.ValueFormatter


class MainActivity : AppCompatActivity(), SerialInputOutputManager.Listener{

    private lateinit var binding: ActivityMainBinding

    private var CR_active = false
    private var NL_active = false
    private var serialBuffer = ""

    private val BAUD_RATE = 250000
    private val DATA_BITS = 8
    private val TAG = "MainActivity"
    private val ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION"
    private lateinit var port: UsbSerialPort

    private val DATA_FREQUENCY = 4000 //100
    private val REFRESH_DATA_INTERVAL: Long = 200 // 0.2 Seconds
    private val CHART_X_RANGE_VISIBILITY = 2 * DATA_FREQUENCY //8000
    private val CHART_Y_RANGE_VISIBILITY = 5f //1024f
    private val MAX_ADC_RESOLUTION = 1023u
    private val ADC_VOLTAGE_REF = 5u

    private val ADD_DATA_INTERVAL : Long = 10 // ms

    private val lineDataSet = LineDataSet(ArrayList<Entry>(), "Recorded Data")
    private val lineData = LineData(lineDataSet)


    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prepareGraph()
        usbConnection()
//        lineDataSet.addEntry(Entry(lineDataSet.entryCount.toFloat(), 0f))
//        addNewDataPeriodically()
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
        for (i in data!!.indices) {
            when (val intVal = data[i].toInt()) {
                in 48..57 -> {
                    val c = intVal.toChar()
                    serialBuffer += c
                }
                10 -> { // New Line
                    NL_active = true
                }
                13 -> {
                    CR_active = true
                }
            }

            if(NL_active && CR_active) {
                val serialValue = serialBuffer.toUIntOrNull()
                serialBuffer = ""
                CR_active = false
                NL_active = false
                if(serialValue == null) continue
                val limitValue = serialValue and MAX_ADC_RESOLUTION
                val voltageVal: Float = limitValue.toFloat() * ADC_VOLTAGE_REF.toFloat() / MAX_ADC_RESOLUTION.toFloat()
                lineDataSet.addEntry(Entry(lineDataSet.entryCount.toFloat(), voltageVal))
            }
        }
    }

    private fun onNewDataDiagnostic(data: ByteArray?) {
        Log.d(TAG, "Data size: ${data!!.size}")
        for (i in data.indices) {
            Log.d(TAG, "data[$i]: ${data[i].toUByte()}")
        }
        val realValue = (data[0].toUByte() * 256u) + data[1].toUByte()
        Log.d(TAG, "Data value: $realValue")
    }

    override fun onRunError(e: Exception?) {
        Log.d(TAG, "Exception found: ${e?.message}")
    }

    private fun prepareGraph() {
        styleLineDataSet()
        binding.chart.xAxis.granularity = DATA_FREQUENCY.toFloat()
        binding.chart.xAxis.valueFormatter = object: ValueFormatter() {
            override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                val rawSeconds = (value / DATA_FREQUENCY).toInt()
                val seconds = rawSeconds % 60
                val minutes = rawSeconds / 60
                return String.format("%d:%02d", minutes, seconds)
            }
        }
        binding.chart.data = lineData
        binding.chart.invalidate()
        refreshGraphPeriodically()
    }

    private fun styleLineDataSet() {
        val color = ContextCompat.getColor(this, holo_orange_light)
        lineDataSet.setDrawValues(false)
        lineDataSet.setDrawCircles(false)
        lineDataSet.color = color
    }

    private fun addNewDataPeriodically() {
        val addDataHandler = Handler(Looper.getMainLooper())
        val addDataCode = object: Runnable {
            override fun run() {
                lineDataSet.addEntry(Entry(lineDataSet.entryCount.toFloat(), (0..1023).random().toFloat()))
                addDataHandler.postDelayed(this, ADD_DATA_INTERVAL)
            }
        }
        addDataHandler.post(addDataCode)
    }

    private fun refreshGraphPeriodically() {
        val refreshDataHandler = Handler(Looper.getMainLooper())
        val refreshDataCode = object: Runnable {
            override fun run() {
                lineData.notifyDataChanged()
                binding.chart.notifyDataSetChanged()
                binding.chart.setVisibleXRangeMaximum(CHART_X_RANGE_VISIBILITY.toFloat())
                binding.chart.moveViewTo(
                    lineDataSet.entryCount - CHART_X_RANGE_VISIBILITY - 1f,
                    CHART_Y_RANGE_VISIBILITY,
                    YAxis.AxisDependency.LEFT)

                refreshDataHandler.postDelayed(this, REFRESH_DATA_INTERVAL)
            }
        }
        refreshDataHandler.post(refreshDataCode)
    }
}