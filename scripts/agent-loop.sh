#!/usr/bin/env bash
# One round of the working loop: the housekeeping, then a start of each free slot.
# The settings file sets how many slots run: WORKERS for new issues and corrections, REVIEWERS for
# reviews. A change takes effect in the next round. A slot that runs finishes its work, also when its
# number is lowered. Each slot stops before any call to Claude if it has no work.
# Usage: scripts/agent-loop.sh
{ # Bash reads this block in full before it runs it, so a change to this file during a run cannot break the run.
set -uo pipefail

scripts=$(cd "$(dirname "$0")" && pwd)
source "$scripts/agent-lib.sh"

WORKERS=2
REVIEWERS=2
config=${MAKEMYTAXES_LOOP_CONFIG:-$HOME/.config/makemytaxes/loop.conf}
if [[ -f "$config" ]]; then
  # shellcheck source=/dev/null
  source "$config"
fi

# A merge by the owner reaches the local dev branch and the dev container here.
sync_dev
release_stale_claims
"$scripts/update-pr-branches.sh"
"$scripts/label-merged-issues.sh"

# Starts a slot as its own systemd unit, unless a run of that slot is still active.
# The unit reads the token from the same private file as the timer. It never goes on a command line.
start_slot() { # kind, number
  local slot=$1-$2
  flock -n "$base/slot-$slot.lock" true || return 0
  ${AGENT_START:-systemd-run} --user --quiet --collect --unit="makemytaxes-$slot" \
    --working-directory="$root" \
    --property=EnvironmentFile=-"$HOME/.config/makemytaxes/agent.env" \
    --setenv=PATH="$PATH" --setenv=AGENT_SLOT="$slot" \
    "$scripts/agent-slot.sh" "$1" || echo "Slot $slot did not start." >&2
}

for ((i = 1; i <= REVIEWERS; i++)); do start_slot review "$i"; done
for ((i = 1; i <= WORKERS; i++)); do start_slot work "$i"; done
exit
}
