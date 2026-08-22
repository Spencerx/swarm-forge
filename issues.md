1. In approval window, the documents menu is under the swimlanes and cannot be seen.

The Documents list is `position: absolute; top: 100%` inside Attention. Attention is `max-height: 120px; overflow: auto`. The dropdown therefore opens downward into the board and is clipped by that overflow; `z-index: 20` cannot paint over a sibling that sits below a clipping ancestor. The operator cannot pick a Gherkin/QA file.

**Change:** the menu must paint above the swimlanes (fixed/popover positioning, or Attention `overflow: visible` while open). Do not clip it inside the 120px strip.

**Where:** `pack/dashboard.html` Attention `.menu` / `.attention` overflow and stacking.

2. Thermometer should be based on the difference in the last 20 lines of the session. That difference should be measured by counting the number of lines that are different, regardless of the position of those lines in the tail.

Today heat hashes the whole stripped pane. Any hash change raises heat by 1; an unchanged hash decays by 1. A scroll or reorder of the same lines looks like full activity. Squad already samples `-S -20` for heat; pack does not.

**Change:** take the last 20 lines (after the Codex working filter). Compare to the previous tail as **bags of lines**, not as a string and not in order. Heat is how many of those 20 lines are not in the previous bag (and the reverse: lines that disappeared). Map that count onto the 0–6 bars. Identical lines in a different order are zero difference.

**Where:** `pack_web.bb` `record-heat!` / pane tail; keep Codex chrome strip before the count.

3. Specifier wrote `priority: normal`. The helper requires two digits `00`–`99` (`priority: NN` in the sample). `NN` is not a filled default, so the first draft was invalid. Fill `priority: 50` the same way the helper fills `commit`. Do not make the agent invent a two-digit code.

4. Specifier looked for `swarmforge/sessions.tsv` to find the board card name. That file is `.swarmforge/sessions.tsv` (tmux sessions), not the board. The card list is `.swarmforge/board/tasks.tsv`. Name that path in Tool Startup. The helper already fills `task:` from the card in the sender lane; the agent should not hunt session files for the name.

5. Specifier and coder both appended a prose payload after the `git_handoff` headers. The helper generates that payload (`merge_and_process …`). Any non-blank line after the header block is `HANDOFF INVALID`. Constitution already says headers only; agents still write a body. Ignore/strip draft payload the same way the helper fills `commit` and should fill `priority`. Do not make the agent remember “no body.”

6. Architect `rg`’d the worktree for `ready_for_next` / `swarm_handoff` / `done_with_current` instead of running the names already on PATH. Tool Startup names `swarm_tool.sh require` / `ensure` exactly, but not the receive/send helpers, so the agent reconstructs a search. Same class as the APS `$HOME` hunt.

**Change:** Tool Startup names the exact argv: receive with `ready_for_next.sh`, send with `swarm_handoff.sh ./tmp/<draft>`. Do not search the tree or `$HOME` for those scripts.

**Where:** `swarmforge.bb` `tool-startup-section`.

7. The first command typed in the Specifier composer shows at the **top** of the chat output. It should sit at the **bottom** of that pane. Later command + reply already appear at the bottom.

`#chat-history` is a column that starts at the top. The first bubble therefore hugs the top of the empty box. After more turns the stack is long enough that the live end looks like “the bottom.” There is no stick-to-bottom on first paint (`renderChat` only `replaceChildren`).

**Change:** the output area always pins the latest turn to the bottom, including the first operator line before any reply. Same as squad TS chat (stick to end on first paint and when the operator is already at the bottom).

**Where:** `pack/dashboard.html` `#chat-history` / `renderChat`.

8. Cards should show the agent’s status. Status is the sentence in the session tail that contains `I'm`.

Board cards today are name + lane only. Codex writes status as a sentence such as “I'm idle, so I'm running ready_for_next.sh…” The last such sentence in the pane tail is the live status.

**Change:** for the role that holds the card, read the session tail (same last 20 lines as the thermometer, after the working filter). The status is the last sentence that includes the string `I'm`. Show that sentence on the card. Empty if none. Poll with `/api/state`.

**Where:** `pack_web.bb` `/api/state` tasks; `pack/dashboard.html` `cardEl`.

9. Sometimes QA needs clarification from the operator. That must go through Attention, not the pane.

Constitution today says stop and ask for clarification in the session. The operator cannot see that when windows are invisible. Attention is the human gate.

**Change:** QA (and any role that needs a human answer) posts a clarification request instead of asking in the pane. Attention shows a row labeled **Request clarification**, the question text, and a **text box**. Submit sends the operator’s answer into that agent (same inject path as chat: durable id, wake line, they continue). Do not use Approve/Reject for this; it is not a spec handoff.

**Where:** Attention strip in `pack/dashboard.html`; `pack_web.bb` `/api/state` and a post for the reply; a helper QA can call (like `pack_dashboard_request.sh`); Tool Startup / QA prompt: do not ask in the pane.
