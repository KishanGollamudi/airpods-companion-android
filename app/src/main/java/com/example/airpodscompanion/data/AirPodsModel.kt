package com.example.airpodscompanion.data

data class AirPodsStatus(
    val isConnected: Boolean = false,
    val leftBattery: Int = -1,
    val rightBattery: Int = -1,
    val caseBattery: Int = -1,
    val isLeftCharging: Boolean = false,
    val isRightCharging: Boolean = false,
    val isCaseCharging: Boolean = false,
    val model: String = "Unknown"
)
