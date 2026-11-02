package com.money.split;

import com.money.split.support.MySqlIntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the application starts against the Liquibase-managed MySQL schema.
 */
class MoneySplitApplicationTests extends MySqlIntegrationTest {

    /**
     * Starts the complete Spring context so invalid database or Liquibase configuration fails the build.
     */
    @Test
    void contextLoads() {
    }

}
