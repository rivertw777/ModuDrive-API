-- SQL 실행 횟수 측정용: pg_stat_statements 켜기. scenario.sh up이 실행하고 postgres를 재시작한다.
alter system set shared_preload_libraries = 'pg_stat_statements';
alter system set pg_stat_statements.track_utility = off;   -- BEGIN/COMMIT/SET은 세지 않음
