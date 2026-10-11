-- Folder move/trash/restore/purge pick a subtree with `path like '<prefix>/%'`. The unique index on
-- (namespace_id, path, active_slot_name) can't serve that under the en_US collation, so each one read
-- every file of the drive, however small the folder. varchar_pattern_ops compares bytewise, which lets
-- the planner turn the prefix into an index range (the pattern must be a plan-time constant: `||`, not
-- the stable concat()).
create index concurrently if not exists ix_file_namespace_path_prefix on file (namespace_id, path varchar_pattern_ops);
