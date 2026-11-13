#!/usr/bin/env bash
# One round of the working loop: finish open work first, then take a new issue.
# Each step stops before any call to Claude if it has no work.
# Usage: scripts/agent-loop.sh
{ # Bash reads this block in full before it runs it, so a change to this file during a run cannot break the run.
set -uo pipefail

scripts=$(dirname "$0")
# A merge by the owner reaches the local dev branch and the dev container here.
(source "$scripts/agent-lib.sh" && sync_dev)
"$scripts/update-pr-branches.sh"
"$scripts/review-next-pr.sh"
"$scripts/label-merged-issues.sh"
"$scripts/fix-next-pr.sh"
"$scripts/work-next-issue.sh"
exit
}
