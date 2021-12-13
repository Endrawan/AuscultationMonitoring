package com.endrawan.auscultationmonitoring

import android.app.PendingIntent
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
import android.R.color.holo_orange_light
import android.app.AlertDialog
import android.content.*
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText

import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.endrawan.auscultationmonitoring.extensions.filterNormal
import com.endrawan.auscultationmonitoring.extensions.filterSelected
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.formatter.ValueFormatter
import android.content.DialogInterface
import com.endrawan.auscultationmonitoring.configs.Config


class MainActivity : AppCompatActivity(), SerialInputOutputManager.Listener{

    private lateinit var binding: ActivityMainBinding

    private var CR_active = false
    private var NL_active = false
    private var serialBuffer = ""

    private val BAUD_RATE = 1000000 //250000
    private val DATA_BITS = 8
    private val TAG = "MainActivity"
    private val ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION"
    private lateinit var port: UsbSerialPort

    private val DATA_FREQUENCY = 4000 //100
    private val REFRESH_DATA_INTERVAL: Long = 200 // 0.2 Seconds
    private val CHART_X_RANGE_VISIBILITY = 2 * DATA_FREQUENCY //8000
    private val CHART_Y_RANGE_VISIBILITY = 4f //1024f
    private val MAX_ADC_RESOLUTION = 1023u
    private val ADC_VOLTAGE_REF = 5u

    private val ADD_DATA_INTERVAL : Long = 10 // ms

    private val IN_MIN = -1
    private val IN_MAX = 1
    private val OUT_MIN = 0
    private val OUT_MAX = 4

    private val lineDataSet = LineDataSet(ArrayList<Entry>(), "Recorded Data")
    private val lineData = LineData(lineDataSet)

    private var FILTER_OPTION = false // false == heart filter; true == lung filter
    private var AUSCULTATION_STATUS = Config.AUSCULTATION_IDLE


    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prepareToolbar()
        prepareFilterOption()
        prepareActionControl()
        prepareGraph()

//        usbConnection()
//        lineDataSet.addEntry(Entry(lineDataSet.entryCount.toFloat(), 0f))
//        addNewDataPeriodically()
    }

    override fun onDestroy() {
        super.onDestroy()
//        port.close()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            R.id.list_item -> {
                startActivity(Intent(this, ListActivity::class.java))
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun prepareToolbar() {
        setSupportActionBar(binding.toolbar)
    }

    private fun prepareFilterOption() {
        binding.checkboxFilter.setOnCheckedChangeListener { _, isChecked ->
            if(isChecked) {
                binding.linearLayoutFilterOptions.visibility = View.VISIBLE
                binding.imageViewHeartFilter.isEnabled = true
                binding.imageViewLungFilter.isEnabled = true
                handleFilterOption(FILTER_OPTION)
            } else {
                binding.linearLayoutFilterOptions.visibility = View.GONE
                binding.imageViewHeartFilter.isEnabled = false
                binding.imageViewLungFilter.isEnabled = false
            }
        }

        binding.imageViewHeartFilter.setOnClickListener {
            FILTER_OPTION = false
            handleFilterOption(FILTER_OPTION)
        }

        binding.imageViewLungFilter.setOnClickListener {
            FILTER_OPTION = true
            handleFilterOption(FILTER_OPTION)
        }
    }

    private fun handleFilterOption(condition: Boolean) {
        if(!condition) { // If heart filter
            binding.imageViewHeartFilter.filterSelected()
            binding.imageViewLungFilter.filterNormal()
        } else { // If lung filter
            binding.imageViewLungFilter.filterSelected()
            binding.imageViewHeartFilter.filterNormal()
        }
    }

    private fun prepareActionControl() {
        binding.imageViewRecord.setOnClickListener {
            if(AUSCULTATION_STATUS == Config.AUSCULTATION_IDLE || AUSCULTATION_STATUS == Config.AUSCULTATION_PAUSED) {
                AUSCULTATION_STATUS = Config.AUSCULTATION_RECORDING
            } else if (AUSCULTATION_STATUS == Config.AUSCULTATION_RECORDING) {
                AUSCULTATION_STATUS = Config.AUSCULTATION_PAUSED
            }
            handleActionControl(AUSCULTATION_STATUS)
        }

        binding.imageViewStop.setOnClickListener {
            AUSCULTATION_STATUS = Config.AUSCULTATION_PAUSED
            handleActionControl(AUSCULTATION_STATUS)
            showSaveDialog()
        }

        handleActionControl(AUSCULTATION_STATUS)
    }

    private fun handleActionControl(condition: Int) {
        when(condition) {
            Config.AUSCULTATION_IDLE -> {
                binding.imageViewStop.visibility = View.INVISIBLE
                binding.imageViewRecord.setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.ic_record, null))
                binding.imageViewRecord.setColorFilter(ResourcesCompat.getColor(resources, R.color.red, null), android.graphics.PorterDuff.Mode.SRC_IN)
            }
            Config.AUSCULTATION_RECORDING -> {
                binding.imageViewRecord.setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.ic_pause_circle_line, null))
                binding.imageViewRecord.setColorFilter(ResourcesCompat.getColor(resources, R.color.black, null), android.graphics.PorterDuff.Mode.SRC_IN)
                binding.imageViewStop.visibility = View.VISIBLE
            }
            Config.AUSCULTATION_PAUSED -> {
                binding.imageViewRecord.setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.ic_record, null))
                binding.imageViewRecord.setColorFilter(ResourcesCompat.getColor(resources, R.color.red, null), android.graphics.PorterDuff.Mode.SRC_IN)
                binding.imageViewStop.visibility = View.VISIBLE
            }
        }
    }

    private fun showSaveDialog() {
        val editText = EditText(this).apply {
            hint = "Nama audio anda.."
            width = 200
        }
        val saveDialog = AlertDialog.Builder(this)
        saveDialog.apply {
            setTitle("Simpan rekaman?")
            setView(editText)
            setPositiveButton("Simpan", DialogInterface.OnClickListener { dialog, which ->
                AUSCULTATION_STATUS = Config.AUSCULTATION_IDLE
                handleActionControl(AUSCULTATION_STATUS)
                // TODO store audio file

                startActivity(Intent(this@MainActivity, ListActivity::class.java))
            })
            setNeutralButton("Kembali", DialogInterface.OnClickListener { dialog, which ->

            })
            setNegativeButton("Jangan", DialogInterface.OnClickListener { dialog, which ->
                AUSCULTATION_STATUS = Config.AUSCULTATION_IDLE
                handleActionControl(AUSCULTATION_STATUS)
                // TODO clear audio buffer
            })
            create()
        }
        saveDialog.show()
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
//                in 48..57 -> {
//                    val c = intVal.toChar()
//                    serialBuffer += c
//                }
                10 -> { // New Line
                    NL_active = true
                }
                13 -> {
                    CR_active = true
                }
                else -> {
                    val c = intVal.toChar()
                    serialBuffer += c
                }
            }

            if(NL_active && CR_active) {
                val serialValue = serialBuffer.toFloatOrNull()
                serialBuffer = ""
                CR_active = false
                NL_active = false
                if(serialValue == null) continue
//                val limitValue = serialValue and MAX_ADC_RESOLUTION
//                val voltageVal: Float = limitValue.toFloat() * ADC_VOLTAGE_REF.toFloat() / MAX_ADC_RESOLUTION.toFloat()
//                lineDataSet.addEntry(Entry(lineDataSet.entryCount.toFloat(), voltageVal))
                lineDataSet.addEntry(Entry(lineDataSet.entryCount.toFloat(), serialValue))
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
        binding.chart.axisLeft.valueFormatter = object: ValueFormatter() {
            override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                return mapToOutput(value).toString()
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

    private fun mapToOutput(input: Float): Float {
        return (input - IN_MIN) * (OUT_MAX - OUT_MIN) / (IN_MAX - IN_MIN) + OUT_MIN
    }
}