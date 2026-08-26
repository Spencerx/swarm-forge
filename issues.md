# Per-document remedial comments on Attention approvals

## Problem

Attention review is yes/no on the whole git_handoff. The operator opens
Documents, reads files, and has nowhere to attach findings to a file. The
only comment box is the Reject dialog, after Reject is already chosen, and
it is one blob for the whole packet.

## Behavior

Each Documents menu item shows one of three marks:

- Empty box: the document has not been opened for reading (or was opened
  and Cancelled without a prior Save).
- Green check: the document was Saved with no remedial comments (empty or
  whitespace only).
- Red X: the document has saved remedial comments.

The document window stays growable. Title and body still come from the
existing `/doc?path=` fetch. At the bottom is a comments box: scrollable
and word-wrapped. The old Close control is replaced by **Save** and
**Cancel**.

Save writes the comments for that path onto the pending approval. Cancel
closes the window and does not change what was already saved.

Opening a red-X item loads every saved comment for that document into the
box. Cancel in that case does not delete them. The only way to clear
saved comments is to blank the box (or leave only whitespace) and Save.
That turns the item into a green check.

Comments are operator findings about the file, not edits to it. Store
them with the pending approval, keyed by approval id and path. Do not
write them into the artifact. They survive dashboard polls and reloads
until Retry, Accept, or Delete.

Whitespace-only comments count as empty.

## Approval row

If any document on that approval has non-empty remedial comments, the
Attention row’s **Approve** is disabled (greyed). **Reject** stays
enabled. Unread empty boxes do not disable Approve.

## Reject dialog

Reject still opens the three-way dialog (Delete, Retry, Accept).

**Accept** stays enabled. If any remedial comments are saved, Accept
first confirms that those comments will be ignored. Confirm, not a
one-button alert, so the operator can back out. On confirm, Accept is
the original Approve: mark approved, deliver, do not roll back, do not
increment `audit_count`, discard the comments, do not send them to the
agent.

**Retry** is the path that uses the comments. Deliver every saved
per-document comment (path plus text) to the master role as audit
findings, and direct that role to read them, re-read the original task
document, treat the comments as findings, commit on top of the rejected
SHA, and `git_handoff` HEAD. Do not make the operator retype document
comments in the Reject dialog. That textarea remains extra notes only.

**Delete** archives and removes the card as today; saved comments go
away with the pending handoff.

## Verification

Cover dashboard HTML/JS: Documents items can show empty, check, and X;
the document window has a scrollable word-wrapped comments box, Save,
and Cancel; `/doc?path=` is still the fetch.

Cover saved state: Save with text marks X and reopening that item shows
the text; Cancel after an edit leaves the previous saved text; Save of
blank/whitespace clears comments and marks a check; reload/poll does not
drop saved comments.

Cover the row: any non-empty comment disables Approve and leaves Reject
enabled; unread items do not disable Approve.

Cover Accept: with comments, a confirm warns they will be ignored; on
confirm the handoff is approved and comments are not injected. Cover
Retry: comments are delivered to master with the paths, no `(New Task)`
note, original task id and body unchanged. Do not pin prompt wording.
