# .docs/perf-test/003-quota-concurrency.md
# latency.py <userId> <n> — n sequential commits of empty files; prints p50/p95 ms
import sys, json, uuid, time, urllib.request
user, n = sys.argv[1], int(sys.argv[2]); ms = []
for i in range(n + 5):
    req = {"files": [{"path": "/", "name": f"e{uuid.uuid4().hex}", "uploadId": str(uuid.uuid4()), "size": 0, "blocklist": []}]}
    q = urllib.request.Request("http://file-service:10012/api/v1/files/commit", json.dumps(req).encode(),
        {"X_USER_ID": user, "Content-Type": "application/json"})
    t = time.perf_counter(); r = json.loads(urllib.request.urlopen(q).read()); d = (time.perf_counter() - t) * 1000
    assert r["data"]["results"][0].get("versionId"), r
    if i >= 5: ms.append(d)  # first 5 warm up
ms.sort(); print(json.dumps({"p50": round(ms[len(ms)//2], 1), "p95": round(ms[int(len(ms)*0.95)], 1)}))
