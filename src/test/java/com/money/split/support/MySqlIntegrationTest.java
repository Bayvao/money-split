package com.money.split.support;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Provides a migrated MySQL database and deterministic cleanup for schema integration tests.
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
@Import(TestSecurityConfiguration.class)
public abstract class MySqlIntegrationTest {

    @Autowired
    protected JdbcClient jdbcClient;

    /**
     * Registers either the CI-provided datasource or a pinned Testcontainers MySQL instance.
     *
     * @param registry Spring's dynamic test-property registry
     */
    @DynamicPropertySource
    static void configureMySql(DynamicPropertyRegistry registry) {
        MySqlTestDatabase.configure(registry);
    }

    /**
     * Removes mutable foundation data while retaining the versioned currency seed.
     */
    @BeforeEach
    void cleanDatabase() {
        // Child rows are deleted before parent rows so cleanup honors production foreign keys.
        jdbcClient.sql("DELETE FROM audit_event").update();
        jdbcClient.sql("DELETE FROM group_member").update();
        jdbcClient.sql("DELETE FROM split_group").update();
        jdbcClient.sql("DELETE FROM app_user").update();
    }
}
