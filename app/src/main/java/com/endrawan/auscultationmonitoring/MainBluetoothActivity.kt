package com.endrawan.auscultationmonitoring

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.graphics.Color
import android.media.*
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Menu
import android.view.View
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.endrawan.auscultationmonitoring.bluetooth.BluetoothConnectionService
import com.endrawan.auscultationmonitoring.bluetooth.BluetoothStatus
import com.endrawan.auscultationmonitoring.callbacks.BluetoothCallbacks
import com.endrawan.auscultationmonitoring.configs.Config
import com.endrawan.auscultationmonitoring.databinding.ActivityMainBluetoothBinding
import com.endrawan.auscultationmonitoring.extensions.filterNormal
import com.endrawan.auscultationmonitoring.extensions.filterSelected
import com.endrawan.auscultationmonitoring.helpers.AudioHelper
import com.endrawan.auscultationmonitoring.helpers.CounterHelper
import com.endrawan.auscultationmonitoring.helpers.ParameterHelper
import com.endrawan.auscultationmonitoring.helpers.UsbHelper
import com.endrawan.auscultationmonitoring.models.Sample
import com.endrawan.auscultationmonitoring.utils.SmoothedZScore
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.*
import java.util.regex.Pattern
import kotlin.collections.ArrayList
import kotlin.math.log

class MainBluetoothActivity : AppCompatActivity(), BluetoothCallbacks {

    private val TAG = "MainBluetoothActivity"
    private lateinit var binding: ActivityMainBluetoothBinding

    private var bluetoothStatus = BluetoothStatus.Ready
    private var bluetoothAdapter: BluetoothAdapter? = null
    private lateinit var bluetoothDevice: BluetoothDevice
    private lateinit var bluetoothConnection: BluetoothConnectionService
    private val uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")


    private val REFRESH_DATA_INTERVAL: Long = 200 // 0.2 Seconds
    private val REFRESH_PARAMETER_VIEW_INTERVAL: Long = 1000 // 1 Seconds
    private val CHART_X_RANGE_VISIBILITY = 2 * Config.AUDIO_FREQUENCY //8000
    private val CHART_Y_RANGE_VISIBILITY = 4f //1024f
    private val MAX_ADC_RESOLUTION = 1023u
    private val ADC_VOLTAGE_REF = 5u

    private val signalDataSet = LineDataSet(ArrayList<Entry>(), "Recorded Data")
    private val lineData = LineData(listOf(signalDataSet))

    private var FILTER_OPTION = Config.OPTION_UNFILTERED
    private var AUSCULTATION_STATUS = Config.AUSCULTATION_IDLE

    private lateinit var audioBuffer: ShortArray
    private var minBufferSize: Int = 0
    private lateinit var audioTrack: AudioTrack
    private lateinit var audioHelper: AudioHelper

    private lateinit var tempAudioStream: DataOutputStream
    private lateinit var tempAudioFile: File

    private val heartZScore =
        SmoothedZScore(
            Config.HEART_Z_SCORE_LAG,
            Config.HEART_Z_SCORE_THRESHOLD.toDouble(),
            Config.HEART_Z_SCORE_INFLUENCE
        )
    private val lungZScore =
        SmoothedZScore(Config.LUNG_Z_SCORE_LAG, Config.LUNG_Z_SCORE_THRESHOLD.toDouble(), Config.LUNG_Z_SCORE_INFLUENCE)
    private val heartCounterHelper = CounterHelper(Config.AUDIO_FREQUENCY, CounterHelper.HEART_TYPE)
    private val lungCounterHelper = CounterHelper(Config.AUDIO_FREQUENCY, CounterHelper.LUNG_TYPE)

    private val parameterHelper = ParameterHelper(heartZScore, lungZScore,
        heartCounterHelper, lungCounterHelper)

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBluetoothBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prepareToolbar()
        prepareFilterOption()
        prepareActionControl()
        prepareBluetooth()
        prepareAudio()
        prepareGraph()
        prepareDataset()
    }

    override fun onStart() {
        super.onStart()

        if (bluetoothAdapter == null) {
            return
        }

        // If BT is not on, request that it be enabled
        val bluetoothAdapter = bluetoothAdapter!!
        if (!bluetoothAdapter.isEnabled) {
            bluetoothEnableHandler()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onDestroy() {
        stopAction()
        bluetoothConnection.stop()
        bluetoothStatus = BluetoothStatus.Ready
        handleBluetoothControlUI(bluetoothStatus)
        super.onDestroy()
    }

    @SuppressLint("MissingPermission")
    override fun onDeviceConnected() {
        bluetoothStatus = BluetoothStatus.Connected
        runOnUiThread {
            handleBluetoothControlUI(bluetoothStatus)
        }
        prepareFiles()
    }

    override fun onNewData(incomingData: Short) {
//        Log.d(TAG, "onNewData: $incomingData")
        val n = signalDataSet.entryCount
        signalDataSet.addEntry(Entry(n.toFloat(), incomingData.toFloat()))

        val idx = n % minBufferSize
        audioBuffer[idx] = convertToAudioSample(incomingData)
        if (idx == minBufferSize - 1) {
            audioHelper.streamAudio(audioBuffer, audioTrack)
            audioHelper.writeAudio(audioBuffer, tempAudioStream)
        }
        parameterHelper.findParameter(Sample(n, incomingData.toDouble()), FILTER_OPTION)
    }

    private fun prepareToolbar() {
        setSupportActionBar(binding.toolbar)
        setStatus("Not Connected", "Sambung stetoskop")
    }

    private fun prepareFilterOption() {
        binding.checkboxFilter.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
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

            if (AUSCULTATION_STATUS == Config.AUSCULTATION_RECORDING) sendFilterCommand()
        }

        binding.imageViewHeartFilter.setOnClickListener {
            FILTER_OPTION = Config.OPTION_HEART
            handleFilterOptionUI(FILTER_OPTION)
            parameterHelper.reset()
            if (AUSCULTATION_STATUS == Config.AUSCULTATION_RECORDING) sendFilterCommand()
        }

        binding.imageViewLungFilter.setOnClickListener {
            FILTER_OPTION = Config.OPTION_LUNG
            handleFilterOptionUI(FILTER_OPTION)
            parameterHelper.reset()
            if (AUSCULTATION_STATUS == Config.AUSCULTATION_RECORDING) sendFilterCommand()
        }
    }

    // TODO: Starts listening to bluetooth stream
    private fun prepareActionControl() {
        binding.imageViewRecord.setOnClickListener {
            if(bluetoothStatus == BluetoothStatus.Connecting || bluetoothStatus == BluetoothStatus.Ready) {
                toast("Tolong sambungkan stetoskop untuk mulai merekam")
                return@setOnClickListener
            }

            if (AUSCULTATION_STATUS == Config.AUSCULTATION_IDLE || AUSCULTATION_STATUS == Config.AUSCULTATION_PAUSED) {
//                if (!usbHelper.connectToUSB(this)) {
//                    return@setOnClickListener
//                }

                if (AUSCULTATION_STATUS == Config.AUSCULTATION_IDLE) {
                    prepareFiles()
                }

//                usbHelper.usbIoManager.start()
                bluetoothConnection.startListening()
                sendFilterCommand()
                AUSCULTATION_STATUS = Config.AUSCULTATION_RECORDING
            } else if (AUSCULTATION_STATUS == Config.AUSCULTATION_RECORDING) {
//                usbHelper.usbIoManager.stop()
                bluetoothConnection.stopListening()
                AUSCULTATION_STATUS = Config.AUSCULTATION_PAUSED
            }
            handleActionControlUI(AUSCULTATION_STATUS)
        }

        binding.imageViewStop.setOnClickListener {
//            usbHelper.usbIoManager.stop()
            bluetoothConnection.stopListening()
            AUSCULTATION_STATUS = Config.AUSCULTATION_PAUSED
            handleActionControlUI(AUSCULTATION_STATUS)
            stopAction()
        }

        handleActionControlUI(AUSCULTATION_STATUS)
    }

    private fun prepareBluetooth() {
        // Get the local Bluetooth adapter
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        // If the adapter is null, then Bluetooth is not supported
        if (bluetoothAdapter == null) {
            toast("Bluetooth tidak terdeteksi pada perangkat anda")
            finish()
        }

        bluetoothConnection = BluetoothConnectionService(bluetoothAdapter!!, this)

        handleBluetoothControlUI(bluetoothStatus)
        binding.buttonBluetoothConnect.setOnClickListener {
            when(bluetoothStatus) {
                BluetoothStatus.Ready -> {
                    showBluetoothDevicesList()
                }
                BluetoothStatus.Connecting -> {
                    // TODO: add connecting action
                }
                BluetoothStatus.Connected -> {
                    stopAction()
                    bluetoothConnection.stop()
                    bluetoothStatus = BluetoothStatus.Ready
                    handleBluetoothControlUI(bluetoothStatus)
                }
            }
        }
    }

    private fun prepareAudio() {
        minBufferSize = AudioRecord.getMinBufferSize(
            Config.AUDIO_FREQUENCY,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioBuffer = ShortArray(minBufferSize)

        audioTrack = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(Config.AUDIO_FREQUENCY)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
            minBufferSize,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        audioTrack.playbackRate = Config.AUDIO_FREQUENCY
        audioTrack.play()
        audioHelper = AudioHelper(AudioFormat.CHANNEL_OUT_MONO, Config.AUDIO_FREQUENCY, minBufferSize)
    }

    private fun prepareGraph() {
        styleLineDataSet()
        binding.chart.xAxis.granularity = Config.AUDIO_FREQUENCY.toFloat()
        binding.chart.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                val rawSeconds = (value / Config.AUDIO_FREQUENCY).toInt()
                val seconds = rawSeconds % 60
                val minutes = rawSeconds / 60
                return String.format("%d:%02d", minutes, seconds)
            }
        }
        binding.chart.axisLeft.valueFormatter = object : ValueFormatter() {
            override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                val voltageVal: Float =
                    value * ADC_VOLTAGE_REF.toFloat() / MAX_ADC_RESOLUTION.toFloat()
                return String.format("%.2f", voltageVal)
            }
        }
        binding.chart.data = lineData
        binding.chart.invalidate()
        refreshGraphPeriodically()
        refreshParameterPeriodically()
    }

    private fun prepareDataset() {
        val firstIndex = signalDataSet.entryCount
        signalDataSet.addEntry(Entry(firstIndex.toFloat(), 0f))
        audioBuffer[0] = 0
        parameterHelper.findParameter(Sample(0, 0.0), FILTER_OPTION)
    }


    private fun handleFilterOptionUI(condition: Int) {
        if (condition == Config.OPTION_HEART) { // If heart filter
            binding.imageViewHeartFilter.filterSelected()
            binding.imageViewLungFilter.filterNormal()
        } else if (condition == Config.OPTION_LUNG) { // If lung filter
            binding.imageViewLungFilter.filterSelected()
            binding.imageViewHeartFilter.filterNormal()
        }
    }

    // TODO: Adds send command by bluetooth
    private fun sendFilterCommand() {
        when (FILTER_OPTION) {
            Config.OPTION_UNFILTERED -> {
                Log.d(TAG, "sendFilterCommand: option unfiltered")
                bluetoothConnection.write(Config.COMMAND_FILTER_UNFILTERED)
//                usbHelper.port?.write(Config.COMMAND_FILTER_UNFILTERED, Config.WRITE_WAIT_MILLIS)
            }
            Config.OPTION_HEART -> {
                Log.d(TAG, "sendFilterCommand: option heart")
                bluetoothConnection.write(Config.COMMAND_FILTER_HEART)
//                usbHelper.port?.write(Config.COMMAND_FILTER_HEART, Config.WRITE_WAIT_MILLIS)
            }
            Config.OPTION_LUNG -> {
                Log.d(TAG, "sendFilterCommand: option lung")
                bluetoothConnection.write(Config.COMMAND_FILTER_LUNG)
//                usbHelper.port?.write(Config.COMMAND_FILTER_LUNG, Config.WRITE_WAIT_MILLIS)
            }
        }
    }

    private fun handleActionControlUI(condition: Int) {
        when (condition) {
            Config.AUSCULTATION_IDLE -> {
                binding.imageViewStop.visibility = View.INVISIBLE
                binding.imageViewRecord.setImageDrawable(
                    ResourcesCompat.getDrawable(
                        resources,
                        R.drawable.ic_record,
                        null
                    )
                )
                binding.imageViewRecord.setColorFilter(
                    ResourcesCompat.getColor(
                        resources,
                        R.color.red,
                        null
                    ), android.graphics.PorterDuff.Mode.SRC_IN
                )
            }
            Config.AUSCULTATION_RECORDING -> {
                binding.imageViewRecord.setImageDrawable(
                    ResourcesCompat.getDrawable(
                        resources,
                        R.drawable.ic_pause_circle_line,
                        null
                    )
                )
                binding.imageViewRecord.setColorFilter(
                    ResourcesCompat.getColor(
                        resources,
                        R.color.black,
                        null
                    ), android.graphics.PorterDuff.Mode.SRC_IN
                )
                binding.imageViewStop.visibility = View.VISIBLE
            }
            Config.AUSCULTATION_PAUSED -> {
                binding.imageViewRecord.setImageDrawable(
                    ResourcesCompat.getDrawable(
                        resources,
                        R.drawable.ic_record,
                        null
                    )
                )
                binding.imageViewRecord.setColorFilter(
                    ResourcesCompat.getColor(
                        resources,
                        R.color.red,
                        null
                    ), android.graphics.PorterDuff.Mode.SRC_IN
                )
                binding.imageViewStop.visibility = View.VISIBLE
            }
        }
    }

    /**
     * Bluetooth Section
     */

    private fun handleBluetoothControlUI(bluetoothStatus: BluetoothStatus) {
        when(bluetoothStatus) {
            BluetoothStatus.Ready -> {
                setStatus("Tidak terkoneksi", "Sambung stetoskop")
            }
            BluetoothStatus.Connecting -> {
                setStatus("Menyambungkan...", "Menyambungkan...")
            }
            BluetoothStatus.Connected -> {
                setStatus("Terkoneksi", "Memutuskan")
            }
        }
    }

    private fun bluetoothEnableHandler() {
        // Handle permission only on android device above S version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            askBluetoothPermissions()
        } else {
            askEnableBluetooth()
        }
    }

    private val bluetoothPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var granted = true
            permissions.entries.forEach {
                Log.d(TAG, "${it.key} = ${it.value}")
                granted = granted and it.value
            }

            if (granted) {
                askEnableBluetooth()
            } else {
                AlertDialog.Builder(this@MainBluetoothActivity)
                    .setCancelable(false)
                    .setMessage("Bluetooth permission diperlukan.\nSilahkan coba lagi!")
                    .setPositiveButton(
                        "Berikan"
                    ) { _, _ -> bluetoothEnableHandler() }
                    .setNegativeButton(
                        "Jangan"
                    ) { _, _ -> finish() }
                    .create()
                    .show()
            }
        }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun askBluetoothPermissions() {
        bluetoothPermissionsLauncher.launch(
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        )
    }

    private val enableBluetoothLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                toast("Bluetooth activated!")
//                showBluetoothDevicesList()
            } else {
                AlertDialog.Builder(this)
                    .setCancelable(false)
                    .setMessage("Bluetooth perlu dinyalakan.\nSilahkan nyalakan!")
                    .setPositiveButton(
                        "Nyalakan"
                    ) { _, _ ->
                        askEnableBluetooth()
                    }
                    .setNegativeButton(
                        "Jangan"
                    ) { _, _ -> finish() }
                    .create()
                    .show()
            }
        }

    private fun askEnableBluetooth() {
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        enableBluetoothLauncher.launch(enableBtIntent)
    }

    @SuppressLint("MissingPermission")
    private fun showBluetoothDevicesList() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            showCompanionDevicePairing()
        } else {
            showLegacyBluetoothDevices()
        }
    }

    @SuppressLint("MissingPermission")
    private fun showLegacyBluetoothDevices() {
        val devices = mutableListOf<BluetoothDevice>()
        val bluetoothAdapter = bluetoothAdapter!!
        for (device in bluetoothAdapter.bondedDevices)
            if (device.type != BluetoothDevice.DEVICE_TYPE_LE && device.name.startsWith("ESP32")) {
                devices.add(device)
            }

        val devicesName = devices.map { it.name }.toTypedArray()

        // setup the alert builder
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Pilih device stetoskop:")
        builder.setCancelable(false)

        builder.setItems(devicesName) { _, which ->
//            toast("Choosen device: ${devicesName[which]}")
            bluetoothDevice = devices[which]
            startBTConnection(devices[which], uuid)
        }

        // create and show the alert dialog
        val dialog = builder.create()
        dialog.show()
    }

    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.O)
    private val companionDeviceLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val deviceToPair: BluetoothDevice? =
                    result.data?.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE)

                if(deviceToPair == null) {
                    toast("Tolong untuk memilih device stetoskop!")
                    return@registerForActivityResult
                }

                bluetoothStatus = BluetoothStatus.Connecting
                bluetoothDevice = deviceToPair
                handleBluetoothControlUI(bluetoothStatus)
                startBTConnection(deviceToPair, uuid)
            }
        }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun showCompanionDevicePairing() {
        val deviceFilter: BluetoothDeviceFilter = BluetoothDeviceFilter.Builder()
            .setNamePattern(Pattern.compile("ESP32"))
            .build()

        val pairingRequest: AssociationRequest = AssociationRequest.Builder()
            .addDeviceFilter(deviceFilter)
            .build()

        val deviceManager = getSystemService(Context.COMPANION_DEVICE_SERVICE) as CompanionDeviceManager

        deviceManager.associate(pairingRequest,
            object : CompanionDeviceManager.Callback() {
                override fun onDeviceFound(chooserLauncher: IntentSender) {
                    val intentSenderRequest = IntentSenderRequest.Builder(chooserLauncher).build()
                    companionDeviceLauncher.launch(intentSenderRequest)
                }

                override fun onFailure(error: CharSequence?) {
                    Log.e(TAG, "onFailure: $error")
                    toast("Error: $error")
                }
            }, null)
    }

    private fun startBTConnection(device: BluetoothDevice, uuid: UUID) {
        Log.d(TAG, "startBTConnection: Initializating RFCOM Bluetooth Connection.")
        bluetoothConnection.startClient(device, uuid)
    }

    private fun styleLineDataSet() {
        val colorRed = Color.parseColor("#781D42")

        signalDataSet.setDrawValues(false)
        signalDataSet.setDrawCircles(false)
        signalDataSet.color = colorRed
    }

    private fun refreshGraphPeriodically() {
        val refreshDataHandler = Handler(Looper.getMainLooper())
        val refreshDataCode = object : Runnable {
            override fun run() {
                if (AUSCULTATION_STATUS == Config.AUSCULTATION_RECORDING)
                    refreshGraph()
                refreshDataHandler.postDelayed(this, REFRESH_DATA_INTERVAL)
            }
        }
        refreshDataHandler.post(refreshDataCode)
    }

    private fun refreshParameterPeriodically() {
        val handler = Handler(Looper.getMainLooper())
        val runnable = object: Runnable {
            override fun run() {
                if (AUSCULTATION_STATUS == Config.AUSCULTATION_RECORDING)
                    refreshParameter()
                handler.postDelayed(this, REFRESH_PARAMETER_VIEW_INTERVAL)
            }
        }
        handler.post(runnable)
    }

    private fun refreshParameter() {
        when (FILTER_OPTION) {
            Config.OPTION_HEART -> {
                val bpm = parameterHelper.getHeartRate()
                val description = "Heart Rate: $bpm bpm"
                binding.textViewDescription.text = description
            }
            Config.OPTION_LUNG -> { // If 1 second elapsed
                val rr = parameterHelper.getRespirationRate()
                val description = "Respiration rate: $rr"
                binding.textViewDescription.text = description
            }
            Config.OPTION_UNFILTERED -> {
                val description = "Deskripsi audio"
                binding.textViewDescription.text = description
            }
        }
    }

    private fun refreshGraph() {
        lineData.notifyDataChanged()
        binding.chart.notifyDataSetChanged()
        binding.chart.setVisibleXRangeMaximum(CHART_X_RANGE_VISIBILITY.toFloat())
        binding.chart.moveViewTo(
            signalDataSet.entryCount - CHART_X_RANGE_VISIBILITY - 1f,
            CHART_Y_RANGE_VISIBILITY,
            YAxis.AxisDependency.LEFT
        )
    }

    private fun stopAction() {
        AUSCULTATION_STATUS = Config.AUSCULTATION_IDLE
        handleActionControlUI(AUSCULTATION_STATUS)
        parameterHelper.reset()
        tempAudioStream.close()
//        tempAudioFile.delete()
        clearGraph()
    }

    private fun prepareFiles() {
        tempAudioFile = prepareFile(Config.TEMP_AUDIO_FILENAME)
        tempAudioStream = prepareStream(tempAudioFile)
    }

    private fun prepareFile(filename: String): File {
        val file = File(getExternalFilesDir(null), filename)
        if(file.exists()) {
            file.delete()
        }
        file.createNewFile()
        return file
    }

    private fun prepareStream(file: File): DataOutputStream {
        return DataOutputStream(BufferedOutputStream(FileOutputStream(file)))
    }

    private fun clearGraph() {
        signalDataSet.clear()
        binding.chart.invalidate()
    }

    private fun setStatus(toolbarStatus: String?, bluetoothButtonStatus: String?) {
        if(toolbarStatus != null)
            supportActionBar?.subtitle = toolbarStatus
        if(bluetoothButtonStatus != null)
            binding.buttonBluetoothConnect.text = bluetoothButtonStatus
    }

    private fun convertToAudioSample(raw: Short): Short {
        val pcm16bit = (raw - 512) shl 6
        return pcm16bit.toShort()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}