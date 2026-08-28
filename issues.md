# Focus the New Task name field

## Problem

Opening the New Task dialog does not put the cursor in the name field. The
operator has to click the field before typing.

## Behavior

When New Task is hit, the dialog's name field gets focus.

## Verification

Cover opening New Task: `#nt-name` is focused. Do not pin prompt wording.

# Document window pane borders are draggable

## Problem

The document window's three panes (file, previous comments, new comment)
have fixed sizes. The operator cannot give more room to the file or the
comment box. With no history, the previous-comments pane still takes a
large slice.

## Behavior

The borders between the three panes are draggable. If there are no
previous comments, that pane starts small.

## Verification

Cover a first Attention document: `#doc-history` is small. Cover dragging
the border between `#doc-body` and `#doc-history` changing their heights.
Do not pin prompt wording.

# Reject-dialog comments appear in document history

## Problem

Comments typed in the reject dialog go only to the agent inject. They do
not show in the document window's previous-comments pane. After retry,
the operator cannot see that text next to the file.

## Behavior

When Retry is hit with reject-dialog comments, that text is appended as a
timestamped previous-comment on each document of that approval. It shows
in `#doc-history` the next time the document window opens. The inject to
the agent is unchanged.

## Verification

Cover Save on a document, Retry with dialog text, then a new pending
offer: `#doc-history` contains both the saved document comment and the
reject-dialog comment, each under a timestamp. Do not pin prompt wording.

# Clarification Open pops a larger answer window

## Problem

Clarification requests are answered in a one-line field on the Attention
row. Some questions and answers need more room.

## Behavior

Each clarification request has an Open button. It pops a growable window
with the request in one pane and the response in another. The border
between the panes is draggable. OK posts the answer and satisfies the
request, same as Submit on the Attention row.

## Verification

Cover Open on a pending clarification: a window shows the request text
and an editable response. Cover OK posting the answer and clearing that
request from Attention. Do not pin prompt wording.

# Merging card second line is Merging role

## Problem

The yellow merging card's second line is pane status. That does not say
the card is a handback merge or who sent it.

## Behavior

The merging card's second line is `Merging <role>`, where `<role>` is the
sender of the reverse `git_handoff`.

## Verification

Cover four-pack refactorer `back-one` in coder in_process: the yellow
card's second line is `Merging refactorer`. Do not pin prompt wording.

# Do not git_handoff a handback

## Problem

After merging a reverse (`non-forwarding`) `git_handoff`, agents still
draft a forward send. Live coder merged the refactorer HTW handback, then
queued `to: refactorer` with the preserved task name. The helper refused;
the agent should not have tried.

## Behavior

Constitution (handoffs article): a reverse (`non-forwarding`) `git_handoff`
is merge-only. Merge it, then `done_with_current`. Do not send a
`git_handoff` for that inbound. The helper refusal stays.

## Verification

Do not pin prompt wording. Cover that a `git_handoff` while inbound
`non-forwarding` is still refused. Cover `done_with_current` after a
reverse copy without a new outbox `git_handoff`.

# Remove the Board toolbar line

## Problem

The dashboard board has a toolbar line that reads "Board" on the left and
"lanes from swarmforge.conf · cards are tasks" on the right. It is noise.

## Behavior

That line is gone. The swimlane columns start at the top of the board.

## Verification

Cover the dashboard: `.board-toolbar` is not present. Do not pin prompt
wording.
