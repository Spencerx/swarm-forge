# Approval unblock does not wake sender role

Observed twice in `/Users/unclebob/junk/four-squad`: a queued task remained in
`specifier`'s inbox after the previous specifier-to-coder handoff was approved
and delivered.

Failure chain:

- The specifier completed `htw` and queued a git handoff to coder.
- Before that handoff was approved, the specifier tried to receive the next
  task.
- `ready_for_next.sh` correctly refused to dequeue `jump` because the specifier
  still had an active pending approval:

  ```text
  WAITING_FOR_APPROVAL: current git handoff is still active
  ```

- The htw handoff was later approved and delivered to coder.
- That approval cleared the condition that had blocked the specifier, but the
  specifier was not notified or rescheduled.
- `jump` stayed in `.swarmforge/handoffs/inbox/new` until someone manually
  prompted the specifier to run `ready_for_next.sh` again.
- The same pattern repeated after `jump` was approved: `jump` moved to `coder`,
  but the later `extras` task remained queued for `specifier` instead of
  starting.
- By contrast, reject/retry and reject/delete paths do wake or otherwise
  stimulate specifier: rejection injects `Rejected: <task>` into the master pane,
  and retry queues a fresh New Task note that `handoffd` delivers normally.

Root cause: approval delivery changes the sender role's eligibility, but the
handoff/approval workflow only notifies the receiver. A role that previously hit
`WAITING_FOR_APPROVAL` can remain idle even though queued work is now available.
The rejection paths have their own wake-ups; the missing path is approval.

Fix:

- When a pending approval is approved and delivered, identify the sender role
  from the approved git handoff.
- After delivery, check whether that sender has queued inbound work that is now
  eligible for `ready_for_next.sh`.
- If so, notify that sender role to run `ready_for_next.sh`.
- The notification should be sent only after the pending approval has been
  removed or moved out of `pending_approval`, so the sender does not immediately
  hit the same `WAITING_FOR_APPROVAL` state again.
- Keep rejection handling as-is unless a separate rejection-specific failure is
  observed; reject/retry and reject/delete already appear to stimulate the
  specifier correctly.

Expected behavior:

- If `htw` blocks specifier from starting `jump` while htw waits for approval,
  approving htw wakes specifier.
- `jump` starts as soon as the htw approval is delivered to coder.
- If `jump` blocks specifier from starting `extras` while jump waits for
  approval, approving jump wakes specifier.
- `extras` starts as soon as the jump approval is delivered to coder.
- Coder's in-process htw work does not block specifier from starting `jump`.
