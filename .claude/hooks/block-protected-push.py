#!/usr/bin/env python3
"""PreToolUse hook: block direct `git push` to protected branches (dev, prod).

CONTRIBUTING.md requires all changes to go through a PR - no direct push to
dev or prod. This inspects Bash tool calls for `git push` invocations and
denies ones that target a protected branch directly (explicitly, or
implicitly via the currently checked-out branch).
"""
import json
import re
import shlex
import subprocess
import sys

PROTECTED_BRANCHES = {"dev", "prod"}


def strip_ref_prefix(ref):
    for prefix in ("refs/heads/", "heads/"):
        if ref.startswith(prefix):
            return ref[len(prefix):]
    return ref


def current_branch():
    try:
        result = subprocess.run(
            ["git", "rev-parse", "--abbrev-ref", "HEAD"],
            capture_output=True, text=True, timeout=5,
        )
        branch = result.stdout.strip()
        return branch or None
    except Exception:
        return None


def target_branches(segment):
    match = re.search(r"\bgit\s+push\b(.*)", segment, re.DOTALL)
    rest = match.group(1) if match else ""
    try:
        tokens = shlex.split(rest)
    except ValueError:
        tokens = rest.split()

    if any(t in ("--all", "--mirror") for t in tokens):
        return {"*"}

    non_flags = [t for t in tokens if not t.startswith("-")]

    if len(non_flags) <= 1:
        # bare `git push` or `git push <remote>` -> pushes current branch
        cur = current_branch()
        return {cur} if cur else set()

    targets = set()
    for refspec in non_flags[1:]:
        refspec = refspec.lstrip("+")
        if ":" in refspec:
            _, dst = refspec.split(":", 1)
            dst = dst.strip()
        else:
            dst = refspec
        if dst in ("", "HEAD"):
            cur = current_branch()
            if cur:
                targets.add(cur)
            continue
        targets.add(strip_ref_prefix(dst))
    return targets


def main():
    try:
        payload = json.load(sys.stdin)
    except json.JSONDecodeError:
        return allow()

    command = payload.get("tool_input", {}).get("command", "")
    if not command:
        return allow()

    for segment in re.split(r"&&|\|\||;|\|", command):
        segment = segment.strip()
        if not re.search(r"(^|[\s(])git\s+push(\s|$)", segment):
            continue

        targets = target_branches(segment)
        if "*" in targets:
            return deny("all branches (--all/--mirror)")
        hit = targets & PROTECTED_BRANCHES
        if hit:
            return deny(sorted(hit)[0])

    return allow()


def allow():
    print(json.dumps({}))
    sys.exit(0)


def deny(branch):
    print(json.dumps({
        "hookSpecificOutput": {
            "hookEventName": "PreToolUse",
            "permissionDecision": "deny",
            "permissionDecisionReason": (
                f"Direct push to '{branch}' is blocked. CONTRIBUTING.md requires "
                "all changes to go through a PR - create a branch and open a PR "
                "instead (see the /flow skill)."
            ),
        }
    }))
    sys.exit(0)


if __name__ == "__main__":
    main()
