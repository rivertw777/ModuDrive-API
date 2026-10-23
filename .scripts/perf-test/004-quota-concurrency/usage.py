# .docs/perf-test/004-quota-concurrency.md
# usage.py <userId> <n> — n sequential GET usage calls; prints p50/p95 ms
import sys, json, time, urllib.request
user, n = sys.argv[1], int(sys.argv[2]); ms = []
for i in range(n + 5):
    q = urllib.request.Request("http://file-service:10012/api/v1/files/usage", headers={"X_USER_ID": user})
    t = time.perf_counter(); urllib.request.urlopen(q).read(); d = (time.perf_counter() - t) * 1000
    if i >= 5: ms.append(d)
ms.sort(); print(json.dumps({"p50": round(ms[len(ms)//2], 1), "p95": round(ms[int(len(ms)*0.95)], 1)}))
