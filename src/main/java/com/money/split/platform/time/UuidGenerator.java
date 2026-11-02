package com.money.split.platform.time;

import java.util.UUID;

/**
 * Boundary for generating UUIDs that require deterministic tests.
 */
@FunctionalInterface
public interface UuidGenerator {

    /**
     * Generates a new UUID.
     *
     * @return generated UUID
     */
    UUID generate();
}
