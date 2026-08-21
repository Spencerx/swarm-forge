# Issues

Handoff helper work on **main**. Six-pack, four-pack, and two-pack load `swarmforge/scripts` from this branch. Do not change the pack graph: agents still send `git_handoff` to the next role.

One phase at a time. Test by hand, then the next.

## Phases

**Open**

1. `--help` is usage, not a draft — implemented; waiting for a hand test
2. Git from the worktree; files on the project — implemented; waiting for a hand test
3. Infer role, fill HEAD, refuse a SHA that is not on the sender — implemented; waiting for a hand test
4. Helper fills the commit (no typed SHA) — folded into 3
5. Helper fills artifacts; infer role; project outbox; merge_and_process is a command — implemented; waiting for a hand test
6. Evidence is not a note — implemented; waiting for a hand test

**Live six-pack**

- Specifier window did not have `SWARMFORGE_ROLE` — folded into 3
- Specifier typed the 10-char SHA — folded into 3
- Coder still prefixes `SWARMFORGE_ROLE` on `ready_for_next.sh` / `done_with_current.sh` — folded into 5
- Coder queued on the worktree outbox — folded into 5
- Coder grepped `merge_and_process`, then `git merge` — folded into 5

---

### 1. `--help` is usage, not a draft

**Implemented. Waiting for a hand test.** Smallest change. Happy path stays `swarm_handoff.sh <draft-file>`.

Today `--help` is read as a missing file (`Draft file not found: --help`). Agents then invent a draft.

**Change:** `swarm_handoff.sh --help` and `-h` print usage and exit 0.

**Test:** from a pack worktree, `swarmforge/scripts/swarm_handoff.sh --help`. See usage, not “file not found.” A real draft still queues.

**Where:** `swarmforge/scripts/swarm_handoff.bb`; `handoffs.prompt` one line.

### 2. Git from the worktree; files on the project

**Implemented. Waiting for a hand test.** After 1.

The walk stall: the helper used master’s HEAD (a merge commit, `artifacts: none`). That SHA was not on the sender’s branch.

**Change:** commit and `diff-tree` come from the **current worktree** (`git rev-parse --show-toplevel`). Handoff outbox/inbox live on the **project** (where `.swarmforge/roles.tsv` is). Do not use `user.dir` for both.

**Test:** in a worktree, queue a git handoff. `commit:` is the worktree HEAD, not master. The queued file appears under the project `.swarmforge/handoffs`.

**Where:** `swarm_handoff.bb` `state-dir` / `git-root` / `project-root`.

### 3. Infer role, fill HEAD, refuse a SHA that is not on the sender

**Implemented. Waiting for a hand test.** After 2. Includes live specifier issues: missing `SWARMFORGE_ROLE`, typed SHA.

**Change:**
- If `SWARMFORGE_ROLE` is unset, infer the role from `roles.tsv` whose worktree is this cwd / git toplevel. Do not make the agent type the export.
- For `git_handoff`, fill `commit` from that worktree’s HEAD. Ignore a typed SHA in the draft.
- If that commit is not on the sender worktree (`merge-base --is-ancestor`), print one error and do not queue.

Draft still supplies `to`, `priority`, `task`. Notes still use a file. No-arg without a draft is later.

**Test:** (a) no env, cwd is the sender worktree → queues as that role. (b) draft names master’s SHA → queued file has worktree HEAD. (c) draft omits `commit` → filled from HEAD.

**Where:** `swarm_handoff.bb` `sender-role` / HEAD fill / reachability; `handoffs.prompt`; specifier pane.

### 4. Helper fills the commit (no typed SHA)

**Folded into 3.** Remaining later: no-arg send with no draft file (`--to` / last `TASK_NAME`).

### 5. Helper fills artifacts; infer role; project outbox; merge_and_process is a command

**Implemented. Waiting for a hand test.** After 4. Includes live coder issues: receive and complete still require `SWARMFORGE_ROLE`; queued on the worktree outbox; `merge_and_process` is not a command.

**Change:**
- Optional `artifacts:` from `diff-tree` of the filled commit. Old drafts without the field still work. Do not queue a merge commit that looks like no files changed.
- If `SWARMFORGE_ROLE` is unset, `ready_for_next.sh` and `done_with_current.sh` infer the role the same way `swarm_handoff.sh` does (`roles.tsv` worktree vs cwd). Do not make the agent type the export. `handoff_lib.bb` shares that infer.
- From a pack worktree, `HANDOFF QUEUED` is the **project** outbox (where `.swarmforge/roles.tsv` is). Never `.worktrees/<role>/.swarmforge/handoffs`. Phase 2 already queues on the project; this finishes the live leftover (worktree dirs still created / still printed).
- `merge_and_process` is a real helper. Recipients do not grep a fake command in the payload and invent `git merge`. Do not change the pack graph: agents still send `git_handoff` to the next role; the helper merges the inbound commit.

**Test:** (a) ordinary commit lists its files. Merge with empty `diff-tree` is refused or not `artifacts: none` pretending to be the work. (b) no env, cwd is a pack worktree → receive and complete run as that role. (c) from a pack worktree, queued path is the project outbox. (d) inbound `git_handoff` → `merge_and_process` (or `ready_for_next`) merges that commit; no agent-authored `git merge`.

**Where:** `swarm_handoff.bb`; `ready_for_next.bb`; `done_with_current.bb`; `handoff_lib.bb`; `merge_and_process` helper; `handoffs.prompt`.

### 6. Evidence is not a note

**Implemented. Waiting for a hand test.** After 5.

Git draft fields stay the short list. Do not send `type: note` to carry CRAP or coverage. If evidence is needed later, the helper writes it; the agent does not author extra headers.

**Test:** a git draft with `coverage:` is invalid. A note is still only `type` / `to` / `priority` / `message`, and only when we asked for a note.

**Where:** `swarm_handoff.bb` allowed fields; `handoffs.prompt`.

## Live six-pack (specifier)

### Specifier window did not have `SWARMFORGE_ROLE`

**Open.** Six-pack specifier in `~/junk/squad` committed, then `swarm_handoff.sh` refused until they ran `SWARMFORGE_ROLE=specifier swarmforge/scripts/swarm_handoff.sh wumpus-classic.handoff`.

The launcher writes `export SWARMFORGE_ROLE=specifier` in the tmux start line (`swarmforge.bb`). Codex still launched `swarm_handoff.sh` without that env. Helper requires it (`Set SWARMFORGE_ROLE.`).

**Proposed fix:** Codex (and other agents) must inherit `SWARMFORGE_ROLE` on every helper process, or the helper must infer the role from `roles.tsv` + cwd/worktree when the env is missing. Do not make the agent type the export.

**Where:** `swarmforge.bb` launch line; `swarm_handoff.bb` `sender-role`; specifier pane at handoff.

### Specifier typed the 10-char SHA

**Open.** Same session: after `git commit`, they ran `rev-parse --short=10`, wrote `commit: deb8ac060a` in a draft, then `swarm_handoff.sh <file>`. Current six-pack still requires that. That is how the wrong object gets in (walk stall on squad).

**Proposed fix:** phase 4 — helper fills `commit` from the sender worktree HEAD. Agent does not type the SHA.

**Where:** phase 4; specifier pane after `deb8ac0`; `wumpus-classic.handoff` draft.

## Live six-pack (coder)

### Coder prefixes `SWARMFORGE_ROLE` on receive and complete

**Implemented in phase 5.** Same env hole as specifier send. Phase 3 infers role on `swarm_handoff.sh`. `ready_for_next.sh` and `done_with_current.sh` still exit `Set SWARMFORGE_ROLE.` Live coder ran `SWARMFORGE_ROLE=coder swarmforge/scripts/ready_for_next.sh wumpus-classic` (old run).

**Proposed fix:** phase 5 — same infer as send (`roles.tsv` worktree vs cwd) in the receive and complete helpers.

**Where:** phase 5; `ready_for_next.bb`; `done_with_current.bb`; `handoff_lib.bb`.

### Coder queued on the worktree outbox

**Implemented in phase 5.** Live coder: `HANDOFF QUEUED: …/.worktrees/coder/.swarmforge/handoffs/outbox/…`. Phase 2 already queues on the project. This swarm’s scripts were fetched before that landed. Daemon still copied it, so this one got through.

**Proposed fix:** phase 5 — queued path is the project outbox. Do not write or print a worktree outbox.

**Where:** phase 5; `swarm_handoff.bb` `state-dir`; coder pane after `ba1afd8`.

### Coder grepped `merge_and_process`, then `git merge`

**Implemented in phase 5.** Delivered `git_handoff` body says `merge_and_process <sender> <commit>`. That is not a script. Constitution uses the same name. Live coder grepped, then ran `git merge`. Fine this time; agents will keep reconstructing it.

**Proposed fix:** phase 5 — `merge_and_process` is a real helper (or `ready_for_next` does the merge). Agent does not invent `git merge`. Pack graph unchanged.

**Where:** phase 5; `swarm_handoff.bb` body; `handoffs.prompt`; new helper or `ready_for_next.bb`.
