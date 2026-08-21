# Issues

Live six-pack specifier session (`htw-console-app`, commit `566b956`). Handoff ceremony worked (no typed SHA, no `SWARMFORGE_ROLE=`, project outbox). These are leftover fumbles.

## Open

(none)

## Implemented

- Specifier hunts the whole home for APS tools — implemented; waiting for a hand test
- Commit message is missing the role byline — implemented; waiting for a hand test
- Temp files go to `/tmp` and the handoff outbox — implemented; waiting for a hand test

---

### Specifier hunts the whole home for APS tools

**Implemented. Waiting for a hand test.** Specifier ran `find /Users/unclebob -name ir-dry-checker` and searched `*Acceptance*` directories. Permission errors on Photos/Downloads. Found `~/.local/bin/ir-dry-checker`. Did not use a project-local require/ensure.

Same hunt as the previous specifier. The role prompt says use `ir-dry-checker`; it does not say where it lives or the exact argv. Agents reconstruct a search.

**Change:** assignment Tool Startup names `swarm_tool.sh require` / `ensure` and the two-arg forms (`gherkin-parser <feature> ./tmp/<stem>.json`, `ir-dry-checker <ir> ./tmp/<stem>.dry.json`). Do not make the agent find binaries under `$HOME`.

**Where:** generated `.swarmforge/prompts/<role>.md` Tool Startup; `swarm_tool.sh`; `engineering.prompt`.

### Commit message is missing the role byline

**Implemented. Waiting for a hand test.** Commit `566b956` message is `Specify Hunt the Wumpus console app`. Constitution `workflow.prompt` requires `By <role>.` in every commit (`By specifier.`).

**Change:** keep the byline rule. A commit-msg hook infers the role (env or `roles.tsv` worktree) and appends `By <role>.` when it is missing. Do not make the agent remember a trailer.

**Where:** `commit-msg-hook.bb`; installed at swarm startup; `workflow.prompt` Commit Messages.

### Temp files go to `/tmp` and the handoff outbox

**Implemented. Waiting for a hand test.** Final parse/dry-check wrote into `/tmp`. Constitution: use `./tmp/` in the assigned worktree, not `/tmp`. The git_handoff draft was created under `.swarmforge/handoffs/outbox/tmp/` (queued anyway).

**Change:** Tool Startup shows parse and dry-check into `./tmp/…`. Drafts live in `./tmp/` as well. `swarm_handoff.sh` rejects `/tmp` and the handoff outbox as scratch.

**Where:** `workflow.prompt` Temporary Files; specifier Tool Startup; `swarm_handoff.bb` draft path check.
