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

-- test 유저 생성
WITH inserted AS (
    INSERT INTO member (id, name, email, password, is_valid, created_at, updated_at, is_deleted)
    VALUES (
        uuidv7(),
        'test',
        'test@naver.com',
        '$2b$10$3GYoSaQvZ7CONpKRijBv5eE8/bcIn1oAXQhI8/a3Ml9RVMjWSR44C',
        true,
        NOW(),
        NOW(),
        false
    )
    RETURNING id
),
role_inserted AS (
    INSERT INTO member_role (member_id, role)
    SELECT id, 'MEMBER' FROM inserted
)
INSERT INTO namespace (id, user_id, root_path, quota_bytes, created_at, updated_at)
SELECT uuidv7(), id, '/' || id, 21474836480, NOW(), NOW() FROM inserted;

-- test2 유저 생성
WITH inserted AS (
INSERT INTO member (id, name, email, password, is_valid, created_at, updated_at, is_deleted)
VALUES (
    uuidv7(),
    'test2',
    'test2@naver.com',
    '$2b$10$3GYoSaQvZ7CONpKRijBv5eE8/bcIn1oAXQhI8/a3Ml9RVMjWSR44C',
    true,
    NOW(),
    NOW(),
    false
    )
    RETURNING id
    ),
    role_inserted AS (
INSERT INTO member_role (member_id, role)
SELECT id, 'MEMBER' FROM inserted
    )
INSERT INTO namespace (id, user_id, root_path, quota_bytes, created_at, updated_at)
SELECT uuidv7(), id, '/' || id, 21474836480, NOW(), NOW() FROM inserted;