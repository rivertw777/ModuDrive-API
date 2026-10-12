"""Lock-hold benchmark (.docs/perf-test/001-folder-cascade.md): while a cascade runs, a second client keeps
committing empty files into the same drive; records the slowest commit.
python contend.py <label> [sizes...]"""
import json, os, sys, threading, time, statistics
import psycopg
from cascade import DB, http, seed, commit_empty


def run(user, op_call, commit_path):
    lat, stop = [], threading.Event()

    def committer():
        while not stop.is_set():
            s, ms, _ = commit_empty(user, commit_path)
            lat.append(ms)

    th = threading.Thread(target=committer)
    th.start()
    time.sleep(0.2)  # committer warm and running before the cascade starts
    s, op_ms, body = http(op_call[0], op_call[1], user, op_call[2])
    stop.set(); th.join()
    assert s < 300, body[:300]
    return {"op_ms": round(op_ms), "commits": len(lat), "max_commit_ms": round(max(lat)), "p50_commit_ms": round(statistics.median(lat))}


def main():
    label = sys.argv[1]
    sizes = [int(x) for x in sys.argv[2:]] or [1000, 10000]
    out = {}
    with psycopg.connect(DB, autocommit=True) as conn, conn.cursor() as cur:
        for n in sizes:
            rows = {"purge_vs_root_commit": [], "move_vs_inner_commit": []}
            for _ in range(int(os.environ.get("ROUNDS", "3"))):
                with conn.transaction():
                    ns, owner, root = seed(cur, n)
                http("DELETE", f"/api/v1/files/{root}", owner)
                rows["purge_vs_root_commit"].append(run(owner, ("DELETE", f"/api/v1/files/{root}/purge", None), "/"))
                with conn.transaction():
                    ns, owner, root = seed(cur, n)
                rows["move_vs_inner_commit"].append(run(owner, ("PATCH", f"/api/v1/files/{root}/path", {"path": "/dest"}), "/bench/d3"))
            out[n] = rows
            print(n, json.dumps(rows), flush=True)
    json.dump(out, open(f"/w/results/contend-{label}.json", "w"), indent=1)


if __name__ == "__main__":
    main()
