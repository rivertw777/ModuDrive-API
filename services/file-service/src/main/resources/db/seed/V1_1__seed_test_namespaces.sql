-- dev only (see application-jpa.yml). Namespaces for member-service's seeded test / test2 (20 GiB quota).
insert into namespace (id, user_id, root_path, quota_bytes, created_at, updated_at, is_deleted) values
    (uuidv7(), '0199a1b2-0000-7000-8000-000000000001', '/0199a1b2-0000-7000-8000-000000000001', 21474836480, now(), now(), false),
    (uuidv7(), '0199a1b2-0000-7000-8000-000000000002', '/0199a1b2-0000-7000-8000-000000000002', 21474836480, now(), now(), false);
