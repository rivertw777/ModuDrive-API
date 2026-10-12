"""EXPLAIN ANALYZE of the purge's block-release statement on a fresh 10k-file tree (rolled back, leaves
nothing). Use when a purge is slow or unstable (.docs/perf-test/001-folder-cascade.md 3장)."""
import psycopg
from cascade import DB, seed
SQL = """explain (analyze, buffers off, timing off) with refs as (select v.owner_id, vb.hash, count(*) as cnt from file_version v
 join file_version_block vb on vb.version_id = v.id where v.file_id = any(%s) group by v.owner_id, vb.hash),
 locked as materialized (select b.owner_id, b.hash, r.cnt from block b join refs r on r.owner_id = b.owner_id and r.hash = b.hash order by b.owner_id, b.hash for update of b),
 released as (update block b set ref_count = b.ref_count - l.cnt, unreferenced_at = case when b.ref_count - l.cnt <= 0 then now() else b.unreferenced_at end
 from locked l where b.owner_id = l.owner_id and b.hash = l.hash returning 1)
 select (select count(*) from refs) - (select count(*) from released)"""
with psycopg.connect(DB) as conn, conn.cursor() as cur:
    ns, owner, root = seed(cur, 10000)
    cur.execute("select id from file where namespace_id=%s and not is_directory", (ns,))
    ids = [r[0] for r in cur.fetchall()]
    cur.execute(SQL, (ids,))
    for r in cur.fetchall(): print(r[0])
    conn.rollback()
