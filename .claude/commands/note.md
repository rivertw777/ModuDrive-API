Sync the Obsidian vault's ModuDrive-API "data model" and "feature" notes against the current source in this repo. Do not ask the user questions; report a summary at the end instead.

Vault root: `ModuDrive/1. Projects/ModuDrive-API`. Use the `mcp__obsidian__*` tools (search_notes / read_note / write_note / patch_note) to read and write notes — not raw filesystem access.

## Scope
- `1. data model/<service>/` — one note per JPA entity or domain model (`services/*/src/main/java/.../adapter/out/persistence/*JpaEntity.java`, plus in-memory/runtime models for auth-service and storage-service).
- `2. feature/<service>/` — one note per REST endpoint (one `@UseCase` behind each `@WebAdapter` controller method).

## Steps
1. Read both top-level `0. 목차.md` files and each service's `0. 목차.md` to see what's already documented.
2. For each service under `services/`, find current entities/domain models (`*JpaEntity.java`, `domain/model/*.java`) and endpoints (`@WebAdapter` controllers, `@GetMapping`/`@PostMapping`/`@PutMapping`/`@DeleteMapping`).
3. Diff against the vault: new entity/endpoint → new note; source changed (fields, flow, validation) → update the existing note; note exists but source is gone → flag it in the summary, don't delete.
4. Before writing a new note, read one existing note in the same folder and match its structure exactly (heading order, wikilink back to the service 목차, Korean prose, mermaid sequence diagram for features). Don't invent fields or steps — base every line on the actual source file.
5. If a note was added, add a one-line entry (wikilink + short description, matching the existing entries' style) to that service's `0. 목차.md`, and to the top-level `0. 목차.md` if the service is new there.
6. Report per service: notes added, notes updated, notes flagged as stale — one line each.

Only touch `1. data model/` and `2. feature/` under this project's vault folder.
