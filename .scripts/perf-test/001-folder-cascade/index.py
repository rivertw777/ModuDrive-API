"""Path-prefix index benchmark (V11): the same folder delete as cascade.py (trash, then DELETE …/purge of a
folder with N files; both timed), but in a drive that also holds BACKGROUND other files, so a subtree query that
can't use the index pays for the whole drive. Run inside the bench container: python index.py <label> [sizes...]"""
import json, os, statistics, sys, uuid
import psycopg
from cascade import DB, op

BACKGROUND = int(os.environ.get("BACKGROUND", "1000000"))
OPS = os.environ.get("OPS", "trash,purge").split(",")  # trash-only for the per-file code: its 10k purge runs ~8 min
COLS = "id,namespace_id,owner_id,name,path,is_directory,file_size,current_version_id,status,active_slot_name,access_scope,created_at,updated_at,is_deleted"


def seed_drive(cur):
    ns, owner = uuid.uuid4(), uuid.uuid4()
    cur.execute("insert into namespace(id,user_id,root_path,quota_bytes,used_bytes,created_at,updated_at,is_deleted) "
                "values (%s,%s,'/',1e15,0,now(),now(),false)", (ns, owner))
    cur.execute(f"insert into file({COLS}) select gen_random_uuid(),%s,%s,'x'||g,'/bg/d'||(g%%1000),true,null,null,'UPLOADED','x'||g,'RESTRICTED',now(),now(),false "
                "from generate_series(1,%s) g", (ns, owner, BACKGROUND))
    return ns, owner


def seed_folder(cur, ns, owner, n, name):
    root = uuid.uuid4()
    cur.execute(f"insert into file({COLS}) values (%s,%s,%s,%s,'/',true,null,null,'UPLOADED',%s,'RESTRICTED',now(),now(),false)",
                (root, ns, owner, name, name))
    cur.execute(f"insert into file({COLS}) select gen_random_uuid(),%s,%s,'d'||g,%s,true,null,null,'UPLOADED','d'||g,'RESTRICTED',now(),now(),false "
                "from generate_series(0,9) g", (ns, owner, "/" + name))
    cur.execute("create temp table t on commit drop as select gen_random_uuid() fid, gen_random_uuid() vid, g from generate_series(0,%s) g", (n - 1,))
    cur.execute(f"insert into file({COLS}) select fid,%s,%s,'f'||g,%s||'/d'||(g%%10),false,3000,vid,'UPLOADED','f'||g,'RESTRICTED',now(),now(),false from t",
                (ns, owner, "/" + name))
    cur.execute("insert into file_version(id,file_id,file_size,created_at,owner_id,upload_id) select vid,fid,3000,now(),%s,gen_random_uuid() from t", (owner,))
    cur.execute("insert into file_version_block(version_id,idx,hash) select vid,k,md5(fid::text||k)||md5(k||fid::text) from t, generate_series(0,2) k")
    cur.execute("insert into block(owner_id,hash,size,ref_count) select %s,hash,1000,1 from file_version_block b join t on t.vid=b.version_id", (owner,))
    cur.execute("update namespace set used_bytes = used_bytes + %s where id = %s", (n * 3000, ns))
    return root


def main():
    label = sys.argv[1]
    sizes = [int(x) for x in sys.argv[2:]] or [100, 1000, 10000]
    rounds = int(os.environ.get("ROUNDS", "5"))
    out = {"background": BACKGROUND}
    with psycopg.connect(DB, autocommit=True) as conn, conn.cursor() as cur:
        with conn.transaction():
            ns, owner = seed_drive(cur)
        cur.execute("analyze file")
        for n in sizes:
            res = {k: [] for k in OPS}
            for r in range(rounds):
                with conn.transaction():
                    root = seed_folder(cur, ns, owner, n, f"bench{n}-{r}")
                for k in res:
                    res[k].append(op(cur, k, owner, root))
            out[n] = {k: {"ms_median": statistics.median(x["ms"] for x in v), "ms_all": [round(x["ms"]) for x in v],
                          "sql": [x["sql"] for x in v]} for k, v in res.items()}
            print(n, json.dumps(out[n]), flush=True)
    json.dump(out, open(f"results/index-{label}.json", "w"), ensure_ascii=False, indent=1, default=str)


if __name__ == "__main__":
    main()
