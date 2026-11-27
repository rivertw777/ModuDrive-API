-- The services now run on Korean time (Dockerfile: -Duser.timezone=Asia/Seoul), so `timestamp` columns
-- hold KST wall time from here on. Rows written before held UTC wall time: shift them by the same 9 hours.
-- timestamptz columns (processed_event) are absolute and stay as they are.
update notification set read_at = read_at + interval '9 hours',
                        created_at = created_at + interval '9 hours';
