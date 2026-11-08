"""The requests the S3 scenarios send, straight to the services (X_USER_ID, no gateway) — runs in the s3-fault container.
  client.py upload <user> <blocks>             — commit → upload URLs → PUTs (through the proxy) → commit, as the WEB does
  client.py download <user> <fileId> <sha256>  — download the file and check its bytes
  client.py flood <user> <fileId> <hashes> <previews> <seconds> — <previews> previews at once, and meanwhile every
                                               second the commit's block lookup of <hashes> (comma-separated) and an
                                               upload-URL signing (no S3); prints a line per probe, then a summary
  client.py api <user> <path> <json>           — one WEB API call, for put.mjs
  client.py warm <user>                        — a few URL signings, so the first calls of a run aren't a cold start's
env CLIENT_TIMEOUT: seconds a commit or download is waited for (default 60)
env GATEWAY_SESSION, GATEWAY_ORIGIN: downloads, and flood's previews and URL signings, go through the gateway with
                                     this session cookie and Origin (the lookup is internal — always straight)
Prints one JSON line (flood: one per probe, then one)."""
import base64, collections, hashlib, http.client, json, os, sys, threading, time, uuid, urllib.parse, urllib.request

FILE_API, STORAGE_API = "http://file-service:10012", "http://storage-service:10013"
GATEWAY_API = "http://gateway-service:10001"
BLOCK = 4 * 1024 * 1024
# WEB (upload-file.ts withRetry): no status or 5xx -> again after 1s, 2s, 4s (+ up to 50% jitter), then every
# 30s (+ jitter) until 10 minutes have passed since the first of those; a PUT that moves nothing for 30s is
# given up like a network failure (urllib's timeout is that: per socket operation, not for the whole call).
RETRY_DELAYS = [1, 2, 4]
PAUSED_RETRY, MAX_PAUSE = 30, 600
PUT_IDLE_TIMEOUT = 30
TIMEOUT = int(os.environ.get("CLIENT_TIMEOUT", 60))  # how long a commit or download is waited for


def as_user(user):
    """Headers that make a request <user>'s: the gateway's session cookie, or X_USER_ID straight to a service."""
    session = os.environ.get("GATEWAY_SESSION")
    if session:
        return {"Cookie": f"__Host-session={session}", "Origin": os.environ["GATEWAY_ORIGIN"]}
    return {"X_USER_ID": user}


def public_api():
    """Where a WEB call (preview, URL signing) goes: the gateway when flood runs through it."""
    return GATEWAY_API if os.environ.get("GATEWAY_SESSION") else STORAGE_API


def call(method, url, user, body=None, timeout=TIMEOUT, headers=None):
    """(status, data or error text, ms) — never raises on an HTTP or network failure."""
    req = urllib.request.Request(url, json.dumps(body).encode() if body is not None else None, method=method,
                                 headers={**(headers or {"X_USER_ID": user}), "Content-Type": "application/json"})
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
    """As the WEB sends a block: a 2xx or 412 (S3 already has it) ends it; no status or a 5xx is tried again."""
    import random
    u = urllib.parse.urlsplit(url)
    tries, paused_at, start = [], None, time.perf_counter()
    for attempt in range(1000):
        req = urllib.request.Request(urllib.parse.urlunsplit(u._replace(netloc="s3-fault:8080")), data, method="PUT",
                                     headers={"Host": u.netloc, "If-None-Match": "*",
                                              "x-amz-checksum-sha256": base64.b64encode(hashlib.sha256(data).digest()).decode()})
        at = round(time.perf_counter() - start, 1)
        try:
            with urllib.request.urlopen(req, timeout=PUT_IDLE_TIMEOUT) as r:
                status = r.status
        except urllib.error.HTTPError as e:
            status = e.code
        except (OSError, http.client.HTTPException):
            status = None
        tries.append((at, status))
        # Done (2xx, 412: S3 already has it) or never worth another try — but a 409 (another PUT of the same
        # block in flight) is tried again, as the WEB does.
        if status is not None and status < 500 and status != 409:
            break
        if attempt < len(RETRY_DELAYS):
            delay = RETRY_DELAYS[attempt]
        else:
            paused_at = paused_at or time.perf_counter()
            if time.perf_counter() - paused_at >= MAX_PAUSE:
                break
            delay = PAUSED_RETRY
        time.sleep(delay * (1 + random.random() / 2))
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
    req = urllib.request.Request(f"{public_api()}/api/v1/storage/download/{file_id}", headers=as_user(user))
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


def previews(user, file_id, downloads, seconds, out):
    """<downloads> previews at once — in a process of its own, so starting their threads doesn't hold up
    the probes (one process: the probes' first requests went out seconds late)."""
    results = collections.Counter()

    def one():
        # Preview, not download: it answers on the request thread, while a download streams from MVC's async
        # executor and leaves the request thread free.
        req = urllib.request.Request(f"{public_api()}/api/v1/storage/view/{file_id}?fileName=a.bin", headers=as_user(user))
        try:
            with urllib.request.urlopen(req, timeout=seconds + 60) as r:
                r.read()
                results[r.status] += 1
        except urllib.error.HTTPError as e:
            results[e.code] += 1
        except (OSError, http.client.HTTPException) as e:
            results[type(e).__name__] += 1

    workers = [threading.Thread(target=one, daemon=True) for _ in range(downloads)]
    for w in workers:
        w.start()
    deadline = time.perf_counter() + seconds
    for w in workers:
        w.join(timeout=max(0, deadline - time.perf_counter()))
    out.put(dict(results))


def flood(user, file_id, hashes, downloads, seconds):
    import multiprocessing
    out = multiprocessing.Queue()
    proc = multiprocessing.Process(target=previews, args=(user, file_id, downloads, seconds, out))
    start = time.perf_counter()
    proc.start()
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
    presign = lambda: call("POST", public_api() + "/api/v1/storage/blocks/upload-urls", user,
                           {"blocks": [{"hash": hashlib.sha256(os.urandom(8)).hexdigest(), "size": 1}]}, timeout=30,
                           headers=as_user(user))
    probers = [threading.Thread(target=probe, args=(n, f)) for n, f in (("lookup", lookup), ("presign", presign))]
    for p in probers:
        p.start()
    for p in probers:
        p.join()
    done = out.get(timeout=seconds + 30)
    proc.join(timeout=1)
    out = {"downloads": downloads, "done": done, "unfinished": downloads - sum(done.values())}
    for name, got in probes.items():
        out[name] = {"probes": len(got), "ok": sum(1 for status, _ in got if status == 200),
                     "max_ms": max((ms for _, ms in got), default=None)}
    return out

def api(user, path, body):
    """One WEB API call (put.mjs's apiClient): status, body and Retry-After, to file- or storage-service by path."""
    base = STORAGE_API if path.startswith("/api/v1/storage/") else FILE_API
    req = urllib.request.Request(base + path, body.encode(), method="POST",
                                 headers={"X_USER_ID": user, "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=TIMEOUT) as r:
            status, out, retry = r.status, r.read(), r.headers.get("Retry-After")
    except urllib.error.HTTPError as e:
        status, out, retry = e.code, e.read(), e.headers.get("Retry-After")
    except (OSError, http.client.HTTPException) as e:
        return {"status": None, "body": repr(e)}
    try:
        out = json.loads(out)
    except ValueError:
        out = out[:200].decode(errors="replace")
    return {"status": status, "body": out, "retryAfter": int(retry) if retry else None}


if __name__ == "__main__":
    cmd, user, *args = sys.argv[1:]
    if cmd == "api":
        print(json.dumps(api(user, *args)))
        sys.exit()
    if cmd == "warm":
        print(json.dumps([call("POST", public_api() + "/api/v1/storage/blocks/upload-urls", user,
                               {"blocks": [{"hash": hashlib.sha256(os.urandom(8)).hexdigest(), "size": 1}]},
                               headers=as_user(user))[::2] for _ in range(5)]))
        sys.exit()
    out = (upload(user, int(args[0])) if cmd == "upload" else download(user, *args) if cmd == "download"
           else flood(user, args[0], args[1].split(","), int(args[2]), int(args[3])))
    print(json.dumps(out, ensure_ascii=False))
