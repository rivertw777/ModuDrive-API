"""The requests the S3 scenarios send, straight to the services (X_USER_ID, no gateway) — runs in the s3-fault container.
  client.py upload <user> <blocks>             — commit → upload URLs → PUTs (through the proxy) → commit, as the WEB does
  client.py download <user> <fileId> <sha256>  — download the file and check its bytes
  client.py flood <user> <fileId> <hashes> <previews> <seconds> — <previews> previews at once, and meanwhile every
                                               second the commit's block lookup of <hashes> (comma-separated) and an
                                               upload-URL signing (no S3); prints a line per probe, then a summary
env CLIENT_TIMEOUT: seconds a commit or download is waited for (default 60)
Prints one JSON line (flood: one per probe, then one)."""
import base64, collections, hashlib, http.client, json, os, socket, sys, threading, time, uuid, urllib.parse, urllib.request

FILE_API, STORAGE_API = "http://file-service:10012", "http://storage-service:10013"
BLOCK = 4 * 1024 * 1024
PUT_TIMEOUT = 10
TIMEOUT = int(os.environ.get("CLIENT_TIMEOUT", 60))  # how long a commit or download is waited for


def call(method, url, user, body=None, timeout=TIMEOUT):
    """(status, data or error text, ms) — never raises on an HTTP or network failure."""
    req = urllib.request.Request(url, json.dumps(body).encode() if body is not None else None, method=method,
                                 headers={"X_USER_ID": user, "Content-Type": "application/json"})
    t = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            status, out = r.status, r.read()
    except urllib.error.HTTPError as e:
        status, out = e.code, e.read()
    except (OSError, http.client.HTTPException) as e:
        status, out = None, repr(e).encode()
    ms = round((time.perf_counter() - t) * 1000)
    try:
        out = json.loads(out)
    except ValueError:
        out = out[:200].decode(errors="replace")
    return status, out, ms


def put(url, data):
    """Once, then once more on a timeout — what the WEB does. 412 means S3 already has the block."""
    u = urllib.parse.urlsplit(url)
    tries = []
    for _ in range(2):
        req = urllib.request.Request(urllib.parse.urlunsplit(u._replace(netloc="s3-fault:8080")), data, method="PUT",
                                     headers={"Host": u.netloc, "If-None-Match": "*",
                                              "x-amz-checksum-sha256": base64.b64encode(hashlib.sha256(data).digest()).decode()})
        try:
            with urllib.request.urlopen(req, timeout=PUT_TIMEOUT) as r:
                tries.append(r.status)
        except urllib.error.HTTPError as e:
            tries.append(e.code)
        except (socket.timeout, TimeoutError):
            tries.append("timeout")
            continue
        break
    return tries


def upload(user, blocks):
    data = [os.urandom(BLOCK) for _ in range(blocks)]
    hashes = [hashlib.sha256(b).hexdigest() for b in data]
    file = {"path": "/", "name": f"s3-fault-{int(time.time())}.bin", "uploadId": str(uuid.uuid4()),
            "size": BLOCK * blocks, "blocklist": hashes}
    out = {"hashes": hashes, "sha256": hashlib.sha256(b"".join(data)).hexdigest()}

    def commit(name):
        status, body, ms = call("POST", FILE_API + "/api/v1/files/commit", user, {"files": [file]})
        out[name] = {"status": status, "ms": ms}
        result = body["data"]["results"][0] if status == 200 else body
        out[name]["result"] = result
        return result

    first = commit("commit1")
    need = first.get("needBlocks") or [] if isinstance(first, dict) else []
    if need:
        status, body, ms = call("POST", STORAGE_API + "/api/v1/storage/blocks/upload-urls", user,
                                {"blocks": [{"hash": h, "size": BLOCK} for h in need]})
        out["presign"] = {"status": status, "ms": ms}
        if status != 200:
            out["presign"]["result"] = body
            return out
        t = time.perf_counter()
        out["put"] = {"tries": [put(url, data[hashes.index(h)]) for h, url in zip(need, body["data"]["urls"])]}
        out["put"]["ms"] = round((time.perf_counter() - t) * 1000)
        second = commit("commit2")
        out["fileId"] = second.get("fileId") if isinstance(second, dict) else None
    else:
        out["fileId"] = first.get("fileId")
    return out


def download(user, file_id, expected):
    req = urllib.request.Request(f"{STORAGE_API}/api/v1/storage/download/{file_id}", headers={"X_USER_ID": user})
    t = time.perf_counter()
    got, digest, status, error = 0, hashlib.sha256(), None, None
    try:
        with urllib.request.urlopen(req, timeout=TIMEOUT) as r:
            status = r.status
            while chunk := r.read(1 << 16):
                got += len(chunk)
                digest.update(chunk)
    except urllib.error.HTTPError as e:
        status, error = e.code, e.read()[:200].decode(errors="replace")
    except (OSError, http.client.HTTPException) as e:
        error = repr(e)
    return {"status": status, "bytes": got, "intact": digest.hexdigest() == expected, "error": error,
            "ms": round((time.perf_counter() - t) * 1000)}


def flood(user, file_id, hashes, downloads, seconds):
    results = collections.Counter()

    def one():
        # Preview, not download: it answers on the request thread, while a download streams from MVC's async
        # executor and leaves the request thread free.
        req = urllib.request.Request(f"{STORAGE_API}/api/v1/storage/view/{file_id}?fileName=a.bin", headers={"X_USER_ID": user})
        try:
            with urllib.request.urlopen(req, timeout=seconds + 60) as r:
                r.read()
                results[r.status] += 1
        except urllib.error.HTTPError as e:
            results[e.code] += 1
        except (OSError, http.client.HTTPException) as e:
            results[type(e).__name__] += 1

    start = time.perf_counter()
    workers = [threading.Thread(target=one, daemon=True) for _ in range(downloads)]
    for w in workers:
        w.start()
    probes = {"lookup": [], "presign": []}

    def probe(name, send):
        # Once a second, each on its own thread, so a hung lookup doesn't delay the presign probe.
        while (elapsed := time.perf_counter() - start) < seconds:
            status, _, ms = send()
            probes[name].append((status, ms))
            print(json.dumps({"t": round(elapsed), name: status, "ms": ms}), flush=True)
            time.sleep(max(0, 1 - ms / 1000))

    # What file-service's commit asks (HEADs, not the hung GETs) — it only shares S3's connections with them.
    lookup = lambda: call("POST", STORAGE_API + "/internal/storage/blocks/uploaded", user,
                          {"ownerId": user, "hashes": hashes}, timeout=30)
    # Signing an upload URL never calls S3 — it should answer whatever S3 does.
    presign = lambda: call("POST", STORAGE_API + "/api/v1/storage/blocks/upload-urls", user,
                           {"blocks": [{"hash": hashlib.sha256(os.urandom(8)).hexdigest(), "size": 1}]}, timeout=30)
    probers = [threading.Thread(target=probe, args=(n, f)) for n, f in (("lookup", lookup), ("presign", presign))]
    for p in probers:
        p.start()
    for p in probers:
        p.join()
    for w in workers:
        w.join(timeout=0)
    out = {"downloads": downloads, "done": dict(results), "unfinished": downloads - sum(results.values())}
    for name, got in probes.items():
        out[name] = {"probes": len(got), "ok": sum(1 for status, _ in got if status == 200),
                     "max_ms": max((ms for _, ms in got), default=None)}
    return out

if __name__ == "__main__":
    cmd, user, *args = sys.argv[1:]
    out = (upload(user, int(args[0])) if cmd == "upload" else download(user, *args) if cmd == "download"
           else flood(user, args[0], args[1].split(","), int(args[2]), int(args[3])))
    print(json.dumps(out, ensure_ascii=False))
