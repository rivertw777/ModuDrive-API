-- The services now run on Korean time (Dockerfile: -Duser.timezone=Asia/Seoul), so `timestamp` columns
-- hold KST wall time from here on. Rows written before held UTC wall time: shift them by the same 9 hours.
-- timestamptz columns (outbox_event, processed_event, shedlock) are absolute and stay as they are.
update namespace set created_at = created_at + interval '9 hours',
                     updated_at = updated_at + interval '9 hours',
                     deleted_at = deleted_at + interval '9 hours';
update file set trashed_at = trashed_at + interval '9 hours',
                created_at = created_at + interval '9 hours',
                updated_at = updated_at + interval '9 hours',
                deleted_at = deleted_at + interval '9 hours';
update file_version set created_at = created_at + interval '9 hours';
update file_share set created_at = created_at + interval '9 hours';
update file_access set accessed_at = accessed_at + interval '9 hours',
                       created_at = created_at + interval '9 hours';
update file_favorite set created_at = created_at + interval '9 hours';
update block set unreferenced_at = unreferenced_at + interval '9 hours';
