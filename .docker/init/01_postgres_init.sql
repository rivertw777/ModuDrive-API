CREATE TABLE IF NOT EXISTS member (
    id         UUID         PRIMARY KEY,
    name       VARCHAR(255),
    email      VARCHAR(255),
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

-- File sharing: viewer/editor roles, the permissions each grants, and the grant matrix between
-- them. There is no OWNER role — ownership is file.owner_id, checked directly rather than granted.
CREATE TABLE IF NOT EXISTS file_role (
    id        UUID         PRIMARY KEY,
    role_name VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS file_permission (
    id              UUID         PRIMARY KEY,
    permission_name VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS file_role_permission (
    file_role_id       UUID NOT NULL REFERENCES file_role(id),
    file_permission_id UUID NOT NULL REFERENCES file_permission(id),
    PRIMARY KEY (file_role_id, file_permission_id)
);

-- 파일 역할 - 권한 생성
WITH viewer_role AS (
    INSERT INTO file_role (id, role_name) VALUES (gen_random_uuid(), 'VIEWER') RETURNING id
),
editor_role AS (
    INSERT INTO file_role (id, role_name) VALUES (gen_random_uuid(), 'EDITOR') RETURNING id
),
read_permission AS (
    INSERT INTO file_permission (id, permission_name) VALUES (gen_random_uuid(), 'READ') RETURNING id
),
download_permission AS (
    INSERT INTO file_permission (id, permission_name) VALUES (gen_random_uuid(), 'DOWNLOAD') RETURNING id
),
rename_permission AS (
    INSERT INTO file_permission (id, permission_name) VALUES (gen_random_uuid(), 'RENAME') RETURNING id
)
INSERT INTO file_role_permission (file_role_id, file_permission_id)
SELECT viewer_role.id, read_permission.id FROM viewer_role, read_permission
UNION ALL
SELECT viewer_role.id, download_permission.id FROM viewer_role, download_permission
UNION ALL
SELECT editor_role.id, read_permission.id FROM editor_role, read_permission
UNION ALL
SELECT editor_role.id, download_permission.id FROM editor_role, download_permission
UNION ALL
SELECT editor_role.id, rename_permission.id FROM editor_role, rename_permission;

-- test 유저 생성
WITH inserted AS (
    INSERT INTO member (id, name, email, password, is_valid, created_at, updated_at, is_deleted)
    VALUES (
        gen_random_uuid(),
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
SELECT gen_random_uuid(), id, '/' || id, 21474836480, NOW(), NOW() FROM inserted;

-- test2 유저 생성
WITH inserted AS (
INSERT INTO member (id, name, email, password, is_valid, created_at, updated_at, is_deleted)
VALUES (
    gen_random_uuid(),
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
SELECT gen_random_uuid(), id, '/' || id, 21474836480, NOW(), NOW() FROM inserted;