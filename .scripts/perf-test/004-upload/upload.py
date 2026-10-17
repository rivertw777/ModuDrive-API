""".docs/perf-test/004-upload.md — one upload the way the WEB does it (spec 001 2): commit → upload URLs → S3 PUTs →
second commit, each phase timed, with the DB statements and Redis commands it sent.
Usage: upload.py <label> big <blocks>          — one file of <blocks> × 4MiB
       upload.py <label> small <files> <bytes>  — <files> one-block files, committed 100 at a time
Runs in the bench container (scenario.sh up); env REDIS_PASSWORD. Prints one JSON line and appends it to results/."""
import base64, hashlib, json, os, socket, sys, time, uuid, urllib.parse, urllib.request
from concurrent.futures import ThreadPoolExecutor
import psycopg
from bench import DB, reset_stats, read_stats

FILE_API, STORAGE_API = "http://file-service:10012", "http://storage-service:10013"
BLOCK, GROUP, URLS_PER_REQUEST, PUTS = 4 * 1024 * 1024, 100, 64, 6  # spec 001 6: block, commit group, URL request, PUTs
QUOTA = 1_000_000_000_000_000  # scenario.sh cleanup's marker for bench drives
IN_SCRIPT = {"incr", "incrby", "decr", "decrby", "pexpire"}


def post(base, path, user, body):
    req = urllib.request.Request(base + path, json.dumps(body).encode(), method="POST",
                                 headers={"X_USER_ID": user, "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=600) as r:
        return json.loads(r.read())["data"]


def redis_calls():
    """Commands Redis has served so far, by command (INFO commandstats) — a phase's count is the difference."""
    pw = os.environ["REDIS_PASSWORD"]
    with socket.create_connection(("redis", 6379)) as s:
        f = s.makefile("rb")
        s.sendall(f"*2\r\n$4\r\nAUTH\r\n${len(pw)}\r\n{pw}\r\n*2\r\n$4\r\nINFO\r\n$12\r\ncommandstats\r\n".encode())
        assert f.readline() == b"+OK\r\n"
        info = f.read(int(f.readline()[1:]))  # bulk string: $<len>\r\n<bytes>
    return {part.split(b":")[0].decode(): int(part.split(b"calls=")[1].split(b",")[0]) for part in info.split(b"cmdstat_")[1:]}


def put(url, data, digest):
    # The URL names localhost:4566 (the browser's view); the bench reaches LocalStack by its compose name and
    # keeps the signed Host.
    u = urllib.parse.urlsplit(url)
    req = urllib.request.Request(urllib.parse.urlunsplit(u._replace(netloc="localstack:4566")), data, method="PUT",
                                 headers={"Host": u.netloc, "x-amz-checksum-sha256": base64.b64encode(digest).decode(),
                                          "If-None-Match": "*"})
    try:
        urllib.request.urlopen(req, timeout=600).read()
    except urllib.error.HTTPError as e:
        if e.code != 412:  # already there: counts as uploaded (spec 001 2-4-2)
            raise


def main():
    label, kind = sys.argv[1], sys.argv[2]
    if kind == "big":
        n = int(sys.argv[3])
        blobs = [os.urandom(BLOCK) for _ in range(n)]
        files = [{"name": "big.bin", "blocks": list(range(n))}]
    else:
        n, size = int(sys.argv[3]), int(sys.argv[4])
        blobs = [os.urandom(size) for _ in range(n)]
        files = [{"name": f"f{i:05}.bin", "blocks": [i]} for i in range(n)]
    digests = [hashlib.sha256(b).digest() for b in blobs]
    hashes = [d.hex() for d in digests]
    user = str(uuid.uuid4())
    for f in files:
        f["req"] = {"path": "/bench", "name": f["name"], "uploadId": str(uuid.uuid4()),
                    "size": sum(len(blobs[i]) for i in f["blocks"]), "blocklist": [hashes[i] for i in f["blocks"]]}
    groups = [files[i:i + GROUP] for i in range(0, len(files), GROUP)] if kind == "small" else [files]

    with psycopg.connect(DB, autocommit=True) as conn, conn.cursor() as cur:
        cur.execute("insert into namespace(id,user_id,root_path,quota_bytes,used_bytes,created_at,updated_at,is_deleted) "
                    "values (gen_random_uuid(),%s,%s,%s,0,now(),now(),false)", (user, "/" + user, QUOTA))
        out = {"label": label, "kind": kind, "n": n, "user": user}

        def phase(name, fn):
            reset_stats(cur)
            redis0, t = redis_calls(), time.perf_counter()
            result = fn()
            redis1 = redis_calls()
            # Less the bench's own AUTH + INFO between the two reads.
            redis1["auth"] -= 1
            redis1["info"] -= 1
            redis = {c: k - redis0.get(c, 0) for c, k in redis1.items() if k - redis0.get(c, 0)}
            out[name] = {"ms": round((time.perf_counter() - t) * 1000, 1), "db": read_stats(cur)[0],
                         # Round trips: what upload-count.lua runs inside its one EVALSHA isn't one.
                         "redis": sum(k for c, k in redis.items() if c not in IN_SCRIPT), "redis_by_command": redis}
            return result

        def commit_all():
            return [r for g in groups for r in post(FILE_API, "/api/v1/files/commit", user,
                                                    {"files": [f["req"] for f in g]})["results"]]

        first = phase("commit1", commit_all)
        need = sorted({h for r in first for h in (r.get("needBlocks") or [])})
        assert len(need) == n, f"expected every block needed, got {len(need)} of {n}: {first[:2]}"
        index = {h: i for i, h in enumerate(hashes)}

        def presign():
            urls = {}
            for i in range(0, len(need), URLS_PER_REQUEST):
                chunk = need[i:i + URLS_PER_REQUEST]
                got = post(STORAGE_API, "/api/v1/storage/blocks/upload-urls", user,
                           {"blocks": [{"hash": h, "size": len(blobs[index[h]])} for h in chunk]})["urls"]
                urls.update(zip(chunk, got))
            return urls
        urls = phase("presign", presign)

        def put_all():
            with ThreadPoolExecutor(PUTS) as pool:
                list(pool.map(lambda h: put(urls[h], blobs[index[h]], digests[index[h]]), need))
        phase("put", put_all)

        second = phase("commit2", commit_all)
        done = sum(1 for r in second if r.get("versionId"))
        assert done == len(files), f"{done} of {len(files)} committed: {[r for r in second if not r.get('versionId')][:2]}"

    line = json.dumps(out)
    print(line)
    os.makedirs("results", exist_ok=True)
    with open(f"results/{label}.jsonl", "a") as fh:
        fh.write(line + "\n")


main()
