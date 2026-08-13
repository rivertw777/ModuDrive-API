---
name: flow
disable-model-invocation: true
description: >-
  Run the full git workflow end-to-end for this repo (ModuDrive-API) — infer everything from
  the current working tree and ship it: create a GitHub issue, branch, verify the Gradle build,
  run security and code review in parallel, commit, push, and open a PR to dev. Fully autonomous
  once started, asks no questions. Manual-only: invoke by typing /flow or explicitly asking for
  it by name — do not trigger this automatically from a natural-language request like "commit
  this" or "open a PR", since it has irreversible side effects (issue/branch/commit/push/PR).
---

# Flow

Run the full git workflow end-to-end by inferring everything from the current working tree. Do not ask the user any questions. Do not stop between steps to report progress or wait for confirmation — after each step (including agent sub-tasks like the security/code review) completes, immediately continue to the next step in the same turn. Only stop early if a step's own stop condition below says to.

**Language rules:**
- All code, file names, commit messages, branch names, and PR titles must be written in **English**.
- GitHub issue body and PR body must be written in **Korean**.

1. Run `git diff HEAD` and `git status` to understand what changed and why.
2. From the diff, infer: issue type (`bug | feature | refactor | docs | chore`), a concise issue title, the appropriate branch prefix and slug, the commit type and an optional scope (`member | auth | gateway | file | storage | notification | eureka | common`; omit for repo-level changes like `.github/*` or `.claude/*` that don't map to any scope), and a commit subject (lowercase verb, under 50 chars). Follow the conventions in CONTRIBUTING.md.
3. Create a GitHub issue. Write the body to a temp file first and pass it with `--body-file` (never inline it with `--body "..."` — finding text and generated summaries can contain backticks/`$(...)` that the shell would evaluate): `gh issue create --title "[type] title" --body-file <path>`. The title is in English; the body is written in Korean and summarizes what the issue is about. Note the issue number.
4. Run `git checkout dev && git pull origin dev && git checkout -b <prefix>/<issue-number>-<slug>`. Exception: `chore` branches omit the issue number (`chore/<slug>`), per CONTRIBUTING.md's branch table.
5. Run `./gradlew build` to verify the change compiles and all tests pass. If it fails, fix the underlying issue and rerun, up to 3 attempts total. If it still fails after 3 attempts, stop and report the failure to the user instead of continuing.
6. In the same message, launch both the `oh-my-claudecode:security-reviewer` and `oh-my-claudecode:code-reviewer` agents in parallel via the Agent tool (`run_in_background: false`, current diff as context) — security review and code review respectively. Do NOT invoke `/security-review` or `/code-review` directly — those skills force their reply to be only the markdown report, which ends your turn and breaks this workflow; agent reports come back as tool results instead, so you continue immediately in the same turn. Both agents surface every finding including low-severity ones by design (they don't self-filter) — this is expected, not a failure. Fix cycle: fix only CRITICAL/HIGH findings (or anything a reviewer explicitly marks as blocking / REQUEST CHANGES); list any remaining MEDIUM/LOW findings in the PR body in step 9 instead of blocking on them. If a fix changes code, rerun `./gradlew build`. Repeat the fix cycle up to 3 attempts total (this budget is independent of step 5's). If CRITICAL/HIGH findings still remain after 3 attempts, stop and report them to the user instead of continuing.
7. Stage only the files touched by this change (from the step 1 diff/status) — not `git add .`, since the working tree may carry unrelated changes that shouldn't ride along in this commit. Commit, including `Closes #<issue-number>` in the body.
8. Push with `git push -u origin <branch>`.
9. Create a PR to `dev`. Write the body to a temp file first and pass it with `--body-file` (never inline it with `--body "..."` — finding text can contain backticks/`$(...)` that the shell would evaluate): `gh pr create --base dev --body-file <path>`. The PR title is in English (same format as the commit message). The PR body is written in Korean, includes a summary of changes, `Closes #<issue-number>`, and any unresolved MEDIUM/LOW findings from step 6. Print the PR URL.
10. Run `git checkout dev` to return to the `dev` branch.
