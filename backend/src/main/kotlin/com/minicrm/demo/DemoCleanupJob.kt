package com.minicrm.demo

import com.minicrm.auth.AppUserRepository
import com.minicrm.auth.UserSessionRepository
import com.minicrm.config.AppProperties
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * Автоочищення: прострочені демо-акаунти видаляються разом з усіма даними (каскад FK),
 * старі сесії — теж. Запускається кожні `app.demo.cleanup-interval` (за замовчуванням 5 хв).
 */
@Component
class DemoCleanupJob(
    private val users: AppUserRepository,
    private val sessions: UserSessionRepository,
    private val props: AppProperties,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${app.demo.cleanup-interval:PT5M}", initialDelayString = "\${app.demo.cleanup-initial-delay:PT30S}")
    fun scheduled() {
        val result = run()
        if (result.demos > 0 || result.sessions > 0) log.info("Demo cleanup: {} demo accounts, {} sessions removed", result.demos, result.sessions)
    }

    data class Result(val demos: Int, val sessions: Int)

    @Transactional
    fun run(): Result {
        val now = clock.instant()
        val demos = users.deleteExpiredDemos(now)
        val purged = sessions.purge(now, now.minus(props.demo.tombstoneRetention))
        return Result(demos, purged)
    }
}
