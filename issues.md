# Issues

Pack master-agent chat vs squad Troubleshooter rail.

## Open

(none)

## Implemented

- Master chat has no request/answer protocol
- Work Queue should show the active task and a green dot while the agent is working on it
- Specifier asked for approval; Attention stayed empty
- Attention artifact Views should be a menu; popups should be growable
- When the coder started, the task card should have moved to the coder swimlane
- Clicking a card should show the task that card represents
- Handoff task name did not match the board card (HTW vs htw)
- Specifier invented a task name instead of using the New Task name

---

### Master chat has no request/answer protocol

**Implemented.** `/api/chat` writes a durable request, injects `[id] text` to the master pane, and `/api/state` `chat` carries body/response. The master answers with `pack_dashboard_request.sh answer <id> ./tmp/answer.txt`. The rail renders both bubbles on poll.

**Where:** `pack_web.bb`; `pack_dashboard_request.sh`; Tool Startup; `pack/dashboard.html`.

### Work Queue should show the active task and a green dot while the agent is working on it

**Implemented.** Task name comes from `in_process` or the board card in that lane. Green/`live` when the agent holds a task and the session is up.

**Where:** `pack_web.bb` `work_in_flight`.

### Specifier asked for approval; Attention stayed empty

**Implemented.** Specifier prompt and Tool Startup: do not ask in the pane. Queue `git_handoff`; Attention is the gate.

**Where:** six-pack/four-pack `specifier.prompt`; Tool Startup; `handoffs.prompt`.

### Attention artifact Views should be a menu; popups should be growable

**Implemented.** Documents menu instead of one View per file. `window.open` uses `resizable=yes`.

**Where:** `pack/dashboard.html`.

### When the coder started, the task card should have moved to the coder swimlane

**Implemented.** Board move is case-insensitive. Unknown `task:` fails the handoff before delivery.

**Where:** `pack_board.bb`; `handoffd.bb`.

### Clicking a card should show the task that card represents

**Implemented.** Card click opens `/task?name=` with the New Task body in a growable window.

**Where:** `pack/dashboard.html`; `pack_web.bb`.

### Handoff task name did not match the board card (HTW vs htw)

**Implemented.** `pack_board` matches names ignoring case and keeps the board spelling. Helper fills `task:` from the card in the sender lane.

**Where:** `pack_board.bb`; `swarm_handoff.bb`.

### Specifier invented a task name instead of using the New Task name

**Implemented.** Prompt and Tool Startup: use the board card name; do not invent. Helper overwrites `task:` from that card.

**Where:** specifier prompt; Tool Startup; `swarm_handoff.bb`.
