package com.minicrm.common.web

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

/**
 * Простий in-memory обмежувач «не більше [limit] подій за [window]» на ключ (IP, IP+email ...).
 * Достатній для одного інстансу бекенду; стан не переживає перезапуск.
 */
class RateLimiter(private val clock: Clock, private val limit: Int, private val window: Duration) {

    private val hits = ConcurrentHashMap<String, ArrayDeque<Instant>>()

    /** true, якщо ліміт вичерпано (подію не зареєстровано). */
    fun isLimited(key: String): Boolean {
        val q = hits[key] ?: return false
        synchronized(q) {
            prune(q)
            return q.size >= limit
        }
    }

    /** Реєструє подію; повертає false, якщо ліміт уже вичерпано. */
    fun tryAcquire(key: String): Boolean {
        val q = hits.computeIfAbsent(key) { ArrayDeque() }
        synchronized(q) {
            prune(q)
            if (q.size >= limit) return false
            q.addLast(clock.instant())
            return true
        }
    }

    fun reset(key: String) {
        hits.remove(key)
    }

    private fun prune(q: ArrayDeque<Instant>) {
        val border = clock.instant().minus(window)
        while (q.isNotEmpty() && !q.peekFirst().isAfter(border)) q.removeFirst()
        if (hits.size > 50_000) hits.entries.removeIf { it.value.isEmpty() }
    }
}
