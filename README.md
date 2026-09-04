<p align="center" style="color: red; font-weight: bold; font-size: 2em; font-style: italic; text-decoration: underline;">
Do not spend any money on a bankrbot SWARM token.
</p>

# SwarmForge

SwarmForge coordinates AI agents in isolated git worktrees and tmux sessions.
Agents exchange committed work through durable handoffs, while the operator
uses a local dashboard to start work, inspect agents, handle approval gates,
answer clarifications, and stop the swarm.

![SwarmForge dashboard](project-swarm.jpg)

This repository's master branch is named `main`. It is the landing page,
installer source, shared runtime, and shared engineering law. It is not itself
a runnable SwarmForge product.

## Products

| Command | Branch | Shape |
|---|---|---|
| `get-swarm-forge two-pack` | [`two-pack`](https://github.com/unclebob/swarm-forge/blob/two-pack/README.md) | Pack installed into the current project: `coder` → `cleaner`. |
| `get-swarm-forge four-pack` | [`four-pack`](https://github.com/unclebob/swarm-forge/blob/four-pack/README.md) | Pack installed into the current project: `specifier` → `coder` → `refactorer` → `architect`. |
| `get-swarm-forge six-pack` | [`six-pack`](https://github.com/unclebob/swarm-forge/blob/six-pack/README.md) | Pack installed into the current project: six separate specification, implementation, cleanup, architecture, hardening, and QA roles. |
| `get-swarm-forge project-manager` | [`project-manager`](https://github.com/unclebob/swarm-forge/blob/project-manager/README.md) | Multi-project forge with selectable two-, four-, and six-pack templates and a host lieutenant. |
| `get-swarm-forge lieutenant` | [`lieutenant`](https://github.com/unclebob/swarm-forge/blob/lieutenant/README.md) | Multi-project forge with one configurable project template and a planning lieutenant. |

A **pack** is composed into an existing project. Running `./swarm` starts that
project's configured roles.

A **forge** is installed into an empty host directory. Running `./swarm` starts
the forge dashboard and host lieutenant; project swarms start when the operator
creates or opens projects beneath `projects/`.

The `squad`, `sprint-module-squad`, and `adversaries` branches are separate
experimental workflows. They are not `get-swarm-forge` products.

## Prerequisites

- `zsh`
- `git`
- `tmux`
- Babashka (`bb`)
- At least one configured agent backend: `grok`, `codex`, `claude`, or
  `copilot`

## Install the helper

Put `get-swarm-forge` somewhere on `PATH`:

```sh
mkdir -p ~/cmds
curl -L -o ~/cmds/get-swarm-forge \
  https://raw.githubusercontent.com/unclebob/swarm-forge/main/get-swarm-forge
chmod +x ~/cmds/get-swarm-forge
```

Add `~/cmds` to `PATH`, then recopy the helper when it changes. The helper is
the supported entry point because it composes files from more than one branch.

## Composition

For a pack install, the helper downloads two branches:

```text
main
  swarmforge/scripts/                    shared runtime and dashboard
  swarmforge/constitution/articles/      shared engineering, workflow, handoffs

<pack branch>
  swarm                                  launcher
  swarmforge/swarmforge.conf             roles, agents, and worktrees
  swarmforge/constitution.prompt         constitution entry point
  swarmforge/constitution/articles/      pack-local additions
  swarmforge/roles/                       role ownership
```

The result is written into the current project. Shared article names
`engineering.prompt`, `workflow.prompt`, and `handoffs.prompt` always come from
`main`; a pack specializes them with `project.prompt` and `local-*.prompt`
files.

For a forge install, the named forge branch supplies the host runtime,
lieutenant, and dashboard. `project-manager` also downloads the three pack
branches into `packs/`; `lieutenant` carries its one template under
`.swarmforge/project-pack/`.

## Use a product

Install a pack in an existing software repository:

```sh
get-swarm-forge six-pack
./swarm
```

Or install a forge in an empty directory:

```sh
get-swarm-forge lieutenant
./swarm
```

The selected product's README describes its routes, roles, worktrees, project
lifecycle, and dashboard behavior. The branch configuration—not this README—is
the authority for current backend assignments and topology.

## What `main` owns

```text
get-swarm-forge                         product composer
swarmforge/scripts/                    launcher, dashboard, board, handoffs
swarmforge/constitution/articles/      shared agent rules
swarmforge/handoff-protocol.md         durable handoff protocol
test/                                  shared runtime tests
```

Changes to shared launch, dashboard, terminal, worktree, board, or handoff
behavior belong on `main` first. Pack branches own only their configuration,
local constitution additions, role prompts, and launcher. Forge branches carry
the common files needed for standalone installation and should be refreshed
from `main` when those files change.

Do not pin prompt prose with automated tests. Test observable runtime behavior
instead.

## Runtime model

At startup the composed runtime validates the configuration, initializes git
when necessary, creates role worktrees, mirrors the managed SwarmForge files
into them, creates isolated tmux sessions, starts the handoff daemon and local
dashboard, and launches each configured agent backend.

`master` in a role configuration means the project's main checkout on its
current branch; it is a worktree sentinel, not a required git branch name.
Generated transport and process state lives under `.swarmforge/`; generated
role checkouts live under `.worktrees/`.

Agents send committed work with `swarm_handoff.sh`, accept it with
`ready_for_next.sh`, and finish the current item with `done_with_current.sh`.
See [the handoff protocol](swarmforge/handoff-protocol.md) for message format,
auditing, delivery, retry, merge, and lifecycle details.

The `simple-windows` tag marks the last `main` snapshot before the dashboard
cockpit. It is historical and is not a `get-swarm-forge` product.
