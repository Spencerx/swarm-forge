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

# Lieutenant chat shows live thinking status

## Problem

The lieutenant defaults to grok. Chat shows the operator's request, then
waits for the finished answer. It does not show that the lieutenant is
thinking, or what it is thinking about.

## Behavior

The lieutenant agent defaults to grok. While a chat request is pending,
the chat rail shows live status under that request, using the same
pane-status sentences and filtering the board cards use. Each status
line is prefixed with `|`. New status replaces the previous; at most two
lines are shown. Those lines are green. They go away when the answer
arrives.

## Verification

A pending lieutenant chat shows green `|` status under the request, at
most two lines, replaced as the pane status changes. The same filtering
as card status applies. A finished answer has no leftover status lines.
Do not pin prompt wording.

# Lieutenant agent is configurable

## Problem

The host lieutenant defaults to grok, but there is no config line to
pick a different backend or extra CLI flags.

## Behavior

Host `swarmforge/swarmforge.conf` may contain a lieutenant line of the
form `Lieutenant <agent> [extra-cli-args...]`, for example
`Lieutenant grok --yolo`. If the line is omitted, the lieutenant is
grok with no extra args.

## Verification

No lieutenant line: launched agent is grok. `Lieutenant claude --yolo`:
launched agent is claude with `--yolo`. Do not pin prompt wording.

# Attention shows underlined project/task

## Problem

Attention rows name the project and the task in the same weight, so the
project does not stand out when several projects have gates.

## Behavior

Each Attention row shows the pair as `<project>/<task>`: project name
bold, task name plain, the pair underlined.

## Verification

An Attention approval or clarification names the work as
`project/task`, with the project bold, the task not bold, and the pair
underlined. Do not pin prompt wording.

# Lieutenant chat scroll follows the bottom only

## Problem

The lieutenant chat history jumps to the bottom on refresh and when new
lines appear, so the operator loses their place.

## Behavior

If the scroller is not at the bottom, the chat rail does not move on
poll or when new lines are added. If the scroller is at the bottom, new
lines append at the bottom and the view stays pinned there.

## Verification

Scrolled away from the bottom: a poll and a new request, status, or
answer leave the view where it was. At the bottom: a new line stays in
view and the scroller remains at the bottom. Do not pin prompt wording.

# Default agents per pack role

## Problem

Pack conf should default each role to a specific agent backend.

## Behavior

Default `swarmforge.conf` agent for each pack role:

- two-pack: coder grok, cleaner codex
- four-pack: specifier codex, coder grok, refactorer grok, architect
  codex
- six-pack: specifier codex, coder grok, cleaner grok, hardender
  codex, architect grok, QA grok

## Verification

Each pack template's conf names those agents on the corresponding
window lines. New Project without editing conf launches those
backends. Do not pin prompt wording.

# Update README with project features

## Problem

The README does not fully describe the forge project features an
operator uses: New/Open/Close project, each project as its own git
repo, lieutenant chat, concurrent project bands, and related dashboard
behavior.

## Behavior

README documents those project features in the same simple style as
the rest of the file.

## Verification

An operator can learn the project/forge workflow from the README
without reading `project-board.md`. Do not pin prompt wording.
