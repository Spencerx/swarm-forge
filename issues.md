# Remove empty audit_pending directories after a successful handoff

## Problem

A Git-handoff audit challenge lives under `.swarmforge/handoffs/audit_pending/`
as a sender-hash directory containing a task `.edn` file. After a successful
unchanged submit, the helper deletes the `.edn` file but leaves the sender
directory behind.

A finished four-squad run left four empty hash directories there. They look
like stuck audits and accumulate across tasks.

## Behavior

- When an audit challenge is recorded, keep the existing sender-hash directory
  and task `.edn` file.
- When that challenge is consumed by a successful unchanged handoff, delete the
  `.edn` file and remove the sender-hash directory if it is then empty.
- When a changed candidate invalidates prior challenges, apply the same
  cleanup: no empty sender-hash directories remain.
- Do not delete `.swarmforge/handoffs/audit_pending/` itself, or its lock file.
- A later audit for the same sender may recreate the directory.

## Verification

Cover successful unchanged submit, changed-candidate invalidation, and a second
audit by the same sender after cleanup. After each successful or invalidated
challenge, `audit_pending` must contain no empty sender-hash directories. A
still-pending challenge must keep its directory and `.edn` file.

# Treat an already-answered clarification as success

## Problem

An agent asks the operator with `pack_dashboard_request.sh clarify`. The
operator answers on the dashboard, which moves the clarification from pending
to done and injects the answer into the pane.

The agent then runs `pack_dashboard_request.sh answer <clar-id> …` to record
that it received the answer. That command looks only for a pending *request*,
not a done clarification, and exits `Unknown pending request`.

In four-squad the specifier hit this after the HHG clarification was already
answered. The work continued, but the helper reported a failure for a closed
item.

## Behavior

- `pack_dashboard_request.sh answer <id>` must succeed when `<id>` is a
  clarification the operator has already answered.
- Do not recreate a pending clarification or overwrite the stored operator
  response.
- Print a stable success line that names the id.
- A truly unknown id (never created) still fails.
- Answering a still-pending operator request is unchanged.

## Verification

Cover: operator answers a clarification, then the originating role runs
`answer` with that clar id and a local file. The command must exit 0, leave the
clarification in `done` with the original operator text, and not create a
pending request. An unknown id must still fail. A pending operator-to-agent
request must still move to done with the agent's answer.
