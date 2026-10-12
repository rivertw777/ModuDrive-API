-- Recorded but never read: the relay deduplicates by row id.
alter table outbox_event drop column message_key;
