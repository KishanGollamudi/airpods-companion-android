package com.example.airpodscompanion.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@SuppressLint("MissingPermission") // Permissions handled in UI
class BluetoothLeManager(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter

    private val _airPodsStatus = MutableStateFlow(AirPodsStatus())
    val airPodsStatus: StateFlow<AirPodsStatus> = _airPodsStatus.asStateFlow()

    private var isScanning = false

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val record = result.scanRecord ?: return
            
            // Apple Manufacturer ID is 0x004C
            val manufacturerData = record.getManufacturerSpecificData(0x004C)
            
            if (manufacturerData != null && manufacturerData.size == 27) {
                // This is a simplified parser logic based on common reverse-engineering
                // Real data is bit-packed and proprietary
                parseAirPodsData(manufacturerData)
            }
        }
        
        override fun onScanFailed(errorCode: Int) {
            Log.e("BLE", "Scan failed: $errorCode")
        }
    }

    fun startScan() {
        if (adapter?.isEnabled == true && !isScanning) {
            val scanner = adapter.bluetoothLeScanner
            
            // Filter for Apple devices could be done via Manufacturer ID in ScanFilter
            // But usually we filter widely and parse manually for specific beacons
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            try {
                scanner.startScan(null, settings, scanCallback)
                isScanning = true
                _airPodsStatus.update { it.copy(isConnected = true) }
            } catch (e: Exception) {
                Log.e("BLE", "Error starting scan", e)
            }
        }
    }

    fun stopScan() {
        if (isScanning && adapter?.isEnabled == true) {
            try {
                adapter.bluetoothLeScanner.stopScan(scanCallback)
            } catch (e: Exception) {
                Log.e("BLE", "Error stopping scan", e)
            }
            isScanning = false
            _airPodsStatus.update { it.copy(isConnected = false) }
        }
    }

    private fun parseAirPodsData(data: ByteArray) {
        // NOTE: exact bit decoding varies by model (Pro, Gen 1, Gen 2)
        // This is a heuristic simulation for demonstration
        try {
            // Flip bits occasionally happen, simple parse for battery usually at these offsets
            // Values are often 0-10 mapped to 0-100%
            val leftRaw = (data[6].toInt() and 0xF0) shr 4
            val rightRaw = data[6].toInt() and 0x0F
            val caseRaw = data[7].toInt() and 0x0F
            
            val leftCharge = (data[8].toInt() and 0x20) != 0
            val rightCharge = (data[8].toInt() and 0x10) != 0

            _airPodsStatus.update {
                it.copy(
                    isConnected = true,
                    leftBattery = if (leftRaw <= 10) leftRaw * 10 else -1,
                    rightBattery = if (rightRaw <= 10) rightRaw * 10 else -1,
                    caseBattery = if (caseRaw <= 10) caseRaw * 10 else -1,
                    isLeftCharging = leftCharge,
                    isRightCharging = rightCharge
                )
            }
        } catch (e: Exception) {
            // Parser error, ignore
        }
    }
    
    // For testing without physical device
    fun simulateConnection() {
        _airPodsStatus.update {
            it.copy(
                isConnected = true,
                leftBattery = 85,
                rightBattery = 90,
                caseBattery = 100,
                model = "AirPods Pro (Sim)"
            )
        }
    }
}
