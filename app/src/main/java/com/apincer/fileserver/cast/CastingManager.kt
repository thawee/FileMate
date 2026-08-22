package com.apincer.fileserver.cast

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.SocketTimeoutException

class CastingManager(context: Context) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val client = OkHttpClient()
    
    private val _devices = MutableStateFlow<List<TvCaster>>(emptyList())
    val devices: StateFlow<List<TvCaster>> = _devices.asStateFlow()

    private var ssdpJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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
        
        startDlnaDiscovery()
    }

    private fun startDlnaDiscovery() {
        ssdpJob?.cancel()
        ssdpJob = coroutineScope.launch {
            try {
                val socket = DatagramSocket()
                socket.soTimeout = 5000
                val address = InetAddress.getByName("239.255.255.250")
                val searchMsg = "M-SEARCH * HTTP/1.1\r\n" +
                                "HOST: 239.255.255.250:1900\r\n" +
                                "MAN: \"ssdp:discover\"\r\n" +
                                "MX: 3\r\n" +
                                "ST: urn:schemas-upnp-org:service:AVTransport:1\r\n\r\n"
                
                val packet = DatagramPacket(searchMsg.toByteArray(), searchMsg.length, address, 1900)
                for (i in 0..2) {
                    socket.send(packet)
                    delay(200)
                }

                val buffer = ByteArray(4096)
                while (isActive) {
                    val recvPacket = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(recvPacket)
                        val response = String(recvPacket.data, 0, recvPacket.length)
                        val lines = response.lines()
                        val locationLine = lines.find { it.startsWith("LOCATION:", ignoreCase = true) }
                        val usnLine = lines.find { it.startsWith("USN:", ignoreCase = true) }
                        
                        if (locationLine != null) {
                            val locationUrl = locationLine.substringAfter(":").trim()
                            val id = usnLine?.substringAfter(":")?.trim() ?: locationUrl
                            fetchDlnaDeviceXml(locationUrl, id)
                        }
                    } catch (e: SocketTimeoutException) {
                        break
                    }
                }
                socket.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private suspend fun fetchDlnaDeviceXml(url: String, id: String) {
        if (_devices.value.any { it.deviceId == id }) return
        
        // Exclude the http/https string before passing it to URL
        val actualUrl = if (url.startsWith("//")) "http:$url" else url

        try {
            val request = Request.Builder().url(actualUrl).build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val xml = response.body?.string() ?: return
                
                val nameMatch = Regex("<friendlyName>(.*?)</friendlyName>").find(xml)
                val friendlyName = nameMatch?.groupValues?.get(1) ?: "DLNA Device"
                
                val serviceMatch = Regex("(?s)<serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>.*?<controlURL>(.*?)</controlURL>").find(xml)
                val controlUrlPath = serviceMatch?.groupValues?.get(1)
                
                if (controlUrlPath != null) {
                    val baseUri = java.net.URI(actualUrl)
                    val controlUrl = baseUri.resolve(controlUrlPath).toString()
                    
                    val caster = DlnaCaster(id, friendlyName, controlUrl)
                    _devices.update { current ->
                        if (current.none { it.deviceId == id }) current + caster else current
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopDiscovery() {
        try {
            nsdManager.stopServiceDiscovery(airPlayListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        ssdpJob?.cancel()
    }
}
