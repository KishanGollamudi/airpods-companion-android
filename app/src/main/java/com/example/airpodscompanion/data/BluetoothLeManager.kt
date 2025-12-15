package com.example.airpodscompanion.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@SuppressLint("MissingPermission")
class BluetoothLeManager(private val context: Context) {

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    private val adapter: BluetoothAdapter? = bluetoothManager.adapter

    private val _airPodsStatus = MutableStateFlow(AirPodsStatus())
    val airPodsStatus: StateFlow<AirPodsStatus> = _airPodsStatus.asStateFlow()

    private var isScanning = false

    private var lastSeenDeviceAddress: String? = null

    private val scanCallback = object : ScanCallback() {

        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val record = result.scanRecord

            val deviceName = device.name ?: "Unknown"
            val deviceAddress = device.address
            val rssi = result.rssi

            // Avoid flooding logs with same device
            if (deviceAddress == lastSeenDeviceAddress) return
            lastSeenDeviceAddress = deviceAddress

            Log.d(
                "BLE_SCAN",
                "Device: $deviceName | MAC: $deviceAddress | RSSI: $rssi"
            )

            // --- APPLE AIRPODS DETECTION ---
            val manufacturerData = record?.getManufacturerSpecificData(0x004C)
            if (manufacturerData != null) {
                Log.d("BLE_SCAN", "Apple manufacturer data detected (${manufacturerData.size} bytes)")
                parseAirPodsData(manufacturerData)
                return
            }

            // --- GENERIC EARBUD DETECTION ---
            if (isLikelyEarbuds(deviceName)) {
                Log.d("BLE_SCAN", "Likely earbuds detected: $deviceName")

                _airPodsStatus.update {
                    it.copy(
                        isConnected = true,
                        model = deviceName,
                        leftBattery = -1,
                        rightBattery = -1,
                        caseBattery = -1
                    )
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("BLE_SCAN", "Scan failed with error code: $errorCode")
        }
    }

    fun startScan() {
        if (adapter?.isEnabled == true && !isScanning) {
            val scanner = adapter.bluetoothLeScanner

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            try {
                scanner.startScan(null, settings, scanCallback)
                isScanning = true
                Log.d("BLE_SCAN", "BLE scan started")
            } catch (e: Exception) {
                Log.e("BLE_SCAN", "Error starting scan", e)
            }
        }
    }

    fun stopScan() {
        if (isScanning && adapter?.isEnabled == true) {
            try {
                adapter.bluetoothLeScanner.stopScan(scanCallback)
                Log.d("BLE_SCAN", "BLE scan stopped")
            } catch (e: Exception) {
                Log.e("BLE_SCAN", "Error stopping scan", e)
            }
            isScanning = false
        }
    }

    private fun parseAirPodsData(data: ByteArray) {
        try {
            if (data.size < 8) return

            val leftRaw = (data[6].toInt() and 0xF0) shr 4
            val rightRaw = data[6].toInt() and 0x0F
            val caseRaw = data[7].toInt() and 0x0F

            val leftCharge = (data[8].toInt() and 0x20) != 0
            val rightCharge = (data[8].toInt() and 0x10) != 0

            _airPodsStatus.update {
                it.copy(
                    isConnected = true,
                    model = "Apple AirPods",
                    leftBattery = if (leftRaw <= 10) leftRaw * 10 else -1,
                    rightBattery = if (rightRaw <= 10) rightRaw * 10 else -1,
                    caseBattery = if (caseRaw <= 10) caseRaw * 10 else -1,
                    isLeftCharging = leftCharge,
                    isRightCharging = rightCharge
                )
            }

            Log.d(
                "BLE_AIRPODS",
                "Parsed batteries → L:${leftRaw * 10}% R:${rightRaw * 10}% Case:${caseRaw * 10}%"
            )

        } catch (e: Exception) {
            Log.e("BLE_AIRPODS", "Failed to parse AirPods data", e)
        }
    }

    private fun isLikelyEarbuds(name: String): Boolean {
        val lower = name.lowercase()
        return listOf(
            "buds",
            "ear",
            "pods",
            "air",
            "galaxy",
            "sony",
            "jabra",
            "pixel"
        ).any { lower.contains(it) }
    }

    fun simulateConnection() {
        _airPodsStatus.update {
            it.copy(
                isConnected = true,
                model = "Simulated Earbuds",
                leftBattery = 80,
                rightBattery = 75,
                caseBattery = 90
            )
        }
    }
}

