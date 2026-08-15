package com.example.webfs

import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AuthHelper {
    private val _currentPin = MutableStateFlow("0000")
    val currentPin: StateFlow<String> = _currentPin.asStateFlow()

    fun generateNewPin() {
        _currentPin.value = String.format("%04d", Random.nextInt(10000))
    }
    
    fun verifyPin(pin: String): Boolean {
        return pin == _currentPin.value
    }
}
