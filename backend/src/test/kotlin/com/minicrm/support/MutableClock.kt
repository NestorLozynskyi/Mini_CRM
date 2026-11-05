package com.minicrm.support

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class MutableClock(@Volatile var now: Instant = BASE) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = Clock.fixed(now, zone)
    override fun instant(): Instant = now

    fun advance(d: Duration) { now = now.plus(d) }
    fun reset() { now = BASE }

    companion object {
        /** Середа, 4 листопада 2026, 12:00 UTC — «сьогодні» макетів. */
        val BASE: Instant = Instant.parse("2026-11-04T12:00:00Z")
    }
}
