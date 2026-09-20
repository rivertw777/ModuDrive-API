-- The column has always held a queue name; `topic` was left over from Kafka and said the opposite of
-- what it holds (a topic fans out to every subscriber, these messages go to one consumer).
alter table outbox_event rename column topic to queue;
