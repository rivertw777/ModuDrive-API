"""Slow-query isolation (.docs/perf-test/004). SEARCHERS clients search a drive of BIG files back to back for
SECONDS, while a probe lists another user's small folder once a second — does the slow search take the
connections the folder list needs? Run inside the bench container: python isolate.py <label>"""
import json, os, re, statistics, sys, threading, time, urllib.parse, urllib.request, uuid
import psycopg
from bench import DB, http

BIG = int(os.environ.get("BIG", "1000000"))
SEARCHERS = int(os.environ.get("SEARCHERS", "20"))
SECONDS = int(os.environ.get("SECONDS", "60"))
QUERY = os.environ.get("QUERY", "회의록_123")
METRICS = "http://file-service:9464/actuator/prometheus"
MARK = 1000000000000000  # quota 1e15: scenario.sh cleanup deletes these drives
BIG_USER = uuid.uuid5(uuid.NAMESPACE_URL, "modudrive-bench/slow-query/big")
SMALL_USER = uuid.uuid5(uuid.NAMESPACE_URL, "modudrive-bench/slow-query/small")
WORDS = "회의록 보고서 견적서 계약서 기획안 report invoice draft final photo notes budget".split()
EXTS = "pdf docx xlsx jpg png txt".split()
COLS = "id,namespace_id,owner_id,name,path,is_directory,file_size,current_version_id,status,active_slot_name,access_scope,created_at,updated_at,is_deleted"


def drive(cur, user, files):
    """The user's drive, made once: files named like '보고서_123456.pdf', spread over 1,000 folders."""
    cur.execute("select id from namespace where user_id = %s", (user,))
    row = cur.fetchone()
    if row:
        return
    ns = uuid.uuid4()
    cur.execute("insert into namespace(id,user_id,root_path,quota_bytes,used_bytes,created_at,updated_at,is_deleted) "
                "values (%s,%s,%s,%s,%s,now(),now(),false)", (ns, user, f"/{user}", MARK, files * 1000))
    words = "array[" + ",".join(f"'{w}'" for w in WORDS) + "]"
    exts = "array[" + ",".join(f"'{e}'" for e in EXTS) + "]"
    # %% — psycopg reads a lone % as a placeholder.
    name = f"({words})[1+g%%{len(WORDS)}]||'_'||g||'.'||({exts})[1+(g/{len(WORDS)})%%{len(EXTS)}]"
    path = "'/'" if files <= 1000 else "'/d'||(g%%1000)"
    cur.execute(f"insert into file({COLS}) select gen_random_uuid(),%s,%s,{name},{path},false,1000,null,'UPLOADED',"
                f"{name},'RESTRICTED',now(),now(),false from generate_series(1,%s) g", (ns, user, files))


def sample_pool(stop, out):
    """Hikari's pending (waiting for a connection) and active, every 0.5s, from file-service's actuator."""
    while not stop.is_set():
        try:
            text = urllib.request.urlopen(METRICS, timeout=2).read().decode()
            pick = lambda m: float(re.search(rf"^hikaricp_connections_{m}{{[^}}]*}} (\S+)", text, re.M).group(1))
            out.append((time.time(), pick("pending"), pick("active")))
        except Exception:
            pass
        stop.wait(0.5)


def summary(calls):
    ms = sorted(c["ms"] for c in calls)
    statuses = {}
    for c in calls:
        statuses[c["status"]] = statuses.get(c["status"], 0) + 1
    return {"n": len(calls), "status": statuses, "ms_median": round(statistics.median(ms)) if ms else None,
            "ms_max": round(ms[-1]) if ms else None}


def main():
    label = sys.argv[1]
    with psycopg.connect(DB, autocommit=True) as conn, conn.cursor() as cur:
        with conn.transaction():
            drive(cur, BIG_USER, BIG)
            drive(cur, SMALL_USER, 100)
        cur.execute("analyze file")
    search = f"/api/v1/files/search?query={urllib.parse.quote(QUERY)}"
    folder = "/api/v1/directories?path=%2F"

    # Alone first: one search's time and the folder list's normal time (after a warm-up each).
    http("GET", search, BIG_USER), http("GET", folder, SMALL_USER)
    s, alone_search, body = http("GET", search, BIG_USER)
    hits = len(json.loads(body)["data"]) if s == 200 else None
    alone_folder = [http("GET", folder, SMALL_USER)[1] for _ in range(10)]

    searches, probes, pool = [], [], []
    stop_at = time.time() + SECONDS
    done = threading.Event()

    def searcher():
        while time.time() < stop_at:
            st, ms, _ = http("GET", search, BIG_USER)
            searches.append({"status": st, "ms": ms})

    def probe():
        for i in range(SECONDS):
            time.sleep(max(0, stop_at - SECONDS + i - time.time()))
            st, ms, _ = http("GET", folder, SMALL_USER)
            probes.append({"at": i, "status": st, "ms": ms})

    sampler = threading.Thread(target=sample_pool, args=(done, pool))
    sampler.start()
    threads = [threading.Thread(target=searcher) for _ in range(SEARCHERS)] + [threading.Thread(target=probe)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    done.set()
    sampler.join()

    out = {
        "label": label, "big_files": BIG, "searchers": SEARCHERS, "seconds": SECONDS, "query": QUERY, "hits": hits,
        "alone": {"search_ms": round(alone_search), "folder_ms_median": round(statistics.median(alone_folder))},
        "search": summary(searches), "folder": summary(probes),
        "pool_pending_max": max((p[1] for p in pool), default=None),
        "pool_active_max": max((p[2] for p in pool), default=None),
        "folder_calls": [{"at": p["at"], "status": p["status"], "ms": round(p["ms"])} for p in probes],
    }
    print(json.dumps({k: v for k, v in out.items() if k != "folder_calls"}, ensure_ascii=False), flush=True)
    json.dump(out, open(f"results/isolate-{label}.json", "w"), ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
