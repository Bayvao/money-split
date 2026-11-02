package com.money.split.platform.time;

import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * Generates cryptographically strong random UUID values through the JDK.
 */
@Component
final class RandomUuidGenerator implements UuidGenerator {

    /**
     * {@inheritDoc}
     */
    @Override
    public UUID generate() {
        return UUID.randomUUID();
    }
}
