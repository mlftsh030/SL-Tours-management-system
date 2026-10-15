package com.sltours.support;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Runs against a real PostgreSQL 16 with every Flyway migration applied, so constraints, exclusion
 * rules and entity mappings are verified exactly as in production. Requires Docker.
 */
@SpringBootTest
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(Fixtures.class)
public abstract class PostgresIntegrationTest {
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    static { POSTGRES.start(); }
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("sltours.storage.local-root", () -> System.getProperty("java.io.tmpdir") + "/sltours-test-media");
    }
}
