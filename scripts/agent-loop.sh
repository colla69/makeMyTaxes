#!/usr/bin/env bash
# One round of the working loop: finish open work first, then take a new issue.
# Each step stops before any call to Claude if it has no work.
# Usage: scripts/agent-loop.sh
set -uo pipefail

scripts=$(dirname "$0")
"$scripts/review-next-pr.sh"
"$scripts/label-merged-issues.sh"
"$scripts/fix-next-pr.sh"
"$scripts/work-next-issue.sh"
