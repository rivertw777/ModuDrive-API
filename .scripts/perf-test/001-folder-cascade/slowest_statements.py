"""Purges one 10k-file folder and prints the statements that took the most time — first step when a purge
is slow. Needs pg_stat_statements on."""
import psycopg, time
from cascade import DB, seed, http, reset_stats
with psycopg.connect(DB, autocommit=True) as conn, conn.cursor() as cur:
    with conn.transaction():
        ns, owner, root = seed(cur, 10000)
    http("DELETE", f"/api/v1/files/{root}", owner)
    reset_stats(cur)
    s, ms, _ = http("DELETE", f"/api/v1/files/{root}/purge", owner)
    print("purge", s, round(ms)); time.sleep(0.5)
    cur.execute("select round(total_exec_time::numeric,1), calls, left(regexp_replace(query,'\\s+',' ','g'),110) from pg_stat_statements s join pg_roles r on r.oid=s.userid where r.rolname='file_service' order by total_exec_time desc limit 8")
    for r in cur.fetchall(): print(r)
