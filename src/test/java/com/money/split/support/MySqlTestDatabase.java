package com.money.split.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Supplies one MySQL 8.4 database for all integration tests in the JVM.
 */
final class MySqlTestDatabase {

    private static final String MYSQL_IMAGE =
            "mysql:8.4.13@sha256:a0225d1ea6631c8bc2385dc4c2faba17bbf1834ecf369e1659a9a382bd8d5dd7";
    private static MySQLContainer container;

    /**
     * Prevents construction because this class owns one process-wide test database.
     */
    private MySqlTestDatabase() {
    }

    /**
     * Uses an externally configured datasource when present; otherwise starts the pinned MySQL image.
     *
     * @param registry Spring's dynamic test-property registry
     */
    static synchronized void configure(DynamicPropertyRegistry registry) {
        if (hasConfiguredDataSource()) {
            return;
        }
        if (container == null) {
            DockerImageName image = DockerImageName.parse(MYSQL_IMAGE).asCompatibleSubstituteFor("mysql");
            // Synchronization around this method guarantees concurrent Spring contexts start only one container.
            container = new MySQLContainer(image)
                    .withDatabaseName("money_split_test")
                    .withUsername("money_split")
                    .withPassword("money_split_password");
            container.start();
        }
        registry.add("spring.datasource.url", container::getJdbcUrl);
        registry.add("spring.datasource.username", container::getUsername);
        registry.add("spring.datasource.password", container::getPassword);
    }

    /**
     * Determines whether CI or the developer supplied an isolated MySQL test database.
     *
     * @return {@code true} when a datasource URL is present in the environment
     */
    private static boolean hasConfiguredDataSource() {
        String value = System.getenv("SPRING_DATASOURCE_URL");
        return value != null && !value.isBlank();
    }
}
