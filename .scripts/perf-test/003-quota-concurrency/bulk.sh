#!/bin/bash
# .docs/perf-test/003-quota-concurrency.md
# bulk.sh <userId> <files>  — a namespace (20GiB quota) holding, under /bulk, <files> uploaded 1MiB file rows
docker exec -i modudrive-infra-postgres-1 sh -c 'psql -qtA -U $POSTGRES_USER -d file_db' <<SQL
insert into namespace(id,user_id,root_path,quota_bytes,created_at,updated_at,is_deleted)
  values (gen_random_uuid(),'$1','/$1',21474836480,now(),now(),false);
update namespace n set used_bytes = $2::bigint * 1048576 where user_id = '$1';
insert into file(id,namespace_id,owner_id,name,path,is_directory,file_size,status,active_slot_name,access_scope,created_at,updated_at,is_deleted)
  select gen_random_uuid(), n.id, '$1', 'b'||g, '/bulk', false, 1048576, 'UPLOADED', 'b'||g, 'RESTRICTED', now(), now(), false
  from namespace n, generate_series(1,$2) g where n.user_id='$1';
analyze file;
SQL
