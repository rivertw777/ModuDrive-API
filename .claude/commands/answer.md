Save your immediately preceding response (the assistant message right before this command was invoked) as a Markdown file under `.claude/answer/` in the project root. Do not ask the user any questions.

1. Check `.claude/answer/` for existing files matching `NNN_*.md` and take the highest `NNN` found; use `001` if the directory is empty or missing.
2. Derive a short kebab-case slug (a few words) summarizing the topic of the response being saved.
3. Write the full text of that previous response, unmodified, into `.claude/answer/<NNN>_<slug>.md` (e.g. `001_datasource-proxy-setup.md`).
4. Report the saved file path to the user.
