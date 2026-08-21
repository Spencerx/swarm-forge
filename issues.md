# Issues

Pack dashboard after Work Queue + live panes (`0fca036`).

## Open

(none)

## Implemented

- Make thermometers live
- Make all agents yolo
- Specifier response does not show in the specifier window
- After teardown, show a red “Swarm disconnected”

---

### Make thermometers live

**Implemented.** `pack_web` samples each role pane on `/api/state`, hashes it, and raises/decays heat 0–6 as `activity`. Dashboard lights that many thermometer bars. Not the daemon.

**Where:** `pack_web.bb` `/api/state`; `pack/dashboard.html`.

### Make all agents yolo

**Implemented.** Launcher always passes Codex/Copilot `--yolo` and Claude/Grok `bypassPermissions`. Pack confs on six/four/two-pack add `--yolo` extra-args. Pack graph unchanged.

**Where:** pack-branch `swarmforge.conf`; `swarmforge.bb` launch args.

### Specifier response does not show in the specifier window

**Implemented.** Capture and inject target `session:Window.0` (same as launch). Agent page polls `/api/agents/<role>/pane` every second with squad TS scroll-to-end (`toEndSoon`, `stickBottom`).

**Where:** `pack_web.bb` pane capture + `/api/agents/<role>/pane`; agent page JS.

### After teardown, show a red “Swarm disconnected”

**Implemented.** `#error` strip. `loadState` catch sets **Swarm disconnected** when `/api/state` cannot be fetched. Not the browser's `Failed to fetch`.

**Where:** `pack/dashboard.html`.
