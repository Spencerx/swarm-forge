#!/usr/bin/env zsh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

find_project_dir() {
  local git_common_dir

  if git_common_dir=$(git -C "$SCRIPT_DIR" rev-parse --git-common-dir 2>/dev/null); then
    if [[ "$git_common_dir" != /* ]]; then
      git_common_dir="$(cd "$SCRIPT_DIR/$git_common_dir" && pwd)"
    fi
    local project_dir="${git_common_dir:h}"
    if [[ -f "$project_dir/.swarmforge/sessions.tsv" ]]; then
      echo "$project_dir"
      return 0
    fi
  fi

  echo "${SCRIPT_DIR:h:h}"
}

PROJECT_DIR="$(find_project_dir)"
SESSIONS_FILE="$PROJECT_DIR/.swarmforge/sessions.tsv"
TMUX_SOCKET_FILE="$PROJECT_DIR/.swarmforge/tmux-socket"

if [[ ! -f "$TMUX_SOCKET_FILE" ]]; then
  echo "Tmux socket file not found: $TMUX_SOCKET_FILE" >&2
  exit 1
fi

TMUX_SOCKET="$(< "$TMUX_SOCKET_FILE")"
TMUX_WINDOW_BASE_INDEX="$(tmux -S "$TMUX_SOCKET" show-options -gqv base-index 2>/dev/null || echo 0)"
if [[ ! "$TMUX_WINDOW_BASE_INDEX" == <-> ]]; then
  TMUX_WINDOW_BASE_INDEX=0
fi
TMUX_PANE_BASE_INDEX="$(tmux -S "$TMUX_SOCKET" show-window-options -gqv pane-base-index 2>/dev/null || echo 0)"
if [[ ! "$TMUX_PANE_BASE_INDEX" == <-> ]]; then
  TMUX_PANE_BASE_INDEX=0
fi

if [[ $# -lt 2 ]]; then
  echo "Usage: notify-agent.sh <target-role-or-index> \"message\"" >&2
  echo "       notify-agent.sh <target-role-or-index> --file <message-file>" >&2
  exit 1
fi

if [[ ! -f "$SESSIONS_FILE" ]]; then
  echo "Sessions file not found: $SESSIONS_FILE" >&2
  exit 1
fi

resolve_session() {
  local target="${1:l}"
  local index role session display agent

  while IFS=$'\t' read -r index role session display agent; do
    if [[ "$target" == "${index:l}" || "$target" == "${role:l}" ]]; then
      echo "$session"
      return 0
    fi
  done < "$SESSIONS_FILE"

  return 1
}

TARGET_SESSION=$(resolve_session "$1") || {
  echo "Unknown target: $1" >&2
  exit 1
}

shift
if [[ "${1:-}" == "--file" ]]; then
  if [[ $# -ne 2 ]]; then
    echo "Usage: notify-agent.sh <target-role-or-index> --file <message-file>" >&2
    exit 1
  fi
  MESSAGE_FILE="$2"
  if [[ ! -f "$MESSAGE_FILE" ]]; then
    echo "Message file not found: $MESSAGE_FILE" >&2
    exit 1
  fi
  MESSAGE="$(< "$MESSAGE_FILE")"
else
  MESSAGE="$*"
fi

tmux -S "$TMUX_SOCKET" send-keys -t "${TARGET_SESSION}:${TMUX_WINDOW_BASE_INDEX}.${TMUX_PANE_BASE_INDEX}" -l -- "$MESSAGE"
sleep 0.15
tmux -S "$TMUX_SOCKET" send-keys -t "${TARGET_SESSION}:${TMUX_WINDOW_BASE_INDEX}.${TMUX_PANE_BASE_INDEX}" C-m
sleep 0.05
tmux -S "$TMUX_SOCKET" send-keys -t "${TARGET_SESSION}:${TMUX_WINDOW_BASE_INDEX}.${TMUX_PANE_BASE_INDEX}" C-j
