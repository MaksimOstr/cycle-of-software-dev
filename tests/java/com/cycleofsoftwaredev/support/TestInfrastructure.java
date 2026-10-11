package com.cycleofsoftwaredev.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Real infrastructure for the integration tests: PostgreSQL is started in Docker by Testcontainers and the
 * application is connected to it. One container serves all test classes of a build; Docker must be running.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestInfrastructure {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    static {
        POSTGRES.start();
    }

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return POSTGRES;
    }
}
