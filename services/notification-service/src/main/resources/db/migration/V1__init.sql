create table notification (
    id           uuid not null,
    event_id     uuid not null,
    recipient_id uuid not null,
    file_id      uuid not null,
    file_name    varchar(255) not null,
    directory    boolean not null,
    role         varchar(255) not null,
    sharer_name  varchar(255),
    sharer_email varchar(255),
    read_at      timestamp(6),
    created_at   timestamp(6) not null,
    created_by   uuid,
    primary key (id),
    constraint uk_notification_event_id unique (event_id)
);
