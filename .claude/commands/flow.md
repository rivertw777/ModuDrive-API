Run the full git workflow end-to-end by inferring everything from the current working tree. Do not ask the user any questions.

**Language rules:**
- All code, file names, commit messages, branch names, and PR titles must be written in **English**.
- GitHub issue body and PR body must be written in **Korean**.

1. Run `git diff HEAD` and `git status` to understand what changed and why.
2. From the diff, infer: issue type (`bug | feature | refactor | docs | chore`), a concise issue title, the appropriate branch prefix and slug, the commit type and scope (`member | auth | gateway | eureka | common`), and a commit subject (lowercase verb, under 50 chars). Follow the conventions in CONTRIBUTING.md.
3. Create a GitHub issue with `gh issue create --title "[type] title" --body "..."`. The title is in English; the body is written in Korean and summarizes what the issue is about. Note the issue number.
4. Run `git checkout dev && git pull origin dev && git checkout -b <prefix>/<issue-number>-<slug>`.
5. Run `./gradlew build` to verify the change compiles and all tests pass. If it fails, fix the underlying issue and rerun, up to 3 attempts total. If it still fails after 3 attempts, stop and report the failure to the user instead of continuing.
6. Run `/security-review` against the current diff. Fix any findings they surface; if a fix changes code, rerun `./gradlew build`. Repeat this cycle up to 3 attempts total. If findings still remain after 3 attempts, stop and report the remaining findings to the user instead of continuing.
7. Stage all changes with `git add .` and commit. Include `Closes #<issue-number>` in the commit body.
8. Push with `git push -u origin <branch>`.
9. Create a PR to `dev` with `gh pr create`. The PR title is in English (same format as the commit message). The PR body is written in Korean and includes a summary of changes and `Closes #<issue-number>`. Print the PR URL.
10. Run `git checkout dev` to return to the `dev` branch.
