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
import android.content.*
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.endrawan.auscultationmonitoring.bluetooth.BluetoothConnectionService
import com.endrawan.auscultationmonitoring.callbacks.BluetoothCallbacks
import com.endrawan.auscultationmonitoring.databinding.ActivityNewBluetoothBinding
import java.nio.charset.Charset
import java.util.*
import java.util.regex.Pattern


class NewBluetoothActivity : AppCompatActivity(), BluetoothCallbacks {

    private lateinit var binding:ActivityNewBluetoothBinding

    private var bluetoothAdapter: BluetoothAdapter? = null
    private val TAG = "NewBluetoothActivity"

    private val uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")

    private lateinit var bluetoothDevice: BluetoothDevice
    private lateinit var bluetoothConnection: BluetoothConnectionService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNewBluetoothBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        // Get the local Bluetooth adapter
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        // If the adapter is null, then Bluetooth is not supported
        if (bluetoothAdapter == null) {
            toast("Bluetooth tidak terdeteksi pada perangkat anda")
            finish()
        }

        binding.buttonSend.setOnClickListener {
            val text = "Hentai"
            val bytes: ByteArray = text.toByteArray(Charset.defaultCharset())
            bluetoothConnection.write(bytes)
        }
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
        } else {
            showBluetoothDevicesList()
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
                AlertDialog.Builder(this@NewBluetoothActivity)
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

    private val enableBluetoothLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                toast("Bluetooth activated!")
                showBluetoothDevicesList()
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

    private fun bluetoothEnableHandler() {
        // Handle permission only on android device above S version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            askBluetoothPermissions()
        } else {
            askEnableBluetooth()
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

    private fun askEnableBluetooth() {
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        enableBluetoothLauncher.launch(enableBtIntent)
    }

//    private fun enableBluetooth() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//            bluetoothPermissionsLauncher.launch(
//                arrayOf(
//                    Manifest.permission.BLUETOOTH_SCAN,
//                    Manifest.permission.BLUETOOTH_CONNECT
//                )
//            )
//        } else {
//            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
//            enableBluetoothLauncher.launch(enableBtIntent)
//        }
//    }

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
            toast("Choosen device: ${devicesName[which]}")
            bluetoothDevice = devices[which]
            startConnection(devices[which], bluetoothAdapter)
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
                startConnection(deviceToPair, bluetoothAdapter!!)
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

    private fun startConnection(device: BluetoothDevice, bluetoothAdapter: BluetoothAdapter) {
        bluetoothConnection = BluetoothConnectionService(bluetoothAdapter, this)
        startBTConnection(device, uuid)
    }

    private fun startBTConnection(device: BluetoothDevice, uuid: UUID) {
        Log.d(TAG, "startBTConnection: Initializating RFCOM Bluetooth Connection.")
        bluetoothConnection.startClient(device, uuid)
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDeviceConnected() {
        toast("Device connected by bluetooth")
    }

    override fun onNewData(incomingData: Short) {
        //TODO("Not yet implemented")
    }
}