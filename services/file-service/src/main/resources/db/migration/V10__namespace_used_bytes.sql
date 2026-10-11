-- The drive usage kept on the namespace row instead of summed over every file on each commit and usage
-- read: the commit's quota check becomes one conditional UPDATE on this row, which also serializes two
-- commits into the same drive (spec 001, commit checks). Backfilled with the very sum it replaces.
alter table namespace add column used_bytes bigint not null default 0;
update namespace n set used_bytes = coalesce((select sum(f.file_size) from file f
    where f.namespace_id = n.id and f.is_directory = false and f.status <> 'PENDING' and f.deleted_at is null), 0);
