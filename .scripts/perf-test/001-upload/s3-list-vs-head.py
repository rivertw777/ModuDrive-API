"""Spec 001 2-3 — block lookup on real S3: HEAD each hash vs listing the owner's keys, as
storage-service does it (S3StorageAdapter.uploaded), for a commit of N hashes in an owner prefix that already holds M
other blocks. Run as a one-off ECS task next to storage-service (its subnets, security group and task role; command:
pip install boto3 && python s3-list-vs-head.py). Env BUCKET. Writes up to 21,280 small objects under
blocks/bench-<run>/ and deletes them at the end.

HEAD: every hash, 8 at a time (LOOKUP_PARALLELISM), ChecksumMode ENABLED.
LIST: the hashes split by first hex digit, a ListObjectsV2 per range from just before its smallest hash, ranges 8 at a
time, a shared page budget of ranges + N/4; ranges the budget runs out on fall back to HEAD."""
import hashlib, json, os, statistics, threading, time, uuid
from concurrent.futures import ThreadPoolExecutor
import boto3
from botocore.config import Config

BUCKET = os.environ["BUCKET"]
PREFIX = f"blocks/bench-{uuid.uuid4().hex[:8]}/"
PAR, HASHES_PER_PAGE, ROUNDS = 8, 4, 3
s3 = boto3.client("s3", region_name="ap-northeast-2",
                  config=Config(max_pool_connections=128, retries={"max_attempts": 2, "mode": "standard"}))


def put(_):
    body = os.urandom(64)
    h = hashlib.sha256(body).hexdigest()
    s3.put_object(Bucket=BUCKET, Key=PREFIX + h, Body=body, ChecksumAlgorithm="SHA256")
    return h


def put_many(n):
    with ThreadPoolExecutor(100) as pool:
        return list(pool.map(put, range(n)))


def head(h):
    s3.head_object(Bucket=BUCKET, Key=PREFIX + h, ChecksumMode="ENABLED")
    return 1


def by_head(hashes):
    with ThreadPoolExecutor(PAR) as pool:
        return {"head": sum(pool.map(head, hashes)), "list": 0}


def by_list(hashes):
    ranges = {}
    for h in hashes:
        ranges.setdefault(h[0], []).append(h)
    ranges = [sorted(r) for r in ranges.values()]
    budget = [len(ranges) + len(hashes) // HASHES_PER_PAGE]
    lock, pages = threading.Lock(), [0]

    def list_range(r):
        first, last, token = PREFIX + r[0], PREFIX + r[-1], None
        while True:
            with lock:
                budget[0] -= 1
                if budget[0] < 0:
                    return r  # unlisted: HEAD these
                pages[0] += 1
            kw = {"Bucket": BUCKET, "Prefix": PREFIX + r[0][0]}
            kw.update({"ContinuationToken": token} if token else {"StartAfter": first[:-1]})
            page = s3.list_objects_v2(**kw)
            contents = page.get("Contents", [])
            if not page.get("IsTruncated") or not contents or contents[-1]["Key"] >= last:
                return []
            token = page["NextContinuationToken"]

    with ThreadPoolExecutor(PAR) as pool:
        unlisted = [h for left in pool.map(list_range, ranges) for h in left]
    heads = by_head(unlisted)["head"] if unlisted else 0
    return {"head": heads, "list": pages[0]}


def measure(fn, hashes):
    out = []
    for _ in range(ROUNDS):
        t = time.perf_counter()
        calls = fn(hashes)
        out.append({"ms": round((time.perf_counter() - t) * 1000), **calls})
    return {"ms_median": statistics.median(o["ms"] for o in out), "rounds": out}


targets = put_many(1280)
stored = 0
results = []
for others in (0, 2000, 20000):
    put_many(others - stored)
    stored = others
    for n in (100, 1280):
        hashes = targets[:n]
        results.append({"other_blocks": others, "hashes": n,
                        "head": measure(by_head, hashes), "list": measure(by_list, hashes)})
        print("RESULT", json.dumps(results[-1]), flush=True)

# clean up
keys, token = [], None
while True:
    page = s3.list_objects_v2(Bucket=BUCKET, Prefix=PREFIX, **({"ContinuationToken": token} if token else {}))
    keys += [o["Key"] for o in page.get("Contents", [])]
    if not page.get("IsTruncated"):
        break
    token = page["NextContinuationToken"]
for i in range(0, len(keys), 1000):
    s3.delete_objects(Bucket=BUCKET, Delete={"Objects": [{"Key": k} for k in keys[i:i + 1000]], "Quiet": True})
print("DONE", json.dumps(results), flush=True)
