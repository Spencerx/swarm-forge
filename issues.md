# Show an audit counter on every task card

## Problem

The dashboard does not show how many audit passes a task has accumulated as it
moves through the squad. The audit challenge is temporary and is deleted after
a successful handoff, so the current pending-audit state cannot provide a
cumulative count.

## Behavior

- Initialize every new timestamped task ID with an audit count of zero.
- Increment the task's count atomically whenever `swarm_handoff.sh` records a
  new audit challenge and returns `AUDIT_REQUIRED`.
- Do not increment for the unchanged second call that submits an audited
  handoff.
- A changed candidate that requires another audit increments the count again.
- Keep the count with the task ID as the card moves between roles and through
  approval. Rejection and retry preserve the count; deletion removes it.
- A later task with the same visible name but a new timestamped task ID starts
  at zero.

Expose the cumulative value as `audit_count` in the dashboard state. Render an
audit icon and the integer in the top-right of every card, including zero-count,
rejected, thin, and batched cards. Put it in the title row beside the task name
so it cannot overlap the name, status text, controls, or batch presentation.
The icon must have an accessible label and hover tooltip.

## Verification

Add behavioral coverage for initial zero, first audit, unchanged submission,
re-audit after a changed candidate, role transition, approval, rejection and
retry, deletion, and isolation between timestamped task IDs that share a
visible name. Add dashboard rendering coverage for normal, rejected, thin, and
batched cards at desktop and mobile widths.

# Strengthen the handoff audit directive

## Problem

The audit gate creates a deliberate pause, but the current directive can be
satisfied by a mechanical review of formatting, tests, and changed files. An
agent can complete that review without comparing its work product to the full
task payload, allowing missing requirements, interactions, boundaries, and
failure cases to pass through an audited handoff.

An audit count records that an audit was requested; it does not establish that
the audit was semantically complete.

## Behavior

When `swarm_handoff.sh` returns `AUDIT_REQUIRED`, direct the agent to:

1. Re-read the complete inbound task payload and every source it references.
2. Compare the completed work product against every requirement and constraint
   in that material, including interactions, boundaries, failure cases, and
   negative requirements.
3. Establish requirement-to-evidence traceability using evidence appropriate
   to the agent's assigned responsibilities. Every requirement must be covered
   by the resulting work, supported by relevant verification, or identified as
   a gap.
4. Review the complete committed diff, tests and checks, generated artifacts,
   and unrelated working-tree changes. Passing tools or clean formatting alone
   are not evidence that the task is complete.
5. Fix every finding, commit the corrections, rerun the applicable checks, and
   repeat the audit against the revised candidate before resubmitting.

Keep this language generic so the same directive applies to every agent and
lets each role select the work products and verification evidence appropriate
to its responsibilities. The tool still cannot prove that the review was
thoughtful, but it must state the semantic standard explicitly rather than
inviting a narrow mechanical audit.

## Verification

Verify behavior through the stable audit protocol: the first Git handoff call
must return `AUDIT_REQUIRED` without queueing, and a changed candidate must
require another audit. Do not pin or search for the directive's prose in an
automated test.
