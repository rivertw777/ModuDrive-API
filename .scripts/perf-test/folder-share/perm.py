"""Shared-folder permission benchmark (.docs/perf-test/002-share-permission.md): a grantee opening / listing
files under a folder shared only at the top of a depth-d chain. python perm.py <label>"""
import json, sys, statistics, uuid, time
import psycopg
from cascade import DB, http, reset_stats, read_stats

COLS = "id,namespace_id,owner_id,name,path,is_directory,file_size,status,active_slot_name,access_scope,created_at,updated_at,is_deleted"
REPS = 200


def seed(cur, depth, files):
    ns, owner, grantee = uuid.uuid4(), uuid.uuid4(), uuid.uuid4()
    cur.execute("insert into namespace(id,user_id,root_path,quota_bytes,used_bytes,created_at,updated_at,is_deleted) "
                "values (%s,%s,'/',1e12,0,now(),now(),false),(gen_random_uuid(),%s,'/',1e12,0,now(),now(),false)", (ns, owner, grantee))
    path, dirs = "/", []
    for i in range(depth):
        d = uuid.uuid4()
        cur.execute(f"insert into file({COLS}) values (%s,%s,%s,%s,%s,true,null,'UPLOADED',%s,'RESTRICTED',now(),now(),false)",
                    (d, ns, owner, f"p{i}", path, f"p{i}"))
        dirs.append(d)
        path = f"/p{i}" if path == "/" else f"{path}/p{i}"
    ids = [uuid.uuid4() for _ in range(files)]
    for k, f in enumerate(ids):
        cur.execute(f"insert into file({COLS}) values (%s,%s,%s,%s,%s,false,10,'UPLOADED',%s,'RESTRICTED',now(),now(),false)",
                    (f, ns, owner, f"f{k}.txt", path, f"f{k}.txt"))
    # The top folder is shared with the grantee; everything below inherits it.
    cur.execute("insert into file_share(id,file_id,owner_id,shared_with_user_id,granted_role,created_at) values (gen_random_uuid(),%s,%s,%s,'VIEWER',now())",
                (dirs[0], owner, grantee))
    for f in ids:
        cur.execute("insert into file_favorite(id,user_id,file_id,created_at) values (gen_random_uuid(),%s,%s,now())", (grantee, f))
        cur.execute("insert into file_access(id,user_id,file_id,accessed_at,created_at) values (gen_random_uuid(),%s,%s,now(),now())", (grantee, f))
    return grantee, dirs[-1], ids


def measure(cur, user, path):
    reset_stats(cur)
    s, _, body = http("GET", path, user)
    assert s == 200, (path, s, body[:300])
    time.sleep(0.3)
    sql, top = read_stats(cur)
    lat = sorted(http("GET", path, user)[1] for _ in range(REPS))
    return {"sql": sql, "p50": round(statistics.median(lat), 2), "p95": round(lat[int(REPS * 0.95)], 2), "top": top}


def main():
    label = sys.argv[1]
    out = {}
    with psycopg.connect(DB, autocommit=True) as conn, conn.cursor() as cur:
        for depth in (1, 5, 10, 20):
            with conn.transaction():
                grantee, deepest, ids = seed(cur, depth, 50)
            r = {"get_file": measure(cur, grantee, f"/api/v1/files/{ids[0]}"),
                 "list_folder_50": measure(cur, grantee, f"/api/v1/files/{deepest}/children"),
                 "favorites_50": measure(cur, grantee, "/api/v1/files/favorites"),
                 "recent_50": measure(cur, grantee, "/api/v1/files/recent?limit=50")}
            out[depth] = r
            print(depth, json.dumps({k: {kk: vv for kk, vv in v.items() if kk != "top"} for k, v in r.items()}), flush=True)
    json.dump(out, open(f"/w/results/perm-{label}.json", "w"), ensure_ascii=False, indent=1, default=str)


if __name__ == "__main__":
    main()
