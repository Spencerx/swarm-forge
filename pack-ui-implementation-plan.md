# Pack UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the pack cockpit on **main** so two/four/six-pack draw agent swimlanes from `swarmforge.conf`, with New Task, Attention, Work Queue, master-agent chat, invisible windows, and recorded sessions.

**Architecture:** One dashboard on main (`pack_web` + `pack/dashboard.html`). Board state is `.swarmforge/board/` written only by **New Task** (create in master lane) and **`handoffd`** (move / Done). Agents still only send `git_handoff`. The UI does not live on pack branches.

**Tech Stack:** Babashka (`swarmforge/scripts/*.bb`), `bb test` (`handoff_test.clj`, `script_test.clj`, new `pack_ui_test.clj`), HttpKit for the dashboard, tmux `capture-pane` / `send-keys` as today.

**Spec:** `pack-ui.md`. Do not contradict it. Do not change the pack graph.

**How to work:** TDD. Given/When/Then comments. Tests run production scripts (`pack_board.sh`, `swarm_handoff.sh`, `handoffd` helpers, `pack_web.bb --test-*`). See each scenario fail first. `bb test` green before every commit. One task at a time. No tests that grep prompts.

**Out of scope:** Squad stories/backlog/SL. Pack role-prompt copy except a one-line “do not ask approval in the pane” if Attention is live. Two-pack spec-approval (leave the bar empty; do not invent a gate).

---

## Files

| File | Responsibility |
|------|----------------|
| `pack-ui.md` | Spec. Do not fork behavior here. |
| `test/swarmforge/pack_ui_test.clj` | **New.** Board, New Task, daemon moves, Attention hold, web state. |
| `test/swarmforge/script_test.clj` | Launcher: `window-invisible`, master agent, no-master fails. |
| `bb.edn` | Register `swarmforge.pack-ui-test`. |
| `swarmforge/scripts/pack_board.bb` + `.sh` | Create/move/done/list tasks. |
| `swarmforge/scripts/handoffd.bb` | After deliver: move card; hold master→next for approval when a specifier exists. |
| `swarmforge/scripts/pack_web.bb` + `.sh` | HTTP: `/` dashboard, `/api/state`, `/api/tasks`, `/api/approvals`, `/agent/<role>`. |
| `swarmforge/scripts/pack/dashboard.html` | Cockpit HTML/JS. |
| `swarmforge/scripts/swarmforge.bb` | Parse `window-invisible`; start `pack_web`; skip Terminal open for invisible roles. |
| `swarmforge/scripts/swarmforge.bb` `required-helpers` | Add pack_board, pack_web. |
| `.swarmforge/board/tasks.tsv` | Runtime. `name\\tlane\\tcreated_at\\tupdated_at`. Not git. |
| `.swarmforge/board/<name>.txt` | Task body from New Task. |
| `.swarmforge/handoffs/pending_approval/` | Master-agent git_handoffs waiting on Attention (specifier packs only). |
| `.swarmforge/sessions/<role>/<task>/pane.txt` | Captured panes. Runtime, not git. |

---

## Task 1: Board create in master lane

**Files:**
- Create: `swarmforge/scripts/pack_board.bb`, `pack_board.sh`
- Create: `test/swarmforge/pack_ui_test.clj`
- Modify: `bb.edn` (require `swarmforge.pack-ui-test`)

- [ ] **Step 1: Failing test**

```clojure
(deftest pack-board-creates-a-task-in-the-master-lane
  ;; Given a pack with specifier on master
  ;; When New Task records name htw-console-app
  ;; Then the card sits in lane specifier
  ...)
```

Run `pack_board.sh create --root <tmp> --name htw-console-app --lane specifier --text "..."` (or argv: `create <name> <lane>` with cwd = project). Prefer same project-root walk as `swarm_handoff.bb`.

- [ ] **Step 2: See it fail** (`pack_board.sh` missing).
- [ ] **Step 3: Implement `pack_board`**

`tasks.tsv` rows: `name\tlane\tcreated_at\tupdated_at`. Reject duplicate name. `list` prints the file. Keep functions small.

- [ ] **Step 4: `bb test` green.**
- [ ] **Step 5: Commit** `Add pack board create in the master lane.`

---

## Task 2: Daemon moves the card

**Files:** `pack_board.bb` (`move`, `done`), `handoffd.bb` `deliver!`, `pack_ui_test.clj`

- [ ] **Step 1: Failing test**

```clojure
(deftest handoffd-moves-the-task-card-to-the-recipient
  ;; Given card htw-console-app in specifier
  ;; When a git_handoff specifier→coder for that task is delivered
  ;; Then the card lane is coder
  )
```

Drive production: write a queued handoff, run one `handoffd` poll (extract `deliver!` via a `--once` or test by calling the same `pack_board.sh move` that `deliver!` will invoke — prefer **handoffd actually calls move**). Add `handoffd.bb --once <root>` if needed so the test does not sleep.

- [ ] **Step 2: Fail because deliver does not touch the board.**
- [ ] **Step 3: `pack_board.sh move <name> <lane>` and `done <name>`.** `handoffd` `deliver!` after copying inbox: `(sh pack_board move task recipient)`. If `to` has several roles (QA broadcast), `done` the card (end-of-chain). Detect end-of-chain: `to` has more than one recipient **or** recipient list includes the master agent as merge-only. Spec: end-of-chain → Done well. Use: **more than one `to` ⇒ Done.** Single `to` ⇒ move to that role.

Six-pack QA `to: specifier,coder,cleaner,architect,hardender` → Done. Specifier→coder single `to` → coder lane.

- [ ] **Step 4: `bb test` green.**
- [ ] **Step 5: Commit** `Move pack task cards when handoffd delivers.`

---

## Task 3: Conf shape for the UI

**Files:** `swarmforge.bb` parse-config, `script_test.clj`, `pack_board.bb` `master-lane` / `lanes`

- [ ] **Step 1: Failing tests**

```clojure
(deftest swarmforge-parses-window-invisible
  ;; Given window-invisible specifier codex master
  ;; When --test-parse
  ;; Then specifier is listed and visible? is false
  )

(deftest swarmforge-fails-without-a-master-worktree
  ;; Given only window coder codex coder
  ;; When --test-parse
  ;; Then exit 1 and error mentions master
  )
```

- [ ] **Step 2: Fail** (`window-invisible` is unknown; no master check).
- [ ] **Step 3: Parse `window` and `window-invisible`.** Store `:visible?`. Require exactly one worktree named `master`. Print `visible?` in `--test-parse` if useful (`specifier invisible` / `specifier visible`).

`pack_board` / `pack_web` read lanes = conf role order; master lane = the `master` worktree role.

- [ ] **Step 4: `bb test` green.**
- [ ] **Step 5: Commit** `Parse window-invisible and require a master worktree.`

---

## Task 4: New Task create + inject payload

**Files:** `pack_board.bb` `create` with body file, `pack_web.bb` later; for now `pack_board.sh create` writes `.txt` and a hook `notify-master` stub.

- [ ] **Step 1: Failing test**

```clojure
(deftest new-task-writes-the-card-and-body
  ;; Given specifier is master
  ;; When create name=htw-console-app text="Integrate HTW stories…"
  ;; Then lane is specifier AND board/htw-console-app.txt has the text
  )
```

Inject into tmux in Task 8. This task only persists name+text+lane.

- [ ] **Step 2–4:** implement; `bb test`; commit `Record New Task name and body on the pack board.`

---

## Task 5: Dashboard state API (no HTML chrome yet)

**Files:** `pack_web.bb` `--test-state <root>` prints JSON; `pack_ui_test.clj`

JSON:

```json
{
  "master_role": "specifier",
  "master_display": "Specifier",
  "lanes": ["specifier", "coder", "cleaner", "architect", "hardender", "QA"],
  "tasks": [{"name": "htw-console-app", "lane": "specifier", "updated_at": "..."}],
  "approvals": [],
  "work_in_flight": []
}
```

- [ ] **Step 1: Failing test** — temp repo, conf six-pack-shaped, one board task, `--test-state` includes lanes from conf and the card.
- [ ] **Step 2–4:** implement; green; commit `Expose pack dashboard state from conf and board.`

Do not hard-code six-pack names in the HTML later; JS renders `data.lanes`.

---

## Task 6: Dashboard HTML — board + New Task dialog

**Files:** `swarmforge/scripts/pack/dashboard.html`, `pack_web.bb` GET `/` and POST `/api/tasks`

- [ ] **Step 1: Failing tests** (string/API, not screenshot):

```clojure
(deftest pack-dashboard-html-has-new-task-and-no-add-story
  ;; When serving dashboard.html
  ;; Then New Task exists, Add Story does not, Troubleshooter does not
  )

(deftest pack-dashboard-renders-a-lane-per-conf-role
  ;; Given --test-state lanes
  ;; (JS uses lanes from /api/state; test HTML has id="columns" and id="btn-new-task")
  )
```

Dialog: name + textarea + OK/Cancel. OK → `POST /api/tasks` `{"name","text"}` → `pack_board create` in master lane.

- [ ] **Step 2–4:** HTML modeled on squad cockpit (Attention stub empty, board columns, rail). Commit `Add pack cockpit board and New Task dialog.`

---

## Task 7: Attention hold for specifier → next

**Files:** `handoffd.bb`, `pack_web.bb` `/api/approvals`, `pack_ui_test.clj`, `dashboard.html` Attention strip

**Rule:** If conf has a role named `specifier` **and** the handoff `from` is the master agent **and** `to` is a single role, **do not deliver**. Move the file to `.swarmforge/handoffs/pending_approval/`. Attention lists it: gate `spec → <to>`, task, View document per `artifacts:` path, Approve, Reject.

Two-pack has no specifier window → deliver immediately (empty Attention).

- [ ] **Step 1: Failing tests**

```clojure
(deftest specifier-git-handoff-waits-for-attention
  ;; Given six-pack-shaped roles + card in specifier
  ;; When specifier→coder is queued
  ;; Then file is in pending_approval, coder inbox empty, /api/state approvals has the task
  )

(deftest two-pack-git-handoff-does-not-wait
  ;; Given coder master, cleaner next, no specifier
  ;; When coder→cleaner queued
  ;; Then delivered to cleaner; approvals empty
  )

(deftest attention-approve-delivers-the-handoff
  ;; Given pending approval
  ;; When POST approve
  ;; Then coder inbox has the file, card lane coder
  )

(deftest attention-reject-returns-to-master
  ;; Given pending
  ;; When POST reject
  ;; Then pending gone, card stays specifier, master session notified (tmux send or notify file)
  )
```

View document: GET `/doc?path=` only files under the project (features/, qa/). Popup like squad.

- [ ] **Step 2–4:** implement; green; commit `Hold specifier handoffs in Attention until approved.`

Constitution one-liner (no prompt tests): master agent queues `git_handoff` and does not ask in the pane. Operator uses Attention.

---

## Task 8: Inject New Task / chat / reject into the master tmux session

**Files:** `pack_web.bb`, reuse `handoffd` `notify!` / `send-keys` pattern

- [ ] **Step 1: Failing test** with a fake tmux or a recorded argv: `inject-master!` sends the task name and body. If tmux is required, skip live attach; test a function that builds the payload string and a `send-keys` wrapper with a stub `sh` in a small test-only `--test-inject-payload`.

Payload:

```text
Task: htw-console-app

Integrate the stories in ~/junk/htw-stories into one console application.
```

New Task OK and chat rail compose both call this. Chat rail labeled `data.master_display`.

- [ ] **Step 2–4:** implement; commit `Inject New Task and chat into the master agent session.`

---

## Task 9: Work Queue + session viewer

**Files:** `pack_web.bb` `/api/state` `work_in_flight`, `/agent/<role>`, `dashboard.html`

- [ ] **Step 1: Failing tests**

`work_in_flight` from each role’s `inbox/in_process` (and batch dirs): task, role, updated_at. Role link `/agent/coder`.

`--test-pane coder` prints capture-pane or recorded file.

HTML: Work Queue table Task / Role / State / Age; role is `data-open-agent`. No merger column. No SL thermometer.

- [ ] **Step 2–4:** implement; commit `Add pack Work Queue and agent session viewer.`

---

## Task 10: Record pane on task finish and teardown

**Files:** `handoffd.bb` (after successful send from a role), `pack_web.bb` or `stop_handoff_daemon` / launcher teardown, `pack_ui_test.clj`

- [ ] **Step 1: Failing test** — after deliver from coder, `.swarmforge/sessions/coder/htw-console-app/pane.txt` exists (fixture can stub capture to `"pane\n"`).

Capture: `tmux capture-pane -p -S -` like squad retire. If tmux missing in unit test, `pack_board` / `handoffd` calls `archive-session!` that writes the capture function’s string.

Teardown: `close-swarm` / `swarm-cleanup` archives every live role still holding a card.

- [ ] **Step 2–4:** implement; commit `Archive pack agent panes when a task finishes.`

---

## Task 11: Launcher starts the dashboard; invisible by default

**Files:** `swarmforge.bb` `run-main!`, `required-helpers`, pack confs on **six-pack / four-pack / two-pack** (`window` → `window-invisible`)

- [ ] **Step 1: Failing tests** — `--test-parse` with `window-invisible`; `required-helpers` includes `pack_web.sh`. A `--test-launch-plan` prints `pack_web` start and `skip-terminal specifier`.

Visible `window` lines still open Terminal (escape hatch). Pack branch confs switch to `window-invisible`.

Start `pack_web` bound to localhost, write `.swarmforge/dashboard-url`. Print the URL. Open the operator’s browser if a backend exists; otherwise print the URL only.

- [ ] **Step 2–4:** implement on main; then edit pack-branch confs in worktrees; commit main `Start pack dashboard and skip Terminal for invisible roles.` Separate commits on six/four/two-pack: `Run pack roles window-invisible.`

---

## Caps

- TDD every task. Given/When/Then. No no-op steps.
- No prompt-string tests.
- Cyclomatic complexity ≤ 5 where practical; split `deliver!` if it grows.
- Do not push unless asked.
- Keep `pack-ui.md` the spec; if the plan must diverge, change the spec in the same commit.

---

## Spec coverage

| Spec | Task |
|------|------|
| Lives on main, conf lanes | 3, 5, 6, 11 |
| Master agent chat name | 5, 6, 8 |
| New Task dialog → first lane + inject | 1, 4, 6, 8 |
| Cards = tasks, not stories | 1, 4 |
| handoffd moves / Done | 2 |
| Agents do not paint the board | 1–2, 7 |
| Attention View / Approve / Reject | 7 |
| Two-pack no invented approval | 7 |
| Work Queue + session links | 9 |
| Invisible windows | 3, 11 |
| Recorded sessions | 10 |
