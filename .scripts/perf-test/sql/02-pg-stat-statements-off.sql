-- 측정 끝나면 원복. scenario.sh down이 file_db의 확장을 지운 뒤 실행하고 postgres를 재시작한다.
alter system reset shared_preload_libraries;
alter system reset pg_stat_statements.track_utility;
