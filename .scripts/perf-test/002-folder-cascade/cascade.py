"""Folder cascade benchmark (.docs/perf-test/002-folder-cascade.md): move / trash / restore / purge
of a folder with N files — SQL count (pg_stat_statements) and latency. Its seed() is shared by this folder's scripts.
Run through scenario.sh, or inside the bench container: python cascade.py <label> [sizes...]"""
import json, os, sys, threading, time, uuid, statistics
import psycopg
from bench import DB, http, reset_stats, read_stats


def seed(cur, n):
    ns, owner = uuid.uuid4(), uuid.uuid4()
    cur.execute("insert into namespace(id,user_id,root_path,quota_bytes,used_bytes,created_at,updated_at,is_deleted) "
                "values (%s,%s,'/',1e15,%s,now(),now(),false)", (ns, owner, n * 3000))
    cols = "id,namespace_id,owner_id,name,path,is_directory,file_size,current_version_id,status,active_slot_name,access_scope,created_at,updated_at,is_deleted"
    root = uuid.uuid4()
    cur.execute(f"insert into file({cols}) values (%s,%s,%s,'bench','/',true,null,null,'UPLOADED','bench','RESTRICTED',now(),now(),false),"
                f"(gen_random_uuid(),%s,%s,'dest','/',true,null,null,'UPLOADED','dest','RESTRICTED',now(),now(),false)",
                (root, ns, owner, ns, owner))
    cur.execute(f"insert into file({cols}) select gen_random_uuid(),%s,%s,'d'||g,'/bench',true,null,null,'UPLOADED','d'||g,'RESTRICTED',now(),now(),false "
                "from generate_series(0,9) g", (ns, owner))
    cur.execute("create temp table t on commit drop as select gen_random_uuid() fid, gen_random_uuid() vid, g from generate_series(0,%s) g", (n - 1,))
    cur.execute(f"insert into file({cols}) select fid,%s,%s,'f'||g,'/bench/d'||(g%%10),false,3000,vid,'UPLOADED','f'||g,'RESTRICTED',now(),now(),false from t", (ns, owner))
    cur.execute("insert into file_version(id,file_id,file_size,created_at,owner_id,upload_id) select vid,fid,3000,now(),%s,gen_random_uuid() from t", (owner,))
    cur.execute("insert into file_version_block(version_id,idx,hash) select vid,k,md5(fid::text||k)||md5(k||fid::text) from t, generate_series(0,2) k")
    cur.execute("insert into block(owner_id,hash,size,ref_count) select %s,hash,1000,1 from file_version_block b join t on t.vid=b.version_id", (owner,))
    return ns, owner, root


def commit_empty(user, path):
    body = {"files": [{"path": path, "name": f"c-{uuid.uuid4().hex[:8]}.txt", "uploadId": str(uuid.uuid4()), "size": 0, "blocklist": []}]}
    return http("POST", "/api/v1/files/commit", user, body)


def op(cur, name, user, fid, contend=None):
    """Runs one cascade op; contend=(delay_ms, path) fires an empty commit into the same drive meanwhile."""
    calls = {"move": ("PATCH", f"/api/v1/files/{fid}/path", {"path": "/dest"}),
             "trash": ("DELETE", f"/api/v1/files/{fid}", None),
             "restore": ("PATCH", f"/api/v1/files/{fid}/restore", None),
             "purge": ("DELETE", f"/api/v1/files/{fid}/purge", None)}
    m, p, b = calls[name]
    reset_stats(cur)
    side = {}
    if contend:
        def fire():
            time.sleep(contend[0] / 1000)
            s, ms, _ = commit_empty(user, contend[1])
            side.update(status=s, ms=ms)
        th = threading.Thread(target=fire); th.start()
    status, ms, payload = http(m, p, user, b)
    if contend:
        th.join()
    assert status < 300, (name, status, payload[:300])
    time.sleep(0.3)  # let pg_stat_statements see the last statement
    total, top = read_stats(cur)
    print(f"    {name}: {ms:.0f}ms sql={total} side={side}", flush=True)
    return {"ms": ms, "sql": total, "top": top, "side": side}


def main():
    label = sys.argv[1]
    sizes = [int(x) for x in sys.argv[2:]] or [100, 1000, 10000]
    rounds = int(os.environ.get("ROUNDS", "3"))
    out = {}
    with psycopg.connect(DB, autocommit=True) as conn, conn.cursor() as cur:
        for n in sizes:
            res = {k: [] for k in ("move", "trash", "restore", "purge", "purge_wait", "move_wait")}
            for r in range(rounds):
                with conn.transaction():
                    ns, owner, root = seed(cur, n)
                for k in ("move", "trash", "restore"):
                    res[k].append(op(cur, k, owner, root))
                res["trash"].append(op(cur, "trash", owner, root))
                res["purge"].append(op(cur, "purge", owner, root))
                if os.environ.get("SKIP_WAIT"):
                    continue
                # Contention: a same-drive commit at the root while a purge runs; a commit into a
                # subfolder while the folder moves.
                with conn.transaction():
                    ns, owner, root = seed(cur, n)
                op(cur, "trash", owner, root)
                res["purge_wait"].append(op(cur, "purge", owner, root, contend=(res["purge"][-1]["ms"] / 2, "/")))
                with conn.transaction():
                    ns, owner, root = seed(cur, n)
                res["move_wait"].append(op(cur, "move", owner, root, contend=(res["move"][-1]["ms"] / 2, "/bench/d3")))
                cur.execute("select n.used_bytes, coalesce((select sum(file_size) from file f where f.namespace_id=n.id and not is_directory and deleted_at is null),0) from namespace n where id=%s", (ns,))
                print("  usage check", cur.fetchone(), flush=True)
            summary = {}
            for k, v in res.items():
                if not v:
                    continue
                summary[k] = {"ms_median": statistics.median(x["ms"] for x in v), "ms_all": [round(x["ms"]) for x in v],
                              "sql": [x["sql"] for x in v], "side_ms": [round(x["side"].get("ms", 0)) for x in v],
                              "side_status": [x["side"].get("status") for x in v]}
            summary["purge_top"] = res["purge"][0]["top"]
            summary["trash_top"] = res["trash"][0]["top"]
            summary["move_top"] = res["move"][0]["top"]
            out[n] = summary
            print(n, json.dumps({k: {kk: vv for kk, vv in v.items()} for k, v in summary.items() if not k.endswith("top")}, ensure_ascii=False), flush=True)
    json.dump(out, open(f"results/cascade-{label}.json", "w"), ensure_ascii=False, indent=1, default=str)


if __name__ == "__main__":
    main()
