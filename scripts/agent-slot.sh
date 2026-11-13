#!/usr/bin/env bash
# Runs the work of one slot of the working loop. scripts/agent-loop.sh starts each slot as its own
# systemd unit, so a long run of one slot does not hold up the others.
# Usage: scripts/agent-slot.sh review|work
{ # Bash reads this block in full before it runs it, so a change to this file during a run cannot break the run.
set -uo pipefail

scripts=$(cd "$(dirname "$0")" && pwd)
case "${1:-}" in
  review) "$scripts/review-next-pr.sh" ;;
  # Corrections come first, so open pull requests finish before new work starts.
  work)
    "$scripts/fix-next-pr.sh"
    "$scripts/work-next-issue.sh"
    ;;
  *)
    echo "Usage: scripts/agent-slot.sh review|work" >&2
    exit 2
    ;;
esac
exit
}
