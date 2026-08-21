# Pack UI

Cockpit for two-pack, four-pack, and six-pack. Modeled after squad. **Not implemented.**

Lives on **main** (`swarmforge/scripts`, with the handoff helpers). Packs do not fork a dashboard. At startup the UI reads `swarmforge/swarmforge.conf` and draws itself from that file.

Do not change the pack graph: agents still send `git_handoff` to the next role. Agents do not manage the board.

Squad chrome to copy: header, **Attention**, board | splitter | rail (Work Queue + chat). See `redo-ui.md` on **squad**.

---

## Layout

**Header:** title, **New Task**, Open (master agent), Teardown. No Open SL, no SL thermometer, no Add Story.

**Attention:** strip under the header (approvals).

**Board:** one swimlane per conf role, left to right, plus a **Done** well (not a conf window).

**Rail:** Work Queue, then chat with the **master agent**.

No Terminal window per role at launch. The dashboard is the operator surface.

---

## Config

`swarmforge.conf` is the only pack-shape input.

| From conf | UI |
|-----------|-----|
| `window` / `window-invisible` lines, file order | Swimlanes |
| Role whose worktree is `master` | Master agent: chat title, Open control, intake |
| Display name of that role | Rail label (`Specifier`, `Coder`, …) |

If there is no `master` worktree, fail at startup. Do not guess. Do not hard-code specifier/coder.

Today’s files (not hard-coded in the UI):

| Pack | Lanes | Master agent |
|------|-------|--------------|
| two-pack | coder, cleaner | coder |
| four-pack | specifier, coder, refactorer, architect | specifier |
| six-pack | specifier, coder, cleaner, architect, hardender, QA | specifier |

---

## Board

Squad lanes are **stages of a story**. Pack lanes are **persistent agents**. A card sits in the agent who holds it. Two in-flight tasks can sit in two lanes at once.

Packs have **no stories**, sprints, or story backlog. The unit is a **task**. The operator names it in **New Task**. Downstream roles keep that name as `task:` on every `git_handoff`.

Each card is one task. Title is that name. A batch lane may hold several cards.

---

## New Task

How a card enters the **first swimlane** (master agent — specifier on six-pack).

1. Operator clicks **New Task**.
2. A dialog asks for a **name** and the **task text**.
3. **OK** does both:
   - Create the card in the master-agent lane, titled with that name.
   - Send the name and text to the master agent (same injection as the chat rail).

Cancel does nothing. The master agent does not invent the name or paint the board.

Follow-up with that agent is still the chat rail. New Task is the start of a card, not a story backlog.

---

## Daemon owns the board

Agents never write swimlane position or task state. The operator **New Task** create and **`handoffd`** moves are the only writers.

`handoffd` on each delivered `git_handoff` (`task`, `from`, `to`):

1. **Move** the card named `task:` out of `from` into each `to` lane. (Create only if New Task was skipped — should not happen.)
2. **Done:** end-of-chain handoff (no further forward). Card goes to the Done well.

No “move this card” or “set task state” in role prompts. If the board is wrong after the first lane, the handoff was wrong.

---

## Attention

Just like squad: a line at the top, not a chat turn.

Each pending row:

- gate (e.g. spec → coder)
- task name
- **View document** for each file to approve (Gherkin, QA suite, …)
- Approve
- Reject

View opens the document (squad approval popup). Approve unblocks `git_handoff` downstream. Reject returns to the master agent with the reason.

Empty bar when nothing is pending. Do not ask “Approve handoff to coder?” only in the pane. Documents are whatever the master agent just wrote on that commit.

Two-pack has no spec gate today. If the master agent has nothing that needs a human sign-off, the bar stays empty. Do not invent approvals.

---

## Work Queue

Just like squad. Table of in-flight work in the rail.

| Column | Meaning |
|--------|---------|
| Task | `task:` name. A batch row may list several tasks. |
| Role | Agent who holds it. **Link** opens that agent’s session. |
| State | Live / idle / no session (icon + thermometer, no SL thermometer). |
| Age | Time since the row last moved. |

Lane = mail arrived. Work Queue = that role’s `in_process`. They diverge when a batch sits in inbox.

Role link: `/agent/<role>` popup. Live = `tmux capture-pane`. After the task finishes = recorded `pane.txt`. Every pack role can appear, including the master agent. No merger column.

---

## Chat rail

The **master agent** (conf worktree `master`). Label from that role’s display name. Not “Troubleshooter,” not Squad Leader.

That agent is a product role. Do not load swarm repair into its prompt. Open / Teardown in the chrome is enough.

---

## Invisible windows

Every pack role is `window-invisible`: tmux session + agent, no Terminal surface at launch.

Open from chrome, lane, or Work Queue role link. Closing the surface does not kill the agent; reopen attaches to the same session (`tmux attach` still works).

Open intake = the master agent.

---

## Recorded sessions

Keep `pane.txt` so a finished run can be read later.

Squad archives on **retire**. Pack roles persist, so capture when a role **finishes a task** (`git_handoff` / `done_with_current`) and at **teardown**.

Path: `.swarmforge/sessions/<role>/<task>/pane.txt`. Runtime state; not git. Live dashboard view is `tmux capture-pane`, not a second log.
