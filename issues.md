# Issues

Handoff helper work on **main**. Six-pack, four-pack, and two-pack load `swarmforge/scripts` from this branch. Do not change the pack graph: agents still send `git_handoff` to the next role.

One phase at a time. Test by hand, then the next.

## Phases

**Open**

1. `--help` is usage, not a draft — implemented; waiting for a hand test
2. Git from the worktree; files on the project — implemented; waiting for a hand test
3. Bad SHA is not queued
4. Helper fills the commit (no typed SHA)
5. Helper fills artifacts
6. Evidence is not a note

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

### 3. Bad SHA is not queued

**Open. After 2.**

If `commit` is not on the sender’s worktree branch, do not put a file in `in_process` for the next role to retry forever.

**Change:** at queue time, if the SHA is not reachable from the sender’s branch, print one error and move nothing into the inbox (or put it in `failed/` with the reason). Residual/receive must not loop the same bad file.

**Test:** draft with a SHA that is on master only. Helper exits nonzero. No new inbox item. A SHA that *is* on the worktree still queues.

**Where:** `swarm_handoff.bb`; handoff daemon `in_process` / `failed`.

### 4. Helper fills the commit (no typed SHA)

**Open. After 3.** Keep the draft path for notes.

**Change:** after commit, `swarm_handoff.sh` with no file (or `--to <next-role>`) fills `commit` from this worktree’s HEAD. `to` and `task` still come from the role / last `TASK_NAME`. `swarm_handoff.sh <draft-file>` still works.

**Test:** commit on the worktree, no-arg (or `--to`) handoff. Queued file has that HEAD. Notes still use a file.

**Where:** `swarm_handoff.bb`; `handoffs.prompt` (add no-arg; do not remove the file form).

### 5. Helper fills artifacts

**Open. After 4.**

**Change:** optional `artifacts:` from `diff-tree` of the filled commit. Old drafts without the field still work. Do not queue a merge commit that looks like no files changed.

**Test:** ordinary commit lists its files. Merge with empty `diff-tree` is refused or not `artifacts: none` pretending to be the work.

**Where:** `swarm_handoff.bb`.

### 6. Evidence is not a note

**Open. After 5.**

Git draft fields stay the short list. Do not send `type: note` to carry CRAP or coverage. If evidence is needed later, the helper writes it; the agent does not author extra headers.

**Test:** a git draft with `coverage:` is invalid. A note is still only `type` / `to` / `priority` / `message`, and only when we asked for a note.

**Where:** `swarm_handoff.bb` allowed fields; `handoffs.prompt`.
