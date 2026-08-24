1. The border between the swimlanes and the Work Queue should be draggable, as in squad. The dashboard already draws an 8px `.splitter` with title "Drag to resize" and a CSS `--rail:340px`, but nothing handles the drag. The rail stays 340px; the operator cannot widen or narrow Work Queue vs the board.

2. A rejected card should also have an edit button. It pops up the task document in an edit pane so the operator can change it. Buttons at the bottom are **Delete** and **Retry**.

   Delete removes the task from the swarm entirely: board row, body, reject notify, and every handoff (pending_approval, inbox, in_process, outbox, sent if still live) whose `task:` is that name. Keep a record of the whole dropped set under `.swarmforge/rejected-tasks/`. After delete, nothing in the swarm still operates on that name.

   Retry (after edit) removes every existing handoff for that `task:`, then queues a note of the edited document to the **master** lane for respecification (same shape as New Task, `to:` whoever is master). The card stays live; it is not REJECTED anymore.

   Today a rejected card only has a × that drops the board row and notify flag. Clicking the card opens `/task` read-only. There is no edit pane. On the squad run, hello was rejected; specifier dropped the spec files and still queued specifier→coder `task: hello` with missing artifacts; that handoff is still in Attention.

3. The receiving agent is responsible for resolving all merge conflicts. Parallel cards on one tree will conflict; that is expected. `ready_for_next` / `merge_and_process` start the merge; the receiver finishes it. Handoffs.prompt says not to invent `git merge` and does not say the agent owns conflict resolution. On the squad run cleaner and architect did resolve `src/htw/core.clj` by hand when Command Syntax landed on an HTW rewrite — the policy needs to be written down.

4. Constitution tools go through `swarm_tool.sh`; do not clone them. QA `git clone`d crap4clj and dry4clj into `./tmp/tools`. Hardender ran `swarm_tool.sh require` for `clj-mutate`, `crap4clj`, `dry4clj`, and `cloverage` and got `Unknown tool`, then web-searched. `swarm_tool.sh` should know every constitution tool (APS plus crap4clj/Cloverage, dry4clj, clj-mutate, and the language table), not only `gherkin-parser` / `gherkin-mutator` / `ir-dry-checker`. Prompt: require/ensure with `swarm_tool.sh`; do not clone GitHub copies into `./tmp`.

5. Session files are per agent, not per card. Update the role’s pane archive on every handoff. Today `archive-all` writes `.swarmforge/sessions/<role>/<task>/pane.txt`, so two cards in one session look like two transcripts. One file per role, refreshed each handoff, is enough.
