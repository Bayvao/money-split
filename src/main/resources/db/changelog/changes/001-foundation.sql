--liquibase formatted sql

--changeset money-split:001-foundation
-- UUIDs use BINARY(16) to keep primary and foreign-key indexes compact.
-- Binary collation makes the OIDC issuer/subject identity key case-sensitive.
CREATE TABLE app_user (
    id BINARY(16) NOT NULL,
    issuer VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    subject VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    email VARCHAR(320) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_app_user PRIMARY KEY (id),
    CONSTRAINT uk_app_user_identity UNIQUE (issuer, subject)
);

-- The application supports only this versioned catalog; no runtime host locale can add currencies.
CREATE TABLE currency (
    code CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name VARCHAR(80) NOT NULL,
    fraction_digits TINYINT UNSIGNED NOT NULL,
    active BOOLEAN NOT NULL,
    CONSTRAINT pk_currency PRIMARY KEY (code),
    CONSTRAINT chk_currency_code CHECK (code REGEXP '^[A-Z]{3}$'),
    CONSTRAINT chk_currency_fraction_digits CHECK (fraction_digits <= 3)
);

-- Fraction digits define the minor-unit scale used by later money calculations.
INSERT INTO currency (code, name, fraction_digits, active) VALUES
    ('AED', 'UAE Dirham', 2, TRUE),
    ('AUD', 'Australian Dollar', 2, TRUE),
    ('BTN', 'Bhutanese Ngultrum', 2, TRUE),
    ('CAD', 'Canadian Dollar', 2, TRUE),
    ('EUR', 'Euro', 2, TRUE),
    ('GBP', 'Pound Sterling', 2, TRUE),
    ('IDR', 'Indonesian Rupiah', 2, TRUE),
    ('INR', 'Indian Rupee', 2, TRUE),
    ('JPY', 'Japanese Yen', 0, TRUE),
    ('LKR', 'Sri Lankan Rupee', 2, TRUE),
    ('MYR', 'Malaysian Ringgit', 2, TRUE),
    ('NPR', 'Nepalese Rupee', 2, TRUE),
    ('SGD', 'Singapore Dollar', 2, TRUE),
    ('THB', 'Thai Baht', 2, TRUE),
    ('USD', 'US Dollar', 2, TRUE),
    ('VND', 'Vietnamese Dong', 0, TRUE);

-- Ledger, synchronization, and optimistic-lock counters begin at zero for every group.
CREATE TABLE split_group (
    id BINARY(16) NOT NULL,
    name VARCHAR(120) NOT NULL,
    base_currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_by BINARY(16) NOT NULL,
    ledger_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    sync_sequence BIGINT UNSIGNED NOT NULL DEFAULT 0,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_split_group PRIMARY KEY (id),
    CONSTRAINT fk_split_group_currency FOREIGN KEY (base_currency) REFERENCES currency (code),
    CONSTRAINT fk_split_group_creator FOREIGN KEY (created_by) REFERENCES app_user (id),
    CONSTRAINT chk_split_group_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT chk_split_group_name CHECK (CHAR_LENGTH(TRIM(name)) BETWEEN 1 AND 120)
);

CREATE INDEX ix_split_group_creator ON split_group (created_by, created_at);

-- Membership intentionally has no owner, administrator, or role column.
CREATE TABLE group_member (
    group_id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    joined_at DATETIME(6) NOT NULL,
    left_at DATETIME(6) NULL,
    CONSTRAINT pk_group_member PRIMARY KEY (group_id, user_id),
    CONSTRAINT fk_group_member_group FOREIGN KEY (group_id) REFERENCES split_group (id),
    CONSTRAINT fk_group_member_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT chk_group_member_status CHECK (status IN ('ACTIVE', 'LEFT')),
    CONSTRAINT chk_group_member_departure CHECK (
        (status = 'ACTIVE' AND left_at IS NULL)
        OR (status = 'LEFT' AND left_at IS NOT NULL)
    )
);

-- This index supports future authorization queries beginning with the authenticated user and status.
CREATE INDEX ix_group_member_user_status ON group_member (user_id, status, group_id);

-- Audit rows retain actor and request provenance without storing sensitive request payloads.
CREATE TABLE audit_event (
    id BINARY(16) NOT NULL,
    actor_user_id BINARY(16) NOT NULL,
    resource_type VARCHAR(40) NOT NULL,
    resource_id BINARY(16) NOT NULL,
    action VARCHAR(60) NOT NULL,
    request_id BINARY(16) NOT NULL,
    resource_version BIGINT UNSIGNED NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_audit_event PRIMARY KEY (id),
    CONSTRAINT fk_audit_event_actor FOREIGN KEY (actor_user_id) REFERENCES app_user (id)
);

-- Resource-first ordering supports chronological audit history for one entity.
CREATE INDEX ix_audit_event_resource
    ON audit_event (resource_type, resource_id, occurred_at, id);
CREATE INDEX ix_audit_event_request ON audit_event (request_id);

--rollback DROP TABLE audit_event;
--rollback DROP TABLE group_member;
--rollback DROP TABLE split_group;
--rollback DROP TABLE currency;
--rollback DROP TABLE app_user;
