-- 측정 중 끝나지 않은 서버 쪽 트랜잭션 확인 (클라이언트 타임아웃 뒤에도 서버는 계속 돌 수 있음)
-- 다음 측정 전에 0행이 될 때까지 기다릴 것 (scenario.sh wait)
select pid, state, now() - xact_start as running, left(query, 100) as query
from pg_stat_activity
where usename = 'file_service' and state <> 'idle' and now() - xact_start > interval '5 seconds';
