""".docs/perf-test/001-upload.md — uploads the way the WEB does (spec 001 2): commit → upload URLs → S3 PUTs → second
commit, each phase timed, with the DB statements and Redis commands it sent.
Usage: upload.py <label> big <blocks>                  — one file of <blocks> × 4MiB
       upload.py <label> small <files> <bytes>          — <files> one-block files, committed 100 at a time
       upload.py <label> concurrent <users> <kind> <args>... — <users> drives each uploading <kind> (big, small
                                                          or mixed, with its args) at once
       upload.py <label> mixed                          — one folder of mixed files (MIXED), grouped as the WEB does
Env INFLIGHT=<n>: the WEB's own flow instead of phase by phase — each group commit → URLs → PUTs → commit on its
own, n groups at a time, sharing the 6 PUTs; only the whole upload is timed ("total").
Runs in the bench container (scenario.sh up); env REDIS_PASSWORD. Prints one JSON line and appends it to results/."""
import base64, contextlib, hashlib, json, os, random, socket, statistics, sys, threading, time, uuid, urllib.parse, urllib.request
from concurrent.futures import ThreadPoolExecutor
import psycopg
from bench import DB, reset_stats, read_stats

FILE_API, STORAGE_API = "http://file-service:10012", "http://storage-service:10013"
BLOCK, GROUP, URLS_PER_REQUEST, PUTS = 4 * 1024 * 1024, 100, 64, 6  # spec 001 6: block, commit group, URL request, PUTs
QUOTA = 1_000_000_000_000_000  # scenario.sh cleanup's marker for bench drives
IN_SCRIPT = {"incr", "incrby", "decr", "decrby", "pexpire"}
PHASES = ("commit1", "presign", "put", "commit2")
INFLIGHT = int(os.environ.get("INFLIGHT") or 0)
SOLO_BLOCKS = 64  # WEB: a file over 64 blocks (256MiB) is a group of its own
KiB, MiB = 1024, 1024 * 1024
# A work folder as people upload it: (subfolder, extension, files, smallest, largest bytes). The extension only
# names the file (FileCategory); the upload's cost is the sizes and the folders.
MIXED = [("문서", "pdf", 60, 100 * KiB, 5 * MiB), ("문서", "docx", 50, 20 * KiB, 2 * MiB),
         ("문서/보고서", "xlsx", 40, 10 * KiB, 1 * MiB), ("문서/보고서", "hwp", 20, 30 * KiB, 3 * MiB),
         ("코드/src/main", "java", 250, 1 * KiB, 20 * KiB), ("코드", "json", 30, 1 * KiB, 50 * KiB),
         ("사진", "jpg", 60, 2 * MiB, 6 * MiB), ("사진/스크린샷", "png", 20, 100 * KiB, 3 * MiB),
         ("음악", "mp3", 15, 3 * MiB, 10 * MiB), ("압축", "zip", 5, 10 * MiB, 60 * MiB),
         ("영상", "mp4", 1, 120 * MiB, 120 * MiB), ("영상", "mp4", 1, 400 * MiB, 400 * MiB)]


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


def redis_delta(before):
    after = redis_calls()
    # Less the bench's own AUTH + INFO between the two reads.
    after["auth"] -= 1
    after["info"] -= 1
    by_command = {c: k - before.get(c, 0) for c, k in after.items() if k - before.get(c, 0)}
    # Round trips: what upload-count.lua runs inside its one EVALSHA isn't one.
    return {"redis": sum(k for c, k in by_command.items() if c not in IN_SCRIPT), "redis_by_command": by_command}


class Blocks:
    """Block i's bytes, made again from a seed whenever needed — a 5GiB file is never held in memory."""

    def __init__(self, sizes):
        self.seed, self.sizes = uuid.uuid4().hex, sizes
        self.digests = [hashlib.sha256(self.data(i)).digest() for i in range(len(sizes))]
        self.hashes = [d.hex() for d in self.digests]
        self.index = {h: i for i, h in enumerate(self.hashes)}

    def data(self, i):
        return random.Random(f"{self.seed}:{i}").randbytes(self.sizes[i])


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


def new_drive(cur):
    user = str(uuid.uuid4())
    cur.execute("insert into namespace(id,user_id,root_path,quota_bytes,used_bytes,created_at,updated_at,is_deleted) "
                "values (gen_random_uuid(),%s,%s,%s,0,now(),now(),false)", (user, "/" + user, QUOTA))
    return user


def split(size):
    """A file's block sizes: 4MiB each, the last one smaller."""
    return [min(BLOCK, size - k) for k in range(0, size, BLOCK)]


def plan(kind, args):
    """The files to upload as (folder, name, size), in the order the WEB would send them."""
    if kind == "big":
        return [("/bench", "big.bin", BLOCK * args[0])]
    if kind == "mixed":
        sizes = random.Random(0)  # the same folder every round; only the bytes change
        files = [(sub, ext, sizes.randint(lo, hi)) for sub, ext, n, lo, hi in MIXED for _ in range(n)]
        # Numbered across the list: two rows may share a folder and an extension.
        return [(f"/bench/{sub}", f"{ext}-{k:04}.{ext}", size) for k, (sub, ext, size) in enumerate(files)]
    files, size = args[-2:]
    return [("/bench", f"f{k:05}.bin", size) for k in range(files)]


class Upload:
    """The files' blocks and their commit requests, grouped as the WEB groups them (groupForCommit: 100 files or
    1,280 blocks to a group, a file over 64 blocks alone)."""

    def __init__(self, files):
        self.files = [(folder, name, size, split(size)) for folder, name, size in files]
        self.blocks = Blocks([b for *_, sizes in self.files for b in sizes])

    def groups(self):
        """Fresh commit requests (new uploadIds)."""
        groups, group, hashes, solo_group, first = [], [], 0, False, 0
        for folder, name, size, sizes in self.files:
            ids = range(first, first + len(sizes))
            first += len(sizes)
            solo = len(sizes) > SOLO_BLOCKS
            if group and (solo or solo_group or len(group) == GROUP or hashes + len(sizes) > 1280):
                groups.append(group)
                group, hashes = [], 0
            group.append({"path": folder, "name": name,
                          "uploadId": str(uuid.uuid4()), "size": size, "blocklist": [self.blocks.hashes[i] for i in ids]})
            hashes += len(sizes)
            solo_group = solo
        return groups + ([group] if group else [])


def upload(user, blocks, groups, phase, seats=None):
    """One upload, step by step; phase(name, fn) runs and records each step. seats, if given, caps the PUTs in flight
    across uploads running at once."""
    files = sum(len(g) for g in groups)

    def commit_all():
        return [r for g in groups for r in post(FILE_API, "/api/v1/files/commit", user, {"files": g})["results"]]

    first = phase("commit1", commit_all)
    # A file that failed (not needBlocks) would never get its blocks sent: stop here, not at the second commit.
    assert not any(r.get("error") for r in first), [r for r in first if r.get("error")][:2]
    need = sorted({h for r in first for h in (r.get("needBlocks") or [])})

    def presign():
        urls = {}
        for i in range(0, len(need), URLS_PER_REQUEST):
            chunk = need[i:i + URLS_PER_REQUEST]
            got = post(STORAGE_API, "/api/v1/storage/blocks/upload-urls", user,
                       {"blocks": [{"hash": h, "size": blocks.sizes[blocks.index[h]]} for h in chunk]})["urls"]
            urls.update(zip(chunk, got))
        return urls
    urls = phase("presign", presign)

    def put_all():
        def one(h):
            with seats or contextlib.nullcontext():
                put(urls[h], blocks.data(blocks.index[h]), blocks.digests[blocks.index[h]])
        with ThreadPoolExecutor(PUTS) as pool:
            list(pool.map(one, need))
    phase("put", put_all)

    second = phase("commit2", commit_all)
    done = sum(1 for r in second if r.get("versionId"))
    assert done == files, f"{done} of {files} committed: {[r for r in second if not r.get('versionId')][:2]}"


def web_upload(user, blocks, groups, inflight):
    """The WEB's flow (use-file-upload.ts): every group on its own, inflight groups at a time, all their PUTs sharing
    the browser's 6 connections."""
    seats = threading.Semaphore(PUTS)
    with ThreadPoolExecutor(inflight) as pool:
        list(pool.map(lambda g: upload(user, blocks, [g], lambda name, fn: fn(), seats), groups))


def measured(cur, out):
    """phase() that records the step's time, DB statements and Redis round trips into out."""
    def phase(name, fn):
        reset_stats(cur)
        redis0, t = redis_calls(), time.perf_counter()
        result = fn()
        out[name] = {"ms": round((time.perf_counter() - t) * 1000, 1), "db": read_stats(cur)[0], **redis_delta(redis0)}
        return result
    return phase


def timed(times):
    """phase() that records only the step's time — for drives uploading at once, whose DB and Redis counts can't be
    told apart."""
    def phase(name, fn):
        t = time.perf_counter()
        result = fn()
        times[name] = round((time.perf_counter() - t) * 1000, 1)
        return result
    return phase


def main():
    label, kind, *args = sys.argv[1:]
    if kind == "concurrent":
        users, each, args = int(args[0]), args[1], [int(a) for a in args[2:]]
    else:
        args = [int(a) for a in args]
    out = {"label": label, "kind": kind, "inflight": INFLIGHT, "args": [users, each, *args] if kind == "concurrent" else args}
    with psycopg.connect(DB, autocommit=True) as conn, conn.cursor() as cur:
        if kind == "concurrent":
            drives = [(new_drive(cur), Upload(plan(each, args))) for _ in range(users)]
            out["users"] = [u for u, _ in drives]
            times = [{} for _ in drives]
            gate = threading.Barrier(users)

            def run(k):
                user, up = drives[k]
                groups = up.groups()
                gate.wait()
                if INFLIGHT:
                    timed(times[k])("total", lambda: web_upload(user, up.blocks, groups, INFLIGHT))
                else:
                    upload(user, up.blocks, groups, timed(times[k]))
            reset_stats(cur)
            redis0, t = redis_calls(), time.perf_counter()
            with ThreadPoolExecutor(users) as pool:
                list(pool.map(run, range(users)))
            out["wall_ms"] = round((time.perf_counter() - t) * 1000, 1)
            out["db"] = read_stats(cur)[0]
            out.update(redis_delta(redis0))
            out["per_drive"] = {p: {"median": statistics.median(x[p] for x in times), "max": max(x[p] for x in times)}
                                for p in (("total",) if INFLIGHT else PHASES)}
        else:
            user = new_drive(cur)
            out["users"] = [user]
            up = Upload(plan(kind, args))
            out["files"], out["blocks"], out["groups"] = len(up.files), len(up.blocks.sizes), len(up.groups())
            if INFLIGHT:
                measured(cur, out)("total", lambda: web_upload(user, up.blocks, up.groups(), INFLIGHT))
            else:
                upload(user, up.blocks, up.groups(), measured(cur, out))

    line = json.dumps(out)
    print(line)
    os.makedirs("results", exist_ok=True)
    with open(f"results/{label}.jsonl", "a") as fh:
        fh.write(line + "\n")


main()
