package com.example.webfs.cast

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.net.Inet4Address

class CastingManager(context: Context) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    
    private val _devices = MutableStateFlow<List<TvCaster>>(emptyList())
    val devices: StateFlow<List<TvCaster>> = _devices.asStateFlow()

    private val airPlayListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(regType: String) {}
        override fun onServiceFound(service: NsdServiceInfo) {
            if (service.serviceType == "_airplay._tcp.") {
                nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                    
                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        val host = serviceInfo.host
                        if (host is Inet4Address) {
                            val ip = host.hostAddress
                            val port = serviceInfo.port
                            val name = serviceInfo.serviceName
                            val id = "$ip:$port"

                            val caster = AirPlayCaster(id, name, ip ?: "", port)
                            _devices.update { current ->
                                if (current.none { it.deviceId == id }) {
                                    current + caster
                                } else {
                                    current
                                }
                            }
                        }
                    }
                })
            }
        }
        override fun onServiceLost(service: NsdServiceInfo) {
            _devices.update { current ->
                current.filterNot { it.deviceName == service.serviceName }
            }
        }
        override fun onDiscoveryStopped(serviceType: String) {}
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            nsdManager.stopServiceDiscovery(this)
        }
        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            nsdManager.stopServiceDiscovery(this)
        }
    }

    fun startDiscovery() {
        _devices.value = emptyList()
        try {
            nsdManager.discoverServices("_airplay._tcp.", NsdManager.PROTOCOL_DNS_SD, airPlayListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // TODO: Start DLNA SSDP discovery here if needed
    }

    fun stopDiscovery() {
        try {
            nsdManager.stopServiceDiscovery(airPlayListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
