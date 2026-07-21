#!/usr/bin/env python3
"""Stop hook: save this turn's assistant response to .claude/answer/NNN_slug.md.

Implements the "Answer Logging Rule" in CLAUDE.md without relying on the
model remembering to write the file every turn.
"""
import json
import os
import re
import sys
from datetime import datetime

PROJECT_ROOT = os.environ.get("CLAUDE_PROJECT_DIR") or os.getcwd()
ANSWER_DIR = os.path.join(PROJECT_ROOT, ".claude", "answer")
STATE_FILE = os.path.join(ANSWER_DIR, ".last_saved_uuid")


def load_entries(transcript_path):
    entries = []
    with open(transcript_path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                entries.append(json.loads(line))
            except json.JSONDecodeError:
                continue
    return entries


def is_real_user_prompt(entry):
    # isMeta marks synthetic user-role turns (skill bodies, injected reminders) —
    # they carry a 'text' block too and must not be mistaken for a turn boundary.
    if entry.get("isMeta"):
        return False
    msg = entry.get("message", {})
    if msg.get("role") != "user":
        return False
    content = msg.get("content")
    if isinstance(content, str):
        return bool(content.strip())
    if isinstance(content, list):
        return any(isinstance(b, dict) and b.get("type") == "text" for b in content)
    return False


def collect_response(entries):
    """Return (text, last_assistant_uuid) for the most recent turn."""
    texts = []
    last_uuid = None
    for entry in reversed(entries):
        if entry.get("isSidechain"):
            continue
        if entry.get("type") == "user" and is_real_user_prompt(entry):
            break
        if entry.get("type") != "assistant":
            continue
        if last_uuid is None:
            last_uuid = entry.get("uuid")
        content = entry.get("message", {}).get("content")
        if isinstance(content, list):
            for block in content:
                if isinstance(block, dict) and block.get("type") == "text":
                    text = block.get("text", "").strip()
                    if text:
                        texts.append(text)
    texts.reverse()
    return "\n\n".join(texts), last_uuid


def next_number():
    if not os.path.isdir(ANSWER_DIR):
        return 1
    max_n = 0
    for name in os.listdir(ANSWER_DIR):
        m = re.match(r"^(\d{3})_", name)
        if m:
            max_n = max(max_n, int(m.group(1)))
    return max_n + 1


def make_slug(text):
    first_line = next((l.strip() for l in text.splitlines() if l.strip()), "")
    words = re.findall(r"[a-zA-Z0-9]+", first_line)[:6]
    slug = "-".join(w.lower() for w in words)
    if not slug:
        # ponytail: non-ascii-only responses (e.g. Korean) fall back to a timestamp slug
        slug = "response-" + datetime.now().strftime("%Y%m%d-%H%M%S")
    return slug[:50]


def already_saved(uuid):
    if not uuid or not os.path.isfile(STATE_FILE):
        return False
    with open(STATE_FILE, "r", encoding="utf-8") as f:
        return f.read().strip() == uuid


def mark_saved(uuid):
    if uuid:
        with open(STATE_FILE, "w", encoding="utf-8") as f:
            f.write(uuid)


def main():
    try:
        payload = json.load(sys.stdin)
    except (json.JSONDecodeError, ValueError):
        payload = {}

    transcript_path = payload.get("transcript_path")
    if not transcript_path or not os.path.isfile(transcript_path):
        return

    entries = load_entries(transcript_path)
    text, last_uuid = collect_response(entries)
    if not text or already_saved(last_uuid):
        return

    os.makedirs(ANSWER_DIR, exist_ok=True)
    filename = f"{next_number():03d}_{make_slug(text)}.md"
    with open(os.path.join(ANSWER_DIR, filename), "w", encoding="utf-8") as f:
        f.write(text + "\n")
    mark_saved(last_uuid)


if __name__ == "__main__":
    main()
