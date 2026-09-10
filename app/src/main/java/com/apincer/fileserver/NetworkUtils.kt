package com.apincer.fileserver

import java.net.Inet4Address
import java.net.NetworkInterface

enum class NetworkType {
    HOTSPOT,
    WIFI,
    ETHERNET,
    USB_TETHERING,
    OTHER,
    CELLULAR
}

data class NetworkAddressInfo(
    val ip: String,
    val interfaceName: String,
    val displayName: String,
    val type: NetworkType,
    val priorityScore: Int
)

object NetworkUtils {
    private val CELLULAR_NAMES = listOf(
        "rmnet", "ccmni", "pdp", "ppp", "wwan", "cellular", "qmimux", "clat", "radio"
    )
    private val VIRTUAL_VPN_NAMES = listOf(
        "tun", "tap", "dummy"
    )

    fun determineNetworkType(interfaceName: String, ip: String): NetworkType {
        val name = interfaceName.lowercase()
        if (CELLULAR_NAMES.any { name.startsWith(it) || name.contains(it) }) {
            return NetworkType.CELLULAR
        }
        if (name.contains("softap") || name.startsWith("ap") || name.contains("swlan") || name.contains("tether") || ip == "192.168.43.1") {
            return NetworkType.HOTSPOT
        }
        if (name.contains("rndis")) {
            return NetworkType.USB_TETHERING
        }
        if (name.contains("eth")) {
            return NetworkType.ETHERNET
        }
        if (name.contains("wlan")) {
            return NetworkType.WIFI
        }
        if (VIRTUAL_VPN_NAMES.any { name.startsWith(it) }) {
            return NetworkType.OTHER
        }
        return NetworkType.OTHER
    }

    fun calculatePriority(type: NetworkType, ip: String): Int {
        return when (type) {
            NetworkType.HOTSPOT -> if (ip == "192.168.43.1") 1000 else 900
            NetworkType.WIFI -> 800
            NetworkType.ETHERNET -> 750
            NetworkType.USB_TETHERING -> 700
            NetworkType.OTHER -> 200
            NetworkType.CELLULAR -> -100 // Excluded from local file server advertisement
        }
    }

    /**
     * Pure function to filter and sort interface/IP pairs.
     * Cellular carrier interfaces are filtered out because carriers block incoming connections
     * and Android hotspot firewall isolates clients from carrier WAN interfaces.
     */
    fun filterAndRankCandidates(candidates: List<Pair<String, String>>): List<NetworkAddressInfo> {
        val results = mutableListOf<NetworkAddressInfo>()
        for ((name, ip) in candidates) {
            val type = determineNetworkType(name, ip)
            if (type == NetworkType.CELLULAR) continue

            val score = calculatePriority(type, ip)
            val displayName = when (type) {
                NetworkType.HOTSPOT -> "Hotspot"
                NetworkType.WIFI -> "Wi-Fi"
                NetworkType.ETHERNET -> "Ethernet"
                NetworkType.USB_TETHERING -> "USB Tethering"
                else -> name
            }
            results.add(NetworkAddressInfo(ip, name, displayName, type, score))
        }

        return results
            .distinctBy { it.ip }
            .sortedByDescending { it.priorityScore }
    }

    /**
     * Queries all active system network interfaces and returns reachable LAN/Hotspot addresses.
     */
    fun getLocalNetworkAddresses(): List<NetworkAddressInfo> {
        val candidates = mutableListOf<Pair<String, String>>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
            while (interfaces.hasMoreElements()) {
                val nif = interfaces.nextElement()
                val isUp = try { nif.isUp } catch (e: Exception) { false }
                if (!isUp) continue

                val name = nif.name.lowercase()
                val addresses = nif.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val ip = addr.hostAddress ?: continue
                        candidates.add(name to ip)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return filterAndRankCandidates(candidates)
    }
}
