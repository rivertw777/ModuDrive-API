-- A commit creates a file row and its first version in one transaction, so no row is committed as
-- PENDING any more. Rows the old upload flow left behind (never given a version) become tombstones.
update file
set status = 'DELETED', deleted_at = now(), is_deleted = true, active_slot_name = null
where status = 'PENDING';

alter table file drop constraint file_status_check;
alter table file add constraint file_status_check check (status in ('UPLOADED', 'TRASHED', 'DELETED'));
