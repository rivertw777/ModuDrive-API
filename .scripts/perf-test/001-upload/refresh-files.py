""".docs/perf-test/001-upload.md 4 — rewrites every file under the given folder with new random bytes, same names and
sizes, so the next browser round isn't skipped by block dedup (a re-upload of the same content sends nothing).
Usage: python3 refresh-files.py "/mnt/c/Users/<user>/Desktop/업로드-테스트" """
import os, sys

CHUNK = 4 * 1024 * 1024
for folder, _, names in os.walk(sys.argv[1]):
    for name in names:
        path = os.path.join(folder, name)
        left = os.path.getsize(path)
        with open(path, "r+b") as f:
            while left:
                n = min(CHUNK, left)
                f.write(os.urandom(n))
                left -= n
