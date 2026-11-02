package com.money.split.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.money.split.support.MySqlIntegrationTest;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

/**
 * Verifies the initial Liquibase schema and currency catalog against MySQL 8.4.
 */
class LiquibaseMigrationIntegrationTest extends MySqlIntegrationTest {

    private static final String TEST_ISSUER = "https://identity.example.com";

    /**
     * Verifies that a fresh migration creates every table needed by the group foundation.
     */
    @Test
    void createsTheFoundationTables() {
        // MySQL metadata proves the physical schema exists independently of future repository mappings.
        List<String> tables = jdbcClient.sql("""
                        SELECT table_name
                        FROM information_schema.tables
                        WHERE table_schema = DATABASE()
                          AND table_name IN ('app_user', 'currency', 'split_group', 'group_member', 'audit_event')
                        ORDER BY table_name
                        """)
                .query(String.class)
                .list();

        assertThat(tables)
                .containsExactly("app_user", "audit_event", "currency", "group_member", "split_group");
    }

    /**
     * Verifies the exact supported currency set, names, activity state, and minor-unit precision.
     */
    @Test
    void seedsTheApprovedCurrencyCatalog() {
        List<CurrencyMetadata> currencies = jdbcClient.sql("""
                        SELECT code, name, fraction_digits, active
                        FROM currency
                        ORDER BY code
                        """)
                .query(CurrencyMetadata.class)
                .list();

        assertThat(currencies)
                .containsExactly(
                        new CurrencyMetadata("AED", "UAE Dirham", 2, true),
                        new CurrencyMetadata("AUD", "Australian Dollar", 2, true),
                        new CurrencyMetadata("BTN", "Bhutanese Ngultrum", 2, true),
                        new CurrencyMetadata("CAD", "Canadian Dollar", 2, true),
                        new CurrencyMetadata("EUR", "Euro", 2, true),
                        new CurrencyMetadata("GBP", "Pound Sterling", 2, true),
                        new CurrencyMetadata("IDR", "Indonesian Rupiah", 2, true),
                        new CurrencyMetadata("INR", "Indian Rupee", 2, true),
                        new CurrencyMetadata("JPY", "Japanese Yen", 0, true),
                        new CurrencyMetadata("LKR", "Sri Lankan Rupee", 2, true),
                        new CurrencyMetadata("MYR", "Malaysian Ringgit", 2, true),
                        new CurrencyMetadata("NPR", "Nepalese Rupee", 2, true),
                        new CurrencyMetadata("SGD", "Singapore Dollar", 2, true),
                        new CurrencyMetadata("THB", "Thai Baht", 2, true),
                        new CurrencyMetadata("USD", "US Dollar", 2, true),
                        new CurrencyMetadata("VND", "Vietnamese Dong", 0, true));
    }

    /**
     * Verifies that OIDC identities distinguish case while rejecting an exact issuer/subject duplicate.
     */
    @Test
    void enforcesCaseSensitiveIssuerAndSubjectUniqueness() {
        Instant now = Instant.parse("2026-11-02T10:00:00Z");
        insertUser(UUID.randomUUID(), "Subject", now);
        insertUser(UUID.randomUUID(), "subject", now);

        assertThatThrownBy(() ->
                insertUser(UUID.randomUUID(), "Subject", now))
                .isInstanceOf(DuplicateKeyException.class);
    }

    /**
     * Verifies that membership stores lifecycle only and cannot introduce implicit privilege.
     */
    @Test
    void excludesRoleAndAdministrativeColumnsFromMembership() {
        assertThat(jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM information_schema.columns
                        WHERE table_schema = DATABASE()
                          AND table_name = 'group_member'
                          AND column_name IN ('role', 'is_admin', 'is_owner')
                        """)
                .query(Long.class)
                .single())
                .isZero();
    }

    /**
     * Verifies that new groups start with zero ledger, synchronization, and optimistic-lock versions.
     */
    @Test
    void initializesGroupVersionsAtZero() {
        Instant now = Instant.parse("2026-11-02T10:00:00Z");
        UUID userId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        insertUser(userId, "subject", now);
        insertGroup(groupId, userId, "USD", now);

        GroupVersions versions = jdbcClient.sql("""
                        SELECT ledger_version, sync_sequence, version
                        FROM split_group
                        WHERE id = UUID_TO_BIN(:groupId)
                        """)
                .param("groupId", groupId.toString())
                .query(GroupVersions.class)
                .single();

        assertThat(versions).isEqualTo(new GroupVersions(0, 0, 0));
    }

    /**
     * Verifies that a group cannot reference a currency outside the approved catalog.
     */
    @Test
    void rejectsUnsupportedGroupCurrency() {
        Instant now = Instant.parse("2026-11-02T10:00:00Z");
        UUID userId = UUID.randomUUID();
        insertUser(userId, "subject", now);

        assertThatThrownBy(() -> insertGroup(UUID.randomUUID(), userId, "XYZ", now))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * Inserts an application identity using MySQL's compact UUID representation.
     *
     * @param id      durable application user identifier
     * @param subject issuer-scoped OIDC subject
     * @param now     deterministic creation and update timestamp
     */
    private void insertUser(UUID id, String subject, Instant now) {
        jdbcClient.sql("""
                        INSERT INTO app_user
                            (id, issuer, subject, display_name, email, created_at, updated_at)
                        VALUES
                            (UUID_TO_BIN(:id), :issuer, :subject, 'User', NULL, :now, :now)
                        """)
                .param("id", id.toString())
                .param("issuer", TEST_ISSUER)
                .param("subject", subject)
                .param("now", Timestamp.from(now))
                .update();
    }

    /**
     * Inserts a group while allowing the database defaults to initialize all version counters.
     *
     * @param id           group identifier
     * @param creatorId    existing application user identifier
     * @param currencyCode catalog currency code
     * @param now          deterministic creation and update timestamp
     */
    private void insertGroup(UUID id, UUID creatorId, String currencyCode, Instant now) {
        jdbcClient.sql("""
                        INSERT INTO split_group
                            (id, name, base_currency, timezone, status, created_by, created_at, updated_at)
                        VALUES
                            (UUID_TO_BIN(:id), 'Schema Test', :currencyCode, 'UTC', 'ACTIVE',
                             UUID_TO_BIN(:creatorId), :now, :now)
                        """)
                .param("id", id.toString())
                .param("creatorId", creatorId.toString())
                .param("currencyCode", currencyCode)
                .param("now", Timestamp.from(now))
                .update();
    }

    /**
     * Currency row projected from the versioned seed data.
     *
     * @param code           ISO-style three-letter code
     * @param name           display name
     * @param fractionDigits supported minor-unit scale
     * @param active         whether the currency is available to new groups
     */
    private record CurrencyMetadata(String code, String name, int fractionDigits, boolean active) {
    }

    /**
     * Database-managed counters assigned to a newly inserted group.
     *
     * @param ledgerVersion financial ledger version
     * @param syncSequence  ordered synchronization sequence
     * @param version       optimistic-lock version
     */
    private record GroupVersions(long ledgerVersion, long syncSequence, long version) {
    }
}
