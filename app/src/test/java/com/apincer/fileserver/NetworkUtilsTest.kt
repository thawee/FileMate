package com.apincer.fileserver

import org.junit.Assert.*
import org.junit.Test

class NetworkUtilsTest {

    @Test
    fun testCellularCarrierInterfacesAreFilteredOut() {
        // Simulates the exact user scenario: rmnet_data0 with cellular CGNAT IP
        val candidates = listOf(
            "rmnet_data0" to "10.112.12.215",
            "ccmni0" to "10.45.67.89",
            "pdp0" to "100.64.1.2"
        )
        val filtered = NetworkUtils.filterAndRankCandidates(candidates)
        assertTrue("Cellular carrier IPs must be excluded", filtered.isEmpty())
    }

    @Test
    fun testHotspotIsPrioritizedOverWifiAndCellular() {
        val candidates = listOf(
            "rmnet_data0" to "10.112.12.215",
            "wlan0" to "192.168.1.150",
            "ap0" to "192.168.43.1"
        )
        val filtered = NetworkUtils.filterAndRankCandidates(candidates)
        assertEquals(2, filtered.size)
        // ap0 should be first with highest priority
        assertEquals("192.168.43.1", filtered[0].ip)
        assertEquals(NetworkType.HOTSPOT, filtered[0].type)
        assertEquals("Hotspot", filtered[0].displayName)

        // wlan0 should be second
        assertEquals("192.168.1.150", filtered[1].ip)
        assertEquals(NetworkType.WIFI, filtered[1].type)
    }

    @Test
    fun testAlternativeHotspotInterfaceNames() {
        val softAp = NetworkUtils.determineNetworkType("softap0", "192.168.43.1")
        assertEquals(NetworkType.HOTSPOT, softAp)

        val swlan = NetworkUtils.determineNetworkType("swlan0", "192.168.43.1")
        assertEquals(NetworkType.HOTSPOT, swlan)

        val tether = NetworkUtils.determineNetworkType("tether0", "192.168.43.1")
        assertEquals(NetworkType.HOTSPOT, tether)

        val rndis = NetworkUtils.determineNetworkType("rndis0", "192.168.42.129")
        assertEquals(NetworkType.USB_TETHERING, rndis)
    }

    @Test
    fun testStandardAndroidHotspotIpDetection() {
        // Even if interface name is generic wlan1, standard 192.168.43.1 should identify as HOTSPOT
        val type = NetworkUtils.determineNetworkType("wlan1", "192.168.43.1")
        assertEquals(NetworkType.HOTSPOT, type)
    }
}
