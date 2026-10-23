""".docs/perf-test/001-upload.md — median over a label's rounds. Usage: summary.py <label>..."""
import json, statistics, sys

PHASES = ("commit1", "presign", "put", "commit2")
med = statistics.median
for label in sys.argv[1:]:
    rows = [json.loads(l) for l in open(f"results/{label}.jsonl")]
    print(label, f"({len(rows)} rounds)")
    if rows[0]["kind"] == "concurrent":
        print(f"  wall {med(r['wall_ms'] for r in rows):9.1f} ms  db {med(r['db'] for r in rows):6.0f}"
              f"  redis {med(r['redis'] for r in rows):5.0f}")
        for p in PHASES:
            print(f"  {p:8} per drive median {med(r['per_drive'][p]['median'] for r in rows):9.1f} ms"
                  f"  max {med(r['per_drive'][p]['max'] for r in rows):9.1f} ms")
        continue
    for p in (p for p in PHASES if p in rows[0]):
        print(f"  {p:8} {med(r[p]['ms'] for r in rows):9.1f} ms  db {med(r[p]['db'] for r in rows):6.0f}"
              f"  redis {med(r[p]['redis'] for r in rows):5.0f}")
