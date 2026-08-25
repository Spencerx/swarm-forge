# Task identity must not use display names

Task names are currently used as identifiers across board rows, handoffs,
rejection archives, pending approvals, and dashboard state. This allows stale or
duplicate handoffs to attach to the wrong task when a task is deleted, retried,
recreated, or recreated with different capitalization.

Expected behavior: every task should have a stable hidden task id separate from
the displayed card name. A timestamp should be part of the id, for example:

```text
task_id: 20260825T135010975699Z-wumpus-jump
task_name: wumpus jump
```

The dashboard card should display only `task_name`. Runtime state should use
`task_id` for board rows, handoffs, pending approvals, rejected task archives,
delete/retry operations, and in-process/completed queues. Display names are
labels, not identifiers.

Retry should keep the same `task_id` and hand the edited card back for fresh
analysis. Delete should permanently retire that `task_id`. Creating a new card,
even with the same display name or different capitalization, should create a new
`task_id`.

# Rejected task commits remain in active history

When a git handoff is rejected, the rejected commit remains on the active
producer branch. If the task is deleted or retried, later work can still be
based on the rejected commit, so stale rejected documents can reappear in later
approval handoffs.

Observed in `/Users/unclebob/junk/four-squad`:

- Lowercase `extras` was rejected and deleted.
- The rejected extras specification commit remained in active history.
- A later handoff for `task: extras` referenced a commit that modified two of
  the three rejected extras documents.
- The active board also had a separate `Extras` task, making the pending
  approval look like it belonged to the new task even though it was continuing
  rejected lowercase `extras` work.

Expected behavior: rejection should preserve the rejected work but remove it
from the active branch. The system should record the task's active base commit
when the task is dequeued. On rejection, create a branch for all unapproved
commits made for that task since the base commit, using a name derived from the
task id, for example:

```text
rejected-20260825T135010975699Z-wumpus-jump-20260825T141500Z
```

Then move the active producer branch back to the task base commit. If the
rejected task is deleted, remove the card and task/runtime state while leaving
the rejected branch as historical evidence. If the rejected task is retried,
hand the edited card back for fresh analysis from the rolled-back active branch,
not from the rejected commit.

# Rejection must clean active runtime state

Rejecting or deleting a task should invalidate all active runtime state for that
`task_id`.

Expected behavior:

- Clear or archive the producer's `in_process` handoff for the rejected task so
  the producer cannot keep sending work for it.
- Remove or archive pending approvals for that `task_id`.
- Remove queued inbox items for that `task_id` unless the task is explicitly
  retried.
- Prevent any completed/done task from accepting new handoffs.
- On retry, queue a fresh task handoff with the same `task_id` and edited
  payload from the rolled-back branch.

# Handoff validation must reject stale or duplicate work

Handoff creation currently allows stale task names and duplicate handoffs to
survive when lane state is ambiguous.

Expected behavior:

- Reject a `git_handoff` unless its `task_id` matches the sender's current
  in-process task, or another explicitly valid current task for that role.
- Reject a `git_handoff` for a deleted, rejected, or done task unless that task
  has been explicitly retried and requeued.
- Reject duplicate active handoffs for the same `from`, `to`, `task_id`, and
  `commit`. "Active" includes pending approvals, sent-but-not-completed
  handoffs, and recipient inbox `new` or `in_process` handoffs.
- Before queueing a handoff, verify the commit is a descendant of the sender's
  current accepted task base and is not on a rejected branch.
- One commit should produce at most one active handoff per recipient for a
  given task id.

# Deleted handoff artifacts appear as missing approval documents

When a git handoff waits for approval, the dashboard Documents menu can include
files that were deleted by the handed-off commit. Clicking one of those entries
opens the document viewer with `Not found`.

Observed in `/Users/unclebob/junk/four-squad`:

- The pending handoff for commit `82f83a7882` lists
  `features/cave_hazards.feature` in its `artifacts:` header.
- That commit deletes `features/cave_hazards.feature` while splitting its
  scenarios into numbered feature files.
- `swarm_handoff.bb` builds artifacts with
  `git diff --name-only <parent> <sha>`, which includes deleted paths.
- `pack_web.bb` only serves existing files under `features` or `qa`, so the
  deleted artifact becomes a Documents entry that renders as `Not found`.

Expected behavior: deleted files should not appear as viewable Documents in
approval handoffs. `commit-artifacts` should exclude deleted paths, or the
dashboard should filter missing/non-viewable artifact paths before displaying
the Documents menu.
