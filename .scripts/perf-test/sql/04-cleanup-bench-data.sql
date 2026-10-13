-- 벤치가 만든 측정용 드라이브와 그 하위 데이터를 전부 지움 (scenario.sh cleanup)
-- 벤치 스크립트는 namespace.quota_bytes로 구분한다: 1e15(cascade/contend), 1e12(perm), 10MiB·1.5MiB(quota/run.sh).
-- quota/bulk.sh의 드라이브는 실제 기본 한도(20GiB)와 같아서 여기 안 걸린다 — bulk-users 파일의 user_id로 따로 지울 것.
-- quota/race.py가 올린 블록의 S3 객체는 남는다(로컬 LocalStack은 재시작하면 비워짐).
-- 실제 사용자 드라이브의 quota와 겹치지 않는지 먼저 확인:  select quota_bytes, count(*) from namespace group by 1;
begin;
create temp table bn as select id, user_id from namespace where quota_bytes in (1000000000000000, 1000000000000, 10485760, 1572864);
create temp table bf as select f.id from file f join bn on bn.id = f.namespace_id;
delete from file_version_block where version_id in (select v.id from file_version v join bf on bf.id = v.file_id);
delete from file_version where file_id in (select id from bf);
delete from file_share where file_id in (select id from bf);
delete from file_favorite where file_id in (select id from bf);
delete from file_access where file_id in (select id from bf);
delete from block where owner_id in (select user_id from bn);
delete from file where id in (select id from bf);
delete from namespace where id in (select id from bn);
commit;
