#!/bin/sh
# One database + one login per service; each login can only connect to its own database.
# Runs only on an empty postgres volume — `make reset` to apply it to an existing one.
set -eu

create_db() { # <db> <role> <password>
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres -v pw="$3" <<SQL
CREATE ROLE $2 LOGIN PASSWORD :'pw';
CREATE DATABASE $1 OWNER $2;
REVOKE ALL ON DATABASE $1 FROM PUBLIC;
SQL
}

create_db member_db member_service "$MEMBER_DB_PASSWORD"
create_db file_db file_service "$FILE_DB_PASSWORD"
create_db notification_db notification_service "$NOTIFICATION_DB_PASSWORD"

# Seed users test / test2. Fixed ids because member and namespace live in different databases.
# Tables are created under SET ROLE so the service role owns them and ddl-auto=update can alter them.
TEST_ID=0199a1b2-0000-7000-8000-000000000001
TEST2_ID=0199a1b2-0000-7000-8000-000000000002
PASSWORD_HASH='$2b$10$3GYoSaQvZ7CONpKRijBv5eE8/bcIn1oAXQhI8/a3Ml9RVMjWSR44C'

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname member_db -v hash="$PASSWORD_HASH" <<SQL
SET ROLE member_service;

CREATE TABLE IF NOT EXISTS member (
    id         UUID         PRIMARY KEY,
    name       VARCHAR(255),
    email      VARCHAR(255) CONSTRAINT uk_member_email UNIQUE,
    password   VARCHAR(255),
    is_valid   BOOLEAN,
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6),
    deleted_at TIMESTAMP(6),
    is_deleted BOOLEAN
);

CREATE TABLE IF NOT EXISTS member_role (
    member_id UUID         NOT NULL REFERENCES member(id),
    role      VARCHAR(255)
);

INSERT INTO member (id, name, email, password, is_valid, created_at, updated_at, is_deleted) VALUES
    ('$TEST_ID',  'test',  'test@naver.com',  :'hash', true, NOW(), NOW(), false),
    ('$TEST2_ID', 'test2', 'test2@naver.com', :'hash', true, NOW(), NOW(), false);

INSERT INTO member_role (member_id, role) VALUES ('$TEST_ID', 'MEMBER'), ('$TEST2_ID', 'MEMBER');
SQL

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname file_db <<SQL
SET ROLE file_service;

CREATE TABLE IF NOT EXISTS namespace (
    id          UUID         PRIMARY KEY,
    user_id     UUID         NOT NULL UNIQUE,
    root_path   VARCHAR(255) NOT NULL,
    quota_bytes BIGINT       NOT NULL,
    created_at  TIMESTAMP(6),
    updated_at  TIMESTAMP(6)
);

-- File sharing: viewer/editor roles and the permissions each grants live in the Role enum
-- (com.moduDrive.file.domain.model.Role), stored directly as file_share.granted_role — same
-- pattern as file.link_role. There is no OWNER role — ownership is file.owner_id, checked
-- directly rather than granted.

INSERT INTO namespace (id, user_id, root_path, quota_bytes, created_at, updated_at) VALUES
    (uuidv7(), '$TEST_ID',  '/$TEST_ID',  21474836480, NOW(), NOW()),
    (uuidv7(), '$TEST2_ID', '/$TEST2_ID', 21474836480, NOW(), NOW());
SQL
