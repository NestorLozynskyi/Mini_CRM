package com.minicrm.support

import com.minicrm.TestcontainersConfiguration
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/** Повний стек: реальний HTTP-порт → Spring Security → MVC → Spring Data JDBC → PostgreSQL (Testcontainers). */
@Import(TestcontainersConfiguration::class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "app.demo.per-ip-limit=3",
        "app.demo.cleanup-initial-delay=PT1000H",
        "app.login.max-failures=5",
    ],
)
abstract class IntegrationTest {
    @Value("\${local.server.port}")
    protected var port: Int = 0

    @Autowired
    protected lateinit var clock: MutableClock

    @Autowired
    protected lateinit var jdbc: JdbcClient

    @BeforeEach
    fun resetClock() = clock.reset()

    /** Новий «браузер» з унікальною IP (щоб ліміти демо/входу не перетиналися між тестами). */
    protected fun browser(timeZone: String = "UTC") = ApiSession("http://localhost:$port", "10.1.${ipSeq.incrementAndGet() / 250}.${ipSeq.get() % 250 + 1}", timeZone)

    protected fun uniqueEmail() = "user-${UUID.randomUUID().toString().take(8)}@example.com"

    protected fun count(sql: String, vararg args: Pair<String, Any>): Long =
        jdbc.sql(sql).params(args.toMap()).query(Long::class.java).single()

    companion object {
        private val ipSeq = AtomicInteger()
    }
}
