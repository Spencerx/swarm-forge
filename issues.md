# Stop get-swarm-forge from overwriting host-project files

## Problem

`get-swarm-forge` validates a few pack pieces, then `rm -rf swarmforge
.worktrees .swarmforge` and `cp -R "$pack_dir"/. .` into the current
directory.

The `rm` is a SwarmForge reset. The copy is not. A pack branch is a full
git tree, not a thin overlay. `two-pack` currently ships `.gitignore`,
`README.md`, `bb.edn`, and `test/`. Those land on the host and overwrite
whatever was there.

## Behavior

- Compose and fully validate an allowlisted overlay in the temporary
  directory.
- Copy only SwarmForge-owned paths into the current directory (`swarm`,
  `swarmforge/…`).
- Do not copy pack-branch `.gitignore`, `README.md`, `bb.edn`, `test/`,
  or other host-project files.
- Keep the existing reset of `swarmforge`, `.worktrees`, and
  `.swarmforge`.

## Verification

Cover install into a directory that already has `README.md`, `bb.edn`,
and a test file: those files must be unchanged after `get-swarm-forge`.
SwarmForge-owned paths (`swarm`, `swarmforge/scripts`,
`swarmforge/roles`, `swarmforge/swarmforge.conf`) must still be installed
from the pack plus `main`. Do not pin prompt wording.

# Stop pack-branch constitution from overlaying main

## Problem

`get-swarm-forge` copies constitution articles from `main`, then copies
the whole pack branch on top. Shared article files that still live on
the pack branch replace the copies just taken from `main`.

`two-pack` currently overwrites `engineering.prompt` and
`workflow.prompt`. Those pack copies omit later rules: `swarm_tool.sh`
require/ensure, APS `ir-dry-checker` and two-arg forms, serial
constitution tools, worker limits, differential mutation, commit
bylines, and `./tmp/` scratch rules. `handoffs.prompt` survives only
because `two-pack` does not ship that file.

Pack-specific files (`constitution.prompt`, `articles/project.prompt`,
`local-*.prompt`) are legitimate overlay. Shared articles are not.

The README currently allows a pack to replace a shared article by
committing the same filename. That is the mechanism that lets stale
pack copies overwrite `main`.

## Behavior

- Constitution articles from `main` (or `SWARMFORGE_BASE_BRANCH`) are
  the law for every pack.
- A pack must not ship `engineering.prompt`, `workflow.prompt`,
  `handoffs.prompt`, or any other shared article filename.
- Pack-specific rules go in `local-*.prompt` (additive: extra
  requirements and exceptions, not a full replacement) and in pack-owned
  files `main` does not own (`constitution.prompt`,
  `articles/project.prompt`, role prompts, `swarmforge.conf`).
- Move any two-pack-only rules out of the stale shared copies into
  `local-*.prompt` or `project.prompt`, then delete those stale copies
  from the pack branch.
- Do not treat a same-name pack file as an override of a shared article.
  Drop that README rule. `local-*.prompt` is the only pack specialization.
- `get-swarm-forge` copies shared articles from `main` and overlays only
  pack-owned files. Longer term, store packs as directories
  (`packs/two-pack/`) instead of branches that look like the whole
  project.

## Verification

Cover compose from `main` plus `two-pack`: after install,
`engineering.prompt`, `workflow.prompt`, and `handoffs.prompt` match
`main`; two-pack does not contain those shared filenames; pack-specific
`project.prompt` / `local-*.prompt` / role prompts / `swarm` are present.
A host file outside the SwarmForge allowlist is unchanged. Do not pin
prompt wording.

# Move pack_web --test-* commands out of the production script

## Problem

`pack_web.bb` is the dashboard server and also a test harness. About 27
`--test-*` commands (`--test-state`, `--test-html`, `--test-save-comments`,
heat/status pane stubs, …) live in the same file as HTTP, tmux, board,
approvals, git, and teardown. Those flags are not used by agents or the
dashboard. They exist so Clojure tests can drive `handle-request` without
speaking HTTP.

## Behavior

- `pack_web.bb` production `-main` serves the dashboard (`--serve`).
  It does not run `-main` when another script loads the file.
- A sibling script (e.g. `pack_web_test.bb`) `load-file`s `pack_web.bb`
  and owns the `--test-*` commands. It calls the same functions
  (`handle-request`, `*pane-text*`); it does not copy board, approval, or
  git logic.
- Tests still go through a CLI. `pack_web.sh` may dispatch `--test-*` to
  the sibling so existing `pack_web.sh --test-state` callers stay valid.
- Do not introduce a `src/swarmforge` library or in-process requires of
  production namespaces as the test API.

## Verification

Cover: `pack_web.bb` has no `--test-*` cases; the sibling still implements
the current flags; existing pack-ui tests that invoke `pack_web.sh
--test-state` (and the other flags) still pass; `--serve` still serves.
Do not pin prompt wording.

# Use handoff_lib for shared root, role, and receive-mode

## Problem

`handoff_lib.bb` already implements project-root discovery, `roles.tsv`
rows, role inference, and receive-mode (blank column 7 defaults to
`task`). Most helpers reimplement that instead of `load-file`ing it.

`ready_for_next.bb` and `done_with_current.bb` are the same ~81-line
dispatcher with different helper names. Their copies of receive-mode
diverge: a present-but-empty mode column is "unknown role" there, and
`"task"` in `handoff_lib`.

## Behavior

- `ready_for_next.bb` and `done_with_current.bb` `load-file`
  `handoff_lib.bb` and dispatch on `role-receive-mode`. They do not
  duplicate git-root / project-root / roles parsing.
- Blank receive-mode is `"task"` everywhere, matching `handoff_lib`.
- Other helpers that copy the same root/role discovery
  (`swarm_handoff.bb`, `pack_board.bb`, `ready_for_next_guard.bb`,
  `pack_dashboard_request.bb`, `swarm_tool.bb`) should load the lib
  rather than keep a private copy, unless a copy is already a thin
  wrapper.
- Do not add a `src/swarmforge` classpath library. Shared code stays a
  script next to the helpers, loaded by those helpers.
- Do not fold inbox task/batch transitions or every timestamp into the
  lib in this change.

## Verification

Cover: a role with a blank receive-mode column is treated as `task` by
`ready_for_next` and `done_with_current`; batch vs task still dispatch
to the existing `*_batch.sh` / `*_task.sh` helpers; unknown role still
fails. Cover one other helper that used to copy `project-root` now
agreeing with `handoff_lib` on a worktree whose git common dir is the
pack root. Do not pin prompt wording.

# Resolve git_handoff task names without treating them as task_id

## Problem

A specifier draft with `type`, `to`, `priority`, and `task: HTW` is
rejected: `task_id HTW does not match current in-process task_id
20260826T…-htw`.

`swarm_handoff` is supposed to fill the hidden `task_id` from current
work or the board card. Instead `fill-task-id` copies the visible `task`
name into `task_id`. `with-board-task` only fills from a **batch**
in-process dir, not a single in-process note (New Task or retry). The
mismatch check then compares the card name to the hidden id.

Usage lists `task_id:` as a draft field. Tool Startup still tells the
agent to write only `task:` (the card name) and not invent ids. The
existing test covers a draft with the *wrong* `task_id`, not a legal
name-only draft against in-process work.

## Behavior

- Do not copy `task` into `task_id`.
- Resolve `task_id` from current in-process work (task or batch), else
  the board card whose name matches `task:`.
- A git_handoff draft stays `type`, `to`, `priority`, `task:`. The
  helper still writes `task_id` on the queued file.
- Usage must not tell the agent to type `task_id`.
- A draft that names a `task_id` other than the current in-process id
  is still rejected (stale-task protection stays).

## Verification

Cover: in-process note with `task_id` `2026…-htw` and `task: HTW`; draft
has `task: HTW` and no `task_id`; `swarm_handoff` queues with the hidden
id and does not report a mismatch. Cover a draft with a different
`task_id` still rejected. Cover usage/help not requiring `task_id` in
the draft. Do not pin prompt wording.

# Log inject and teardown failures; leave pane capture quiet

## Problem

Several helpers swallow exceptions so the dashboard or agent can keep
going. That is correct for pane capture. It is not correct for tmux
inject and async teardown, which can report success while the work
failed.

`inject-role!` catches every tmux failure. New Task still creates the
card; if tmux is down, master never gets the note and `/api/tasks`
still returns ok.

Async teardown returns `teardown_started`, then `catch` plus
`System/exit 0`. If `close-swarm` or `kill-server` throws, the page
already said it started and nothing records the failure.

`finish-done!` swallows archive exceptions and still prints
`MAIL_WAITING` / `NO_TASK`. `archive-current-role!` already prints
helper stderr on a non-zero exit; thrown errors are silent.

## Behavior

- Best-effort stays: do not fail New Task or `/api/state` because tmux
  inject or pane capture failed.
- On inject failure, print to stderr the operation and target (role,
  socket or session). The HTTP response may still be ok.
- On async teardown failure, print to stderr the operation and root
  before exit. Do not exit as if teardown succeeded when it threw.
- On `finish-done!` archive exception, print to stderr role and root,
  then still announce mail/no-task.
- Do not log pane-capture misses (dashboard polls every second; a dead
  session is normal).
- Do not add a logging framework. `binding [*out* *err*]` with
  operation and target is enough.

## Verification

Cover: inject failure (missing tmux) still creates the card and writes
a stderr line naming the role; pane capture of a missing session does
not print; teardown that throws is not a clean success exit; archive
throw in `finish-done!` still prints `MAIL_WAITING` or `NO_TASK` and a
stderr line. Do not pin prompt wording.

# Run dashboard tests in a JavaScript runner; drop HTML/JS scraping

## Problem

Dashboard behavior is asserted from Clojure by scraping `dashboard.html`:
`str/includes?` on markup and a `dashboard-js-fn` helper that slices
JavaScript function source. Those tests do not run the page. Harmless
renames (Close → Save, `/doc` → `/doc-view`) break them. They also
cannot check Save/Cancel, grey Approve, or growable document windows.

## Behavior

- Add a JavaScript test runner that loads and executes the dashboard
  (Playwright or equivalent). Cover Attention, Documents (fetch
  `/doc?path=`, comments, Save/Cancel, marks), Reject dialog, and
  Teardown/New Task placement by interacting with the page.
- Delete Clojure tests that only scrape HTML or JS function bodies,
  including `dashboard-js-fn`.
- Keep `bb test` for CLI and HTTP helpers (`swarm_handoff`,
  `pack_board`, `pack_web.sh --test-state` and other non-HTML flags).
  Do not move those into the JS runner.
- Do not pin prompt wording.

## Verification

Cover: JS tests exercise Documents Save/Cancel and Approve disabled
when comments exist; `pack_ui_test.clj` no longer uses
`dashboard-js-fn` or `str/includes?` on dashboard HTML/JS; `bb test`
still covers handoff and board CLIs. Do not pin prompt wording.

# Ignore local Clojure and tool caches

## Problem

`.gitignore` does not list `.cpcache/`, `target/`, or `.superpowers/`.
All three are local/generated (Babashka compile cache, coverage output,
tool scratch) and are currently untracked.

## Behavior

- Add `.cpcache/`, `target/`, and `.superpowers/` to `.gitignore`.
- Do not commit those directories.

## Verification

Cover: `.gitignore` contains those three paths. Do not pin prompt
wording.
