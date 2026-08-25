# Next task starts from an unapproved rejected commit

Observed in `/Users/unclebob/junk/four-squad`: the current approval can say
`task: extras` while the Documents list contains `features/console/wumpus_jump.feature`,
which belongs to the rejected/retried `jump` task.

Failure chain:

- The specifier completed `jump` and queued a `jump` git handoff for approval.
- Before that approval was accepted or rejected, the specifier marked the
  in-process `jump` handoff complete and ran `ready_for_next.sh`.
- `ready_for_next.sh` dequeued `extras` and stamped it with the current active
  `HEAD` as `task_base_commit`.
- At that moment, active `HEAD` still included the unapproved `jump` commit, so
  `extras` got a task base of the pending/rejectable `jump` commit.
- The `jump` approval was then rejected and the rejected commit was preserved on
  a rejected branch.
- When `extras` later tried to hand off its work, ancestry validation required
  it to descend from its recorded task base, which was the rejected `jump`
  commit.
- The agent merged the rejected `jump` base back into the `extras` line to pass
  ancestry validation.
- The resulting `extras` handoff commit was a merge commit whose first-parent
  diff showed `features/console/wumpus_jump.feature`, so the approval said
  `extras` while the document list showed the `jump` file.

Root cause: a role can dequeue/start the next task while its previous git
handoff is still pending approval. This lets a later task capture unapproved
work as its base. If that earlier work is rejected, the later task is already
contaminated.

Fix:

- Treat a role with an active pending approval or sent-but-not-completed git
  handoff as not idle.
- `ready_for_next.sh` and `ready_for_next_batch.sh` must refuse to dequeue a new
  task for a role while that role has active outbound git work awaiting approval
  or delivery.
- The refusal should be explicit, for example:

  ```text
  WAITING_FOR_APPROVAL: current git handoff is still active
  ```

- A role may resume its existing in-process task, but it must not move to a
  different queued task until the previous outbound git handoff is accepted,
  rejected, or otherwise terminal.
- Rejection must continue to remove/rollback the rejected commit and clear
  runtime state for that task id.
- Artifact generation for git handoffs should use the task's accepted base
  range, not blindly `commit^..commit` for merge commits. For a task handoff,
  artifacts should be computed from `task_base_commit..commit` and should reject
  or ignore paths introduced only by unrelated/rejected merged history.

Expected behavior:

- If `jump` is waiting for approval, the specifier cannot start `extras`.
- If `jump` is rejected, the active branch rolls back before any later task
  records a task base.
- If `extras` is eventually started, its `task_base_commit` is the accepted
  project state, not the rejected `jump` commit.
- An `extras` approval cannot list `jump` documents.
