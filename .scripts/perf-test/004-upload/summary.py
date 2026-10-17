""".docs/perf-test/004-upload.md — median of each phase over a label's rounds. Usage: summary.py <label>..."""
import json, statistics, sys
for label in sys.argv[1:]:
    rows = [json.loads(l) for l in open(f"results/{label}.jsonl")]
    print(label, f"({len(rows)} rounds)")
    for phase in ("commit1", "presign", "put", "commit2"):
        med = lambda k: statistics.median(r[phase][k] for r in rows)
        print(f"  {phase:8} {med('ms'):9.1f} ms  db {med('db'):6.0f}  redis {med('redis'):5.0f}")
