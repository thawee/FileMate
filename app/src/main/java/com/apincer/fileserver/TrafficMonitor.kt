package com.apincer.fileserver

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

object TrafficMonitor {
    private val webfsRxAtomic = AtomicLong(0)
    private val webfsTxAtomic = AtomicLong(0)
    private val proxyRxAtomic = AtomicLong(0)
    private val proxyTxAtomic = AtomicLong(0)

    private val _webfsRxBytes = MutableStateFlow(0L)
    val webfsRxBytes: StateFlow<Long> = _webfsRxBytes.asStateFlow()

    private val _webfsTxBytes = MutableStateFlow(0L)
    val webfsTxBytes: StateFlow<Long> = _webfsTxBytes.asStateFlow()

    private val _proxyRxBytes = MutableStateFlow(0L)
    val proxyRxBytes: StateFlow<Long> = _proxyRxBytes.asStateFlow()

    private val _proxyTxBytes = MutableStateFlow(0L)
    val proxyTxBytes: StateFlow<Long> = _proxyTxBytes.asStateFlow()

    private var monitorJob: Job? = null

    fun start() {
        if (monitorJob?.isActive == true) return
        monitorJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                _webfsRxBytes.value = webfsRxAtomic.get()
                _webfsTxBytes.value = webfsTxAtomic.get()
                _proxyRxBytes.value = proxyRxAtomic.get()
                _proxyTxBytes.value = proxyTxAtomic.get()
                delay(1000)
            }
        }
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
    }

    fun addWebfsRx(bytes: Long) {
        webfsRxAtomic.addAndGet(bytes)
    }

    fun addWebfsTx(bytes: Long) {
        webfsTxAtomic.addAndGet(bytes)
    }

    fun addProxyRx(bytes: Long) {
        proxyRxAtomic.addAndGet(bytes)
    }

    fun addProxyTx(bytes: Long) {
        proxyTxAtomic.addAndGet(bytes)
    }

    fun reset() {
        webfsRxAtomic.set(0)
        webfsTxAtomic.set(0)
        proxyRxAtomic.set(0)
        proxyTxAtomic.set(0)
        _webfsRxBytes.value = 0L
        _webfsTxBytes.value = 0L
        _proxyRxBytes.value = 0L
        _proxyTxBytes.value = 0L
    }
}
