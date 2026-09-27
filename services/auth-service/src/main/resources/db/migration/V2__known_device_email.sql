-- The email the member signed in with, so the login attempt limit can tell a known device from the
-- email before the password is checked (spec 004 2-1). Lowercased. Null on rows from before this
-- column; filled on that device's next login.
alter table known_device add column email varchar(320);

create index known_device_email_idx on known_device (email, device_hash);
