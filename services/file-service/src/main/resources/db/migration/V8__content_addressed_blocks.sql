-- A version is now the ordered list of its blocks' SHA-256 hashes, and a block is stored once per
-- owner at blocks/{owner_id}/{hash} however many versions share it (spec 008). The old per-upload
-- s3_path/block_count layout isn't migrated: a database that still holds versions fails on the
-- NOT NULL columns below, and has to be reset (make reset).
alter table file_version drop constraint uk_file_version_s3_path;
alter table file_version drop column s3_path;
alter table file_version drop column block_count;
alter table file_version add column owner_id uuid not null;
alter table file_version add column upload_id uuid not null;
-- A commit retried after its response was lost finds the version it already made.
alter table file_version add constraint uk_file_version_upload_id unique (upload_id);

create table file_version_block (
    version_id uuid        not null,
    idx        integer     not null,
    hash       varchar(64) not null,
    primary key (version_id, idx)
);

-- One row per committed block. ref_count = file_version_block rows pointing at it; once it drops to
-- 0, unreferenced_at starts the grace period after which the block is deleted.
create table block (
    owner_id        uuid        not null,
    hash            varchar(64) not null,
    size            integer     not null,
    ref_count       integer     not null,
    unreferenced_at timestamp(6),
    primary key (owner_id, hash)
);

create index idx_block_unreferenced on block (unreferenced_at) where ref_count = 0;
