-- The parked-row gauge counts FAILED rows per queue on every relay tick. Without this it scans the
-- whole table, which is mostly SENT rows kept for 7 days.
create index outbox_event_failed_idx on outbox_event (queue) where status = 'FAILED';
