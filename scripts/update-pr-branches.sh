#!/usr/bin/env bash
# Keeps each open agent pull request into dev up to date: GitHub merges dev into a branch that is
# behind. A pull request with a merge conflict goes back to the fixer, which merges dev by hand.
# This runs before the review, so the review reads a branch that holds the latest dev.
# It does not call Claude.
# Usage: scripts/update-pr-branches.sh
set -euo pipefail

source "$(dirname "$0")/agent-lib.sh"
take_lock

prs=$(gh pr list --repo "$REPO" --state open --base dev --author "$OWNER" \
  --search "-label:needs-owner" --json number,isCrossRepository,headRefName,mergeable,labels \
  --jq '.[] | select(.isCrossRepository | not)
        | [.number, .headRefName, .mergeable, ([.labels[].name] | join(","))] | @tsv')

while IFS=$'\t' read -r number branch mergeable labels; do
  [[ -n "$number" ]] || continue
  case "$mergeable" in
    CONFLICTING)
      # Only a pull request that waits for a review goes back. The fixer already has the others.
      if [[ ",$labels," == *",needs-review,"* ]]; then
        set_pr_label "$number" changes-requested needs-review
        gh pr comment "$number" --repo "$REPO" \
          --body "This pull request has a merge conflict with dev. Merge dev into the branch." >/dev/null
        echo "Pull request $number: merge conflict, sent back."
      fi
      ;;
    MERGEABLE)
      behind=$(gh api "repos/$REPO/compare/$branch...dev" --jq .ahead_by)
      if ((behind > 0)); then
        if gh pr update-branch "$number" --repo "$REPO" >/dev/null 2>&1; then
          echo "Pull request $number: merged $behind new commit(s) of dev into the branch."
        else
          echo "Pull request $number: the update of the branch failed." >&2
        fi
      fi
      ;;
    *) ;; # GitHub still computes the state. The next round checks again.
  esac
done <<<"$prs"
