package com.minicrm.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import java.time.Clock
import java.time.Duration

@ConfigurationProperties("app")
data class AppProperties(
    val session: Session = Session(),
    val demo: Demo = Demo(),
    val login: Login = Login(),
) {
    data class Session(
        /** Тривалість сесії зареєстрованого користувача. */
        val ttl: Duration = Duration.ofDays(30),
        val cookieName: String = "mcrm_session",
    )

    data class Demo(
        val ttl: Duration = Duration.ofHours(24),
        /** Скільки демо можна запустити з однієї IP-адреси за [perIpWindow]. */
        val perIpLimit: Int = 10,
        val perIpWindow: Duration = Duration.ofHours(1),
        /** Глобальна стеля одночасно активних демо-акаунтів. */
        val maxActive: Int = 2000,
        val cleanupInterval: Duration = Duration.ofMinutes(5),
        /** Скільки зберігати «надгробки» сесій видаленого демо для екрана demo-expired. */
        val tombstoneRetention: Duration = Duration.ofDays(7),
    )

    data class Login(
        val maxFailures: Int = 10,
        val window: Duration = Duration.ofMinutes(15),
    )
}

@Configuration
@EnableScheduling
@EnableConfigurationProperties(AppProperties::class)
class AppConfig {
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
