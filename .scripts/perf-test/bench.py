"""Shared by the perf-test benchmarks: DB, the file-service HTTP call and pg_stat_statements. scenario.sh puts
this folder on the bench container's PYTHONPATH."""
import json, os, time, urllib.request

DB = os.environ.get("BENCH_DB", "host=postgres dbname=file_db user=modudrive password=changeme")
API = "http://file-service:10012"
NOISE = ("outbox", "shedlock", "processed_event", "pg_stat_statements")


def http(method, path, user, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(API + path, data=data, method=method,
                                 headers={"X_USER_ID": str(user), "Content-Type": "application/json"})
    t = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=600) as r:
            status, payload = r.status, r.read()
    except urllib.error.HTTPError as e:
        status, payload = e.code, e.read()
    return status, (time.perf_counter() - t) * 1000, payload


def reset_stats(cur):
    cur.execute("select pg_stat_statements_reset()")


def read_stats(cur):
    cur.execute("select s.query, s.calls from pg_stat_statements s join pg_roles r on r.oid=s.userid "
                "join pg_database d on d.oid=s.dbid where r.rolname='file_service' and d.datname='file_db'")
    rows = [(q, c) for q, c in cur.fetchall() if not any(x in q.lower() for x in NOISE)]
    return sum(c for _, c in rows), sorted(rows, key=lambda r: -r[1])[:8]
