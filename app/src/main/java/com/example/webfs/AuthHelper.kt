package com.example.webfs

import kotlin.random.Random

object AuthHelper {
    var currentPin: String = "0000"
        private set

    fun generateNewPin() {
        currentPin = String.format("%04d", Random.nextInt(10000))
    }
    
    fun verifyPin(pin: String): Boolean {
        return pin == currentPin
    }
}
