-- dev only (see application-jpa.yml). Fixed ids: file-service seeds their namespaces in another database.
-- Password for both: bcrypt of the shared local test password.
insert into member (id, name, email, password, is_valid, created_at, updated_at, is_deleted) values
    ('0199a1b2-0000-7000-8000-000000000001', 'test',  'test@naver.com',  '$2b$10$3GYoSaQvZ7CONpKRijBv5eE8/bcIn1oAXQhI8/a3Ml9RVMjWSR44C', true, now(), now(), false),
    ('0199a1b2-0000-7000-8000-000000000002', 'test2', 'test2@naver.com', '$2b$10$3GYoSaQvZ7CONpKRijBv5eE8/bcIn1oAXQhI8/a3Ml9RVMjWSR44C', true, now(), now(), false);

insert into member_role (member_id, role) values
    ('0199a1b2-0000-7000-8000-000000000001', 'MEMBER'),
    ('0199a1b2-0000-7000-8000-000000000002', 'MEMBER');
