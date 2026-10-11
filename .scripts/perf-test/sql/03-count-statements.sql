-- 요청 하나가 실행한 SQL 수 세기 (file_db, 슈퍼유저)
-- 1) 요청 직전
select pg_stat_statements_reset();
-- 2) 요청 보낸 뒤: file-service 역할의 문장별 호출 수 (백그라운드 outbox 릴레이 등은 제외)
select s.calls, round(s.total_exec_time::numeric, 1) as total_ms,
       left(regexp_replace(s.query, '\s+', ' ', 'g'), 120) as query
from pg_stat_statements s
join pg_roles r on r.oid = s.userid
join pg_database d on d.oid = s.dbid
where r.rolname = 'file_service' and d.datname = 'file_db'
  and s.query !~* 'outbox|shedlock|processed_event|pg_stat_statements'
order by s.calls desc;
-- 합계만: 위 쿼리를 select sum(calls) from (...) 로 감싸기
