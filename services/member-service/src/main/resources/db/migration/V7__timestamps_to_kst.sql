-- The services now run on Korean time (Dockerfile: -Duser.timezone=Asia/Seoul), so `timestamp` columns
-- hold KST wall time from here on. Rows written before held UTC wall time: shift them by the same 9 hours.
-- timestamptz columns (outbox_event) are absolute and stay as they are.
update member set created_at = created_at + interval '9 hours',
                  updated_at = updated_at + interval '9 hours',
                  deleted_at = deleted_at + interval '9 hours';
