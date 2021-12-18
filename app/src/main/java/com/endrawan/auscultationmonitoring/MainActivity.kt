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
import android.media.*
import android.widget.Toast
import com.endrawan.auscultationmonitoring.configs.Config
import com.endrawan.auscultationmonitoring.configs.Config.AUDIO_FREQUENCY
import com.endrawan.auscultationmonitoring.configs.Config.TEMP_FILENAME
import com.endrawan.auscultationmonitoring.configs.Config.WRITE_WAIT_MILLIS
import com.endrawan.auscultationmonitoring.helpers.AudioHelper
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.experimental.and


class MainActivity : AppCompatActivity(), SerialInputOutputManager.Listener{

    private lateinit var binding: ActivityMainBinding

    private var CR_active = false
    private var NL_active = false
    private var serialBuffer = ""

//    private val BAUD_RATE = 1000000 //250000
    private val DATA_BITS = 8
    private val TAG = "MainActivity"
    private val ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION"
    private lateinit var port: UsbSerialPort
    private lateinit var usbIoManager: SerialInputOutputManager

//    private val DATA_FREQUENCY = 4000 //100
    private val REFRESH_DATA_INTERVAL: Long = 200 // 0.2 Seconds
    private val CHART_X_RANGE_VISIBILITY = 2 * AUDIO_FREQUENCY //8000
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

    private var FILTER_OPTION = Config.OPTION_UNFILTERED
    private var AUSCULTATION_STATUS = Config.AUSCULTATION_IDLE
//    private var isIdle = true

    private lateinit var audioBuffer: ShortArray
    private var minBufferSize: Int = 0
    private lateinit var audioTrack: AudioTrack
    private lateinit var dataOutputStream: DataOutputStream
    private lateinit var tempFile: File
    private lateinit var audioHelper: AudioHelper


    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prepareToolbar()
        prepareFilterOption()
        prepareActionControl()
        prepareAudio()
//        prepareFile()
        prepareGraph()

//        usbConnection()
        lineDataSet.addEntry(Entry(lineDataSet.entryCount.toFloat(), 0f))
        audioBuffer[0] = 0
        Log.d(TAG, "Entry count now: ${lineDataSet.entryCount}")
//        addNewDataPeriodically()
    }

    override fun onDestroy() {
        super.onDestroy()
        port.close()
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
                FILTER_OPTION = Config.OPTION_HEART
                binding.linearLayoutFilterOptions.visibility = View.VISIBLE
                binding.imageViewHeartFilter.isEnabled = true
                binding.imageViewLungFilter.isEnabled = true
                handleFilterOptionUI(FILTER_OPTION)
            } else {
                FILTER_OPTION = Config.OPTION_UNFILTERED
                binding.linearLayoutFilterOptions.visibility = View.GONE
                binding.imageViewHeartFilter.isEnabled = false
                binding.imageViewLungFilter.isEnabled = false
            }

            if(AUSCULTATION_STATUS == Config.AUSCULTATION_RECORDING) sendFilterCommand()
        }

        binding.imageViewHeartFilter.setOnClickListener {
            FILTER_OPTION = Config.OPTION_HEART
            handleFilterOptionUI(FILTER_OPTION)
            if(AUSCULTATION_STATUS == Config.AUSCULTATION_RECORDING) sendFilterCommand()
        }

        binding.imageViewLungFilter.setOnClickListener {
            FILTER_OPTION = Config.OPTION_LUNG
            handleFilterOptionUI(FILTER_OPTION)
            if(AUSCULTATION_STATUS == Config.AUSCULTATION_RECORDING) sendFilterCommand()
        }
    }

    private fun handleFilterOptionUI(condition: Int) {
        if(condition == Config.OPTION_HEART) { // If heart filter
            binding.imageViewHeartFilter.filterSelected()
            binding.imageViewLungFilter.filterNormal()
        } else if(condition == Config.OPTION_LUNG) { // If lung filter
            binding.imageViewLungFilter.filterSelected()
            binding.imageViewHeartFilter.filterNormal()
        }
    }

    private fun prepareActionControl() {
        binding.imageViewRecord.setOnClickListener {
            if(AUSCULTATION_STATUS == Config.AUSCULTATION_IDLE || AUSCULTATION_STATUS == Config.AUSCULTATION_PAUSED) {
                if(!connectToUSB()) {
                    return@setOnClickListener
                }
                if(AUSCULTATION_STATUS == Config.AUSCULTATION_IDLE) {
                    prepareFile()
                }
                usbIoManager.start()
                sendFilterCommand()
                AUSCULTATION_STATUS = Config.AUSCULTATION_RECORDING
            } else if (AUSCULTATION_STATUS == Config.AUSCULTATION_RECORDING) {
                usbIoManager.stop()
                AUSCULTATION_STATUS = Config.AUSCULTATION_PAUSED
            }
            handleActionControlUI(AUSCULTATION_STATUS)
        }

        binding.imageViewStop.setOnClickListener {
            usbIoManager.stop()
            AUSCULTATION_STATUS = Config.AUSCULTATION_PAUSED
            handleActionControlUI(AUSCULTATION_STATUS)
            showSaveDialog()
        }

        handleActionControlUI(AUSCULTATION_STATUS)
    }

    private fun handleActionControlUI(condition: Int) {
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
                // TODO coba save
                val wavFileName = editText.text.toString() + ".wav"
                val wavFile = File(getExternalFilesDir(null), wavFileName)
                if(wavFile.exists()) {
                    toast("Terdapat file dengan nama yang sama!")
                    return@OnClickListener
                }
                wavFile.createNewFile()
                audioHelper.pcmToWav2(tempFile, wavFile)
                dataOutputStream.close()
                tempFile.delete()

                clearGraph()

                AUSCULTATION_STATUS = Config.AUSCULTATION_IDLE
                handleActionControlUI(AUSCULTATION_STATUS)

                startActivity(Intent(this@MainActivity, ListActivity::class.java))
            })
            setNeutralButton("Kembali", DialogInterface.OnClickListener { dialog, which ->

            })
            setNegativeButton("Jangan", DialogInterface.OnClickListener { dialog, which ->
                AUSCULTATION_STATUS = Config.AUSCULTATION_IDLE
                handleActionControlUI(AUSCULTATION_STATUS)
                dataOutputStream.close()
                tempFile.delete()
                clearGraph()
            })
            create()
        }
        saveDialog.show()
    }

    private fun connectToUSB(): Boolean {
        val manager = getSystemService(Context.USB_SERVICE) as UsbManager
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
        port.open(connection)
        port.setParameters(Config.BAUD_RATE, DATA_BITS, UsbSerialPort.STOPBITS_1,
            UsbSerialPort.PARITY_NONE)

        // Trying to read from Arduino
        usbIoManager = SerialInputOutputManager(port, this)
//        usbIoManager.start()
        return true
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
                            toast("Permission tidak diberikan, tolong restart aplikasi!")
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

    private fun sendFilterCommand() {
        when(FILTER_OPTION) {
            Config.OPTION_UNFILTERED -> {
                port.write(Config.COMMAND_FILTER_UNFILTERED, WRITE_WAIT_MILLIS)
            }
            Config.OPTION_HEART -> {
                port.write(Config.COMMAND_FILTER_HEART, WRITE_WAIT_MILLIS)
            }
            Config.OPTION_LUNG -> {
                port.write(Config.COMMAND_FILTER_LUNG, WRITE_WAIT_MILLIS)
            }
        }
    }

    override fun onNewData(data: ByteArray?) {
        for (i in data!!.indices) {
            when (val intVal = data[i].toInt()) {
                10 -> NL_active = true
                13 -> CR_active = true
                else -> {
                    val c = intVal.toChar()
                    serialBuffer += c
                }
            }

            if(NL_active && CR_active) {
                val serialValue = serialBuffer.toShortOrNull()
                serialBuffer = ""
                CR_active = false
                NL_active = false

                if(serialValue == null) continue

                val n = lineDataSet.entryCount
                lineDataSet.addEntry(Entry(n.toFloat(), serialValue.toFloat()))

                val idx = n % minBufferSize
                audioBuffer[idx] = convertToAudioSample(serialValue)
                if(idx == minBufferSize - 1) {
                    streamAudio(audioBuffer)
                    writeAudio(audioBuffer)
                }
            }
        }
    }

    private fun onNewDataDiagnostic(data: ByteArray?) {
        Log.d(TAG, "Data size: ${data!!.size}")
        Log.d(TAG, "Received data: ${data.toString(Charsets.US_ASCII)}")
    }

    override fun onRunError(e: Exception?) {
        Log.d(TAG, "Exception found: ${e?.message}")
    }

    private fun prepareAudio() {
        minBufferSize = AudioRecord.getMinBufferSize(
            AUDIO_FREQUENCY,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT)

        Log.d(TAG, "Min Buffer Size: $minBufferSize")

        audioBuffer = ShortArray(minBufferSize)

        audioTrack = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(AUDIO_FREQUENCY)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
            minBufferSize,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE)
        audioTrack.playbackRate = AUDIO_FREQUENCY
        audioTrack.play()
        audioHelper = AudioHelper(AudioFormat.CHANNEL_OUT_MONO, AUDIO_FREQUENCY, minBufferSize)
    }

    private fun streamAudio(audioData: ShortArray) {
        val streamAudioHandler = Handler(Looper.getMainLooper())
        val streamAudioCode = Runnable {
            audioTrack.write(audioData, 0, minBufferSize)
        }
        streamAudioHandler.post(streamAudioCode)
    }

    private fun writeAudio(audioData: ShortArray) {
        val writeAudioHandler = Handler(Looper.getMainLooper())
        val writeAudioCode = Runnable {
            for(a in audioData) {
                val littleEndian = ByteArray(2)
                littleEndian[0] = (a.toInt() and 0xFF).toByte()
                littleEndian[1] = ((a.toInt() shr 8) and 0xFF).toByte()
                dataOutputStream.write(littleEndian)
            }
        }
        writeAudioHandler.post(writeAudioCode)
    }

    private fun prepareGraph() {
        styleLineDataSet()
        binding.chart.xAxis.granularity = AUDIO_FREQUENCY.toFloat()
        binding.chart.xAxis.valueFormatter = object: ValueFormatter() {
            override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                val rawSeconds = (value / AUDIO_FREQUENCY).toInt()
                val seconds = rawSeconds % 60
                val minutes = rawSeconds / 60
                return String.format("%d:%02d", minutes, seconds)
            }
        }
        binding.chart.axisLeft.valueFormatter = object: ValueFormatter() {
            override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                val voltageVal: Float = value * ADC_VOLTAGE_REF.toFloat() / MAX_ADC_RESOLUTION.toFloat()
                return String.format("%.2f", voltageVal)
            }
        }
        binding.chart.data = lineData
        binding.chart.invalidate()
        refreshGraphPeriodically()
    }

    private fun clearGraph() {
        lineDataSet.clear()
        binding.chart.invalidate()
//        binding.chart.clear()
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

    private fun prepareFile() {
        tempFile = File(getExternalFilesDir(null), TEMP_FILENAME)
        if (tempFile.exists()) {
            tempFile.delete()
        }
        tempFile.createNewFile()
        dataOutputStream = DataOutputStream(BufferedOutputStream(FileOutputStream(tempFile)))
    }

    private fun mapToOutput(input: Float): Float {
        return (input - IN_MIN) * (OUT_MAX - OUT_MIN) / (IN_MAX - IN_MIN) + OUT_MIN
    }

    private fun convertToAudioSample(raw: Short): Short {
        val pcm16bit = (raw - 512) shl 6
        return pcm16bit.toShort()
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }
}