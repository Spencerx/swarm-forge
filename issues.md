# Audible chime when Attention is requested

## Problem

Approvals and clarifications appear in Attention without a sound. The
operator can miss a new request if they are not looking at the dashboard.

## Behavior

When a new approval or clarification appears in Attention, the dashboard
plays a short audible chime. Repeating the same pending row on refresh
does not chime again. Clearing a request and getting a later one does
chime.

## Verification

Cover a new pending approval: a chime is triggered. Cover poll/refresh
with the same approval still pending: no second chime. Cover a new
clarification the same way. Do not pin prompt wording.
