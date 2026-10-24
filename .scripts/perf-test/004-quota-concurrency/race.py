# .docs/perf-test/004-quota-concurrency.md
# Usage: race.py <userId> <files> <fileBytes>  — uploads N distinct blocks, then commits N files at once.
import sys, os, json, uuid, hashlib, threading, urllib.request
user, n, size = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
blocks = [os.urandom(size) for _ in range(n)]
hashes = [hashlib.sha256(b).hexdigest() for b in blocks]
for h, b in zip(hashes, blocks):
    bnd = uuid.uuid4().hex
    body = b""
    body += f"--{bnd}\r\nContent-Disposition: form-data; name=\"hash\"\r\n\r\n{h}\r\n".encode()
    body += f"--{bnd}\r\nContent-Disposition: form-data; name=\"block\"; filename=\"b\"\r\nContent-Type: application/octet-stream\r\n\r\n".encode() + b + b"\r\n"
    body += f"--{bnd}--\r\n".encode()
    r = urllib.request.Request("http://storage-service:10013/api/v1/storage/blocks", body,
        {"X_USER_ID": user, "Content-Type": f"multipart/form-data; boundary={bnd}"})
    urllib.request.urlopen(r).read()
gate = threading.Barrier(n); out = [None] * n
def commit(i):
    req = {"files": [{"path": "/", "name": f"f{i}.bin", "uploadId": str(uuid.uuid4()), "size": size, "blocklist": [hashes[i]]}]}
    q = urllib.request.Request("http://file-service:10012/api/v1/files/commit", json.dumps(req).encode(),
        {"X_USER_ID": user, "Content-Type": "application/json"})
    gate.wait()
    try:
        res = json.loads(urllib.request.urlopen(q).read())["data"]["results"][0]
        out[i] = "ok" if res.get("versionId") else (res.get("error") or {}).get("message", "need")
    except Exception as e:
        out[i] = f"http {e}"
ts = [threading.Thread(target=commit, args=(i,)) for i in range(n)]
[t.start() for t in ts]; [t.join() for t in ts]
print(json.dumps({"ok": out.count("ok"), "other": sorted(set(o for o in out if o != "ok"))}, ensure_ascii=False))
