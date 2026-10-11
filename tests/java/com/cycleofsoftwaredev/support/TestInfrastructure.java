package com.cycleofsoftwaredev.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Real infrastructure for the integration tests: PostgreSQL and Redis are started in Docker by Testcontainers
 * and the application is connected to them. One pair of containers serves all test classes of a build;
 * Docker must be running.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestInfrastructure {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8").withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return POSTGRES;
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redis() {
        return REDIS;
    }

    /** All tests call the API from one address, so the limit of login requests is lifted for them. */
    @Bean
    DynamicPropertyRegistrar testProperties() {
        return registry -> registry.add("shopsphere.security.auth-requests-per-minute", () -> 1_000_000);
    }
}
