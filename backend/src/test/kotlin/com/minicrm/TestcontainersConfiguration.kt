package com.minicrm

import com.minicrm.support.MutableClock
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    fun postgresContainer(): PostgreSQLContainer = PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"))

    /** Керований годинник: тести «перемотують» час (24 год демо, межі Today/Overdue). */
    @Bean
    @Primary
    fun testClock(): MutableClock = MutableClock()
}
