<p align="center" style="color: red; font-weight: bold; font-size: 2em; font-style: italic; text-decoration: underline;">
Do not spend any money on a bankrbot SWARM token.
</p>

# SwarmForge

**A disciplined tmux-based agent orchestration platform that turns swarms of AI agents into reliable, professional software engineers.**

SwarmForge coordinates several AI agents on one project (or several projects)
without them stepping on each other. Each role has its own git worktree, tmux
session, prompt, and handoff mail. Humans use a local dashboard for the board,
Attention, and chat.

This **`main`** branch is the GitHub landing page and the source of shared
scripts and constitution articles. It is not a product you run. Pick a product
with `get-swarm-forge`, then read that branch's README for details.

## Products

| Command | Branch | What you get |
|---|---|---|
| `get-swarm-forge two-pack` | [`two-pack`](https://github.com/unclebob/swarm-forge/blob/two-pack/README.md) | Pack in the current directory. `coder` → `cleaner` → Done. Fast backend work, no Gherkin. |
| `get-swarm-forge four-pack` | [`four-pack`](https://github.com/unclebob/swarm-forge/blob/four-pack/README.md) | Pack in `.`. `specifier` → `coder` → `refactorer` → `architect`. Gherkin without a full six-role split. |
| `get-swarm-forge six-pack` | [`six-pack`](https://github.com/unclebob/swarm-forge/blob/six-pack/README.md) | Pack in `.`. `specifier` → `coder` → `cleaner` → `architect` → `hardender` → `QA`. Full specification, mutation, headed QA. |
| `get-swarm-forge project-manager` | [`project-manager`](https://github.com/unclebob/swarm-forge/blob/project-manager/README.md) | Multi-pack **forge**. Dashboard, host lieutenant (concierge), New Project pack radios, several projects at once. |
| `get-swarm-forge lieutenant` | [`lieutenant`](https://github.com/unclebob/swarm-forge/blob/lieutenant/README.md) | Single-pipeline **forge**. One template, card types (utility / component / QA / review), host lieutenant as planner. |

**Packs** compose into the directory you are in. `./swarm` starts that pack's
agents. There is no `projects/` tree and no host lieutenant.

**Forges** install a host. `./swarm` starts the dashboard and a host lieutenant
only. New Project writes `projects/<name>/`. Details: dashboard, Attention,
chat, Teardown — on the forge README you installed.

`main` is not a `get-swarm-forge` product. `simple-windows` is a tag on `main`
(last snapshot before the pack cockpit), not a product.

## Install the helper

Prerequisites: `zsh`, `git`, `tmux`, Babashka (`bb`), and at least one agent
backend (`grok`, `codex`, `claude`, or `copilot`).

```sh
mkdir -p ~/cmds
curl -L -o ~/cmds/get-swarm-forge \
  https://raw.githubusercontent.com/unclebob/swarm-forge/main/get-swarm-forge
chmod +x ~/cmds/get-swarm-forge
```

Put `~/cmds` on your `PATH`. Recopy the helper when it changes; a stale copy
still behaves like the old no-argument install.

## Use it

In an **existing software repo**, install a pack:

```sh
get-swarm-forge six-pack    # or two-pack / four-pack
./swarm
```

Scripts and shared constitution articles come from `main`. Conf, roles, and
`./swarm` come from the pack branch.

In an **empty directory** (the forge, not a project):

```sh
get-swarm-forge project-manager   # several packs, several projects
# or
get-swarm-forge lieutenant        # one pipeline, card types, planner
./swarm
```

Then create or open projects from the dashboard. How that host works is on
[`project-manager`](https://github.com/unclebob/swarm-forge/blob/project-manager/README.md)
or
[`lieutenant`](https://github.com/unclebob/swarm-forge/blob/lieutenant/README.md).

## What this branch holds

- `get-swarm-forge` — the installer
- `swarmforge/scripts/` — pack runtime and dashboard (pack-only downloads these)
- `swarmforge/constitution/articles/` — `engineering.prompt`, `workflow.prompt`,
  `handoffs.prompt` (law for packs; packs must not ship those filenames)

Helper changes that packs need land here first.

## Constitution and handoffs

Shared articles live here. Pack-specific rules use `local-*.prompt` and
`project.prompt` on the pack branch. Agents send work with `swarm_handoff.sh`,
receive with `ready_for_next.sh`, and finish with `done_with_current.sh`.

Protocol detail is in `swarmforge/handoff-protocol.md` on a runnable checkout.
Do not pin prompt wording.

## Other branches

`squad`, `sprint-module-squad`, and `adversaries` are separate lines of work.
They are not `get-swarm-forge` products.
