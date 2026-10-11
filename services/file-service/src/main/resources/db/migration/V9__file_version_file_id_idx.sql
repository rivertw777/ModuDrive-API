-- Every per-file version lookup (download, revisions list, purge) and the version-retention sweep
-- filter file_version by file_id, which had no index — each was a scan of every version of every file.
-- Concurrently: file_version is the largest table, and a plain create index blocks commits while it builds.
create index concurrently if not exists ix_file_version_file_id_created_at on file_version (file_id, created_at);
