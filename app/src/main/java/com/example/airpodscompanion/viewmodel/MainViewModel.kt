package com.example.airpodscompanion.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.airpodscompanion.data.AirPodsStatus
import com.example.airpodscompanion.service.AirPodsScanService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(private val service: AirPodsScanService?) : ViewModel() {

    private val _uiState = MutableStateFlow(AirPodsStatus())
    val uiState: StateFlow<AirPodsStatus> = _uiState.asStateFlow()

    init {
        service?.bleManager?.airPodsStatus?.let { flow ->
            viewModelScope.launch {
                flow.collect { status ->
                    _uiState.value = status
                }
            }
        }
    }
    
    fun simulateToggle() {
        service?.bleManager?.simulateConnection()
    }
}

class MainViewModelFactory(private val service: AirPodsScanService?) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(service) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
