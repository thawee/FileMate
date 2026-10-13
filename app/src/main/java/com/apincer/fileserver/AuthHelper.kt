package com.apincer.fileserver

import java.security.SecureRandom
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AuthHelper {
    // SecureRandom, not java.util.Random/kotlin.random: the PIN is a security credential and
    // must not come from a predictable linear-congruential generator.
    private val secureRandom = SecureRandom()
    private val _currentPin = MutableStateFlow("0000")
    val currentPin: StateFlow<String> = _currentPin.asStateFlow()

    fun generateNewPin() {
        _currentPin.value = String.format("%04d", secureRandom.nextInt(10000))
    }
    
    fun verifyPin(pin: String): Boolean {
        return pin == _currentPin.value
    }
}
