package com.apincer.fileserver

/**
 * Per-IP throttle for PIN authentication attempts.
 *
 * The PIN is a short numeric secret, so without a limiter the whole keyspace can be brute
 * forced over the LAN in seconds. After [MAX_FAILURES] failures within [WINDOW_MS] an IP is
 * locked out with exponentially growing cooldowns (capped at [MAX_LOCKOUT_MS]). A successful
 * authentication clears the IP's record.
 */
object AuthThrottle {
    private const val MAX_FAILURES = 5
    private const val BASE_LOCKOUT_MS = 5_000L
    private const val MAX_LOCKOUT_MS = 5 * 60_000L
    private const val WINDOW_MS = 10 * 60_000L
    private const val MAX_TRACKED_IPS = 10_000

    private class Record(var failures: Int, var windowStart: Long, var lockedUntil: Long)

    private val records = HashMap<String, Record>()
    private val clock: () -> Long = System::currentTimeMillis

    /** Milliseconds the caller must wait before another attempt is allowed, or 0 if none. */
    @Synchronized
    fun retryAfterMs(ip: String, now: Long = clock()): Long {
        val record = records[ip] ?: return 0
        if (record.lockedUntil > now) return record.lockedUntil - now
        if (now - record.windowStart > WINDOW_MS) records.remove(ip)
        return 0
    }

    @Synchronized
    fun recordFailure(ip: String, now: Long = clock()) {
        if (records.size > MAX_TRACKED_IPS) purge(now)
        val record = records.getOrPut(ip) { Record(0, now, 0) }
        if (now - record.windowStart > WINDOW_MS && record.lockedUntil <= now) {
            record.failures = 0
            record.windowStart = now
        }
        record.failures++
        if (record.failures >= MAX_FAILURES) {
            val over = (record.failures - MAX_FAILURES).coerceIn(0, 6)
            val lockout = (BASE_LOCKOUT_MS shl over).coerceAtMost(MAX_LOCKOUT_MS)
            record.lockedUntil = now + lockout
        }
    }

    @Synchronized
    fun recordSuccess(ip: String) {
        records.remove(ip)
    }

    @Synchronized
    fun reset() {
        records.clear()
    }

    private fun purge(now: Long) {
        records.entries.removeAll { (_, r) -> r.lockedUntil <= now && now - r.windowStart > WINDOW_MS }
    }
}
