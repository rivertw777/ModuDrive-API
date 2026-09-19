-- Why a row became FAILED (exception class + message, truncated), so it can be diagnosed from the table
-- instead of digging the relay's logs by failed_at.
alter table outbox_event add column failure_reason varchar(1000);
