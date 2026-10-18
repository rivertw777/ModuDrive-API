""".docs/perf-test/004-upload.md section 5 — S3 HEAD latency and the commit's lookup on real S3, run as a one-off ECS task
next to storage-service (its subnets, security group and task role; command: pip install boto3 && python s3-head.py). The lookup is simulated as storage-service and file-service do it: each commit
HEADs its blocks <per> at a time (ChecksumMode ENABLED) through one shared bulkhead (40 seats, 1s wait); a HEAD refused
a seat fails the whole lookup, and file-service tries it again from the start (3 attempts, 0.5s then 1s apart).
Env BUCKET. Writes 1,280 small objects under blocks/bench-<run>/ and deletes them at the end."""
import hashlib, json, os, random, statistics, threading, time, uuid
from concurrent.futures import ThreadPoolExecutor
import boto3
from botocore.config import Config

BUCKET, N, SEATS, SEAT_WAIT = os.environ["BUCKET"], 1280, 40, 1.0
PREFIX = f"blocks/bench-{uuid.uuid4().hex[:8]}/"
s3 = boto3.client("s3", region_name="ap-northeast-2",
                  config=Config(max_pool_connections=256, retries={"max_attempts": 2, "mode": "standard"}))


def pct(xs, p):
    xs = sorted(xs)
    return round(xs[min(len(xs) - 1, int(len(xs) * p))], 1)


def put(_):
    body = os.urandom(1024)
    key = PREFIX + hashlib.sha256(body).hexdigest()
    s3.put_object(Bucket=BUCKET, Key=key, Body=body, ChecksumAlgorithm="SHA256")
    return key


def head(key):
    t = time.perf_counter()
    s3.head_object(Bucket=BUCKET, Key=key, ChecksumMode="ENABLED")
    return (time.perf_counter() - t) * 1000


def parallel(concurrency, keys):
    t = time.perf_counter()
    with ThreadPoolExecutor(concurrency) as pool:
        lat = list(pool.map(head, keys))
    total = (time.perf_counter() - t) * 1000
    return {"total_ms": round(total, 1), "per_sec": round(len(keys) / total * 1000), "p50": pct(lat, .5), "p99": pct(lat, .99)}


def lookups(commits, per, hashes, keys):
    """commits lookups at once, each of hashes HEADs, per at a time, all sharing SEATS seats."""
    seats = threading.BoundedSemaphore(SEATS)
    refused = [0]
    lock = threading.Lock()

    def attempt(ks):
        failed = threading.Event()

        def one(k):
            if failed.is_set():
                return  # storage-service stops sending once the answer is a failure
            if not seats.acquire(timeout=SEAT_WAIT):
                with lock:
                    refused[0] += 1
                failed.set()
                return
            try:
                head(k)
            finally:
                seats.release()
        with ThreadPoolExecutor(per) as pool:
            list(pool.map(one, ks))
        return not failed.is_set()

    def lookup(j):
        ks = [keys[(j * hashes + i) % len(keys)] for i in range(hashes)]
        t = time.perf_counter()
        for n, backoff in enumerate((0.5, 1.0, None), 1):
            if attempt(ks):
                return (time.perf_counter() - t) * 1000, n
            if backoff is None:
                return (time.perf_counter() - t) * 1000, -1
            time.sleep(backoff * random.uniform(0.5, 1.5))

    t = time.perf_counter()
    with ThreadPoolExecutor(commits) as pool:
        res = list(pool.map(lookup, range(commits)))
    times = [r[0] for r in res]
    return {"wall_ms": round((time.perf_counter() - t) * 1000, 1), "median_ms": round(statistics.median(times), 1),
            "max_ms": round(max(times), 1), "retried": sum(1 for r in res if r[1] > 1),
            "failed": sum(1 for r in res if r[1] == -1), "refused": refused[0]}


def main():
    with ThreadPoolExecutor(64) as pool:
        keys = list(pool.map(put, range(N)))
    try:
        with ThreadPoolExecutor(64) as pool:
            list(pool.map(head, keys[:256]))  # warm the connections
        out = {"single": None, "parallel": {}, "lookups": []}
        seq = [head(k) for k in keys[:300]]
        out["single"] = {"p50": pct(seq, .5), "p90": pct(seq, .9), "p99": pct(seq, .99)}
        for c in (1, 8, 16, 32, 40, 64):
            out["parallel"][c] = parallel(c, keys if c > 1 else keys[:300])
        for commits, hashes in ((1, 1280), (10, 100), (10, 1280), (30, 100)):
            for per in (8, 32):
                runs = [lookups(commits, per, hashes, keys) for _ in range(3)]
                mid = sorted(runs, key=lambda r: r["wall_ms"])[1]
                out["lookups"].append({"commits": commits, "hashes": hashes, "per": per, **mid,
                                       "walls": [r["wall_ms"] for r in runs]})
        print("BENCH " + json.dumps(out), flush=True)
    finally:
        for i in range(0, len(keys), 1000):
            s3.delete_objects(Bucket=BUCKET, Delete={"Objects": [{"Key": k} for k in keys[i:i + 1000]], "Quiet": True})
        print("BENCH cleaned " + PREFIX, flush=True)


main()
