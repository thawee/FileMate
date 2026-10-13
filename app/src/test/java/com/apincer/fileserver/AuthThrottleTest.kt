package com.apincer.fileserver

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AuthThrottleTest {

    private val ip = "192.168.1.50"

    @Before
    fun clean() = AuthThrottle.reset()

    @Test
    fun belowThresholdIsNotLocked() {
        val t0 = 1_000_000L
        repeat(4) { AuthThrottle.recordFailure(ip, t0) }
        assertEquals(0L, AuthThrottle.retryAfterMs(ip, t0))
    }

    @Test
    fun lockoutEngagesAtThreshold() {
        val t0 = 1_000_000L
        repeat(5) { AuthThrottle.recordFailure(ip, t0) }
        assertTrue("IP should be locked after 5 failures", AuthThrottle.retryAfterMs(ip, t0) > 0)
    }

    @Test
    fun lockoutExpiresAfterCooldown() {
        val t0 = 1_000_000L
        repeat(5) { AuthThrottle.recordFailure(ip, t0) }
        val wait = AuthThrottle.retryAfterMs(ip, t0)
        assertEquals(0L, AuthThrottle.retryAfterMs(ip, t0 + wait + 1))
    }

    @Test
    fun successClearsFailures() {
        val t0 = 1_000_000L
        repeat(5) { AuthThrottle.recordFailure(ip, t0) }
        AuthThrottle.recordSuccess(ip)
        assertEquals(0L, AuthThrottle.retryAfterMs(ip, t0))
    }

    @Test
    fun repeatedLockoutsBackOffLonger() {
        val t0 = 1_000_000L
        repeat(5) { AuthThrottle.recordFailure(ip, t0) }
        val firstWait = AuthThrottle.retryAfterMs(ip, t0)
        AuthThrottle.recordFailure(ip, t0)
        val secondWait = AuthThrottle.retryAfterMs(ip, t0)
        assertTrue("Lockout should grow with more failures", secondWait > firstWait)
    }

    @Test
    fun separateIpsTrackedIndependently() {
        val t0 = 1_000_000L
        repeat(5) { AuthThrottle.recordFailure("10.0.0.1", t0) }
        assertEquals(0L, AuthThrottle.retryAfterMs("10.0.0.2", t0))
    }
}
