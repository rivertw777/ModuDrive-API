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

WITH inserted AS (
    INSERT INTO member (id, name, email, password, is_valid, created_at, updated_at, is_deleted)
    VALUES (
        gen_random_uuid(),
        'taewon',
        'rivertw@naver.com',
        '$2b$10$3GYoSaQvZ7CONpKRijBv5eE8/bcIn1oAXQhI8/a3Ml9RVMjWSR44C',
        true,
        NOW(),
        NOW(),
        false
    )
    RETURNING id
)
INSERT INTO member_role (member_id, role)
SELECT id, 'MEMBER' FROM inserted;
