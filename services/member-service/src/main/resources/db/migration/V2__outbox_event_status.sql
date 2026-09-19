-- outbox_event now keeps sent rows for a retention window (7 days) instead of deleting them, so each
-- row carries an explicit status: PENDING -> SENT, or PENDING -> FAILED (needs a human).
alter table outbox_event add column status varchar(255);
update outbox_event set status = case when failed_at is null then 'PENDING' else 'FAILED' end;
alter table outbox_event alter column status set not null;
alter table outbox_event add constraint outbox_event_status_check
    check (status in ('PENDING', 'SENT', 'FAILED'));
alter table outbox_event add column sent_at timestamp(6) with time zone;

-- The relay polls PENDING rows in id order every second; SENT rows now make up most of the table.
create index outbox_event_pending_idx on outbox_event (id) where status = 'PENDING';
-- The hourly purge deletes SENT rows by age.
create index outbox_event_sent_at_idx on outbox_event (sent_at) where status = 'SENT';
