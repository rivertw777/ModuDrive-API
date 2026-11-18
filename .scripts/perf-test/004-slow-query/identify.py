"""Slow-query identification (.docs/perf-test/004): calls file-service's read APIs on the 1M-file drive that
isolate.py made, then prints the statements that took the most time (pg_stat_statements, reset first).
Run inside the bench container: python identify.py <label> — after isolate.py has made the drives."""
import json, statistics, sys, urllib.parse
import psycopg
from bench import DB, http
from isolate import BIG_USER

ROUNDS = 3
APIS = [
    ("검색 — 드문 이름", "/api/v1/files/search?query=" + urllib.parse.quote("회의록_123")),
    ("검색 — 흔한 단어", "/api/v1/files/search?query=" + urllib.parse.quote("회의록")),
    ("전체 파일", "/api/v1/files/all"),
    ("카테고리 (이미지)", "/api/v1/files/category?type=IMAGE"),
    ("폴더 목록 (1,000개 든 폴더)", "/api/v1/directories?path=%2Fd7"),
    ("최근 문서함", "/api/v1/files/recent"),
    ("휴지통", "/api/v1/files/trash"),
    ("사용량", "/api/v1/files/usage"),
]
TOP = """select calls, round(mean_exec_time::numeric, 1), round(max_exec_time::numeric, 1), rows / greatest(calls, 1),
       left(regexp_replace(query, '\\s+', ' ', 'g'), 160)
  from pg_stat_statements s join pg_roles r on r.oid = s.userid
 where r.rolname = 'file_service' order by total_exec_time desc limit 8"""


def main():
    label = sys.argv[1]
    with psycopg.connect(DB, autocommit=True) as conn, conn.cursor() as cur:
        cur.execute("select pg_stat_statements_reset()")
        apis = []
        for name, path in APIS:
            http("GET", path, BIG_USER)  # warm-up
            calls = [http("GET", path, BIG_USER) for _ in range(ROUNDS)]
            body = json.loads(calls[-1][2] or b"{}")
            data = body.get("data")
            apis.append({"api": name, "status": sorted({c[0] for c in calls}),
                         "ms_median": round(statistics.median(c[1] for c in calls)),
                         "items": len(data) if isinstance(data, list) else None,
                         "message": body.get("message") if calls[-1][0] != 200 else None})
            print(json.dumps(apis[-1], ensure_ascii=False), flush=True)
        cur.execute(TOP)
        top = [dict(zip(["calls", "mean_ms", "max_ms", "rows_per_call", "query"], map(str, r))) for r in cur.fetchall()]
    for t in top:
        print(t["calls"], t["mean_ms"], t["max_ms"], t["rows_per_call"], t["query"][:120], flush=True)
    json.dump({"label": label, "apis": apis, "top_statements": top},
              open(f"results/identify-{label}.json", "w"), ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
