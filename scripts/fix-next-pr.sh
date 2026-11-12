#!/usr/bin/env bash
# Gives the oldest pull request with requested changes back to Claude, to make the corrections.
# With no such pull request, it stops before any call to Claude.
# Usage: scripts/fix-next-pr.sh
set -euo pipefail

EFFORT=${WORKER_EFFORT:-medium}
BUDGET=${WORKER_BUDGET_USD:-10}

source "$(dirname "$0")/agent-lib.sh"
take_lock

number=$(gh pr list --repo "$REPO" --state open --base dev --label changes-requested --author "$OWNER" \
  --search "-label:needs-owner" --json number,isCrossRepository \
  --jq '[.[] | select(.isCrossRepository | not)] | sort_by(.number) | .[0].number // empty')
if [[ -z "$number" ]]; then
  echo "No pull request waits for corrections."
  exit 0
fi

branch=$(gh pr view "$number" --repo "$REPO" --json headRefName --jq .headRefName)
head=$(gh pr view "$number" --repo "$REPO" --json headRefOid --jq .headRefOid)
body=$(gh pr view "$number" --repo "$REPO" --json body --jq .body)
worktree=$base/fix-$number
git -C "$root" fetch -q origin "$branch"
prepare_worktree "$worktree" "origin/$branch"
git -C "$worktree" switch -q -C "$branch" "origin/$branch"

# The worker has internet access, as in scripts/work-next-issue.sh, to test a script against its real source.
status=0
(
  cd "$worktree"
  claude -p "/fix-pr $number" \
    --effort "$EFFORT" \
    --max-budget-usd "$BUDGET" \
    --permission-mode acceptEdits \
    --allowedTools "Read" "Edit" "Write" "Glob" "Grep" "Skill" "WebSearch" "WebFetch" \
    "Bash(./mvnw -q -B test -Dtest=*)" "Bash(./mvnw -q -B -DskipTests test-compile)" \
    "Bash(scripts/law.sh:*)" "Bash(scripts/law-check.sh:*)" \
    "Bash(curl:*)" "Bash(java scripts/*)" "Bash(scripts/*-test.sh)" \
    "Bash(git status:*)" "Bash(git diff:*)" "Bash(git log:*)" "Bash(git show:*)" \
    "Bash(git add:*)" "Bash(git rm:*)" "Bash(git mv:*)" "Bash(git commit:*)" \
    "Bash(git fetch origin dev)" "Bash(git merge origin/dev:*)" \
    "Bash(git push:*)" \
    "Bash(gh pr view:*)" "Bash(gh pr edit:*)" "Bash(gh pr diff:*)" "Bash(gh pr checks:*)" "Bash(gh issue view:*)" \
    "Bash(rm target-test.log)" \
    --disallowedTools "Bash(*--no-verify*)" "Bash(git push*--force*)" "Bash(git push*-f*)" "Bash(git push*+*)"
) || status=$?

now=$(gh pr view "$number" --repo "$REPO" --json headRefOid --jq .headRefOid)
now_body=$(gh pr view "$number" --repo "$REPO" --json body --jq .body)
# A new commit or a new pull request body counts as a correction.
if [[ "$now" != "$head" || "$now_body" != "$body" ]]; then
  set_pr_label "$number" needs-review changes-requested
  remove_worktree "$worktree"
  echo "Pull request $number: corrected, and back in the review queue."
else
  set_pr_label "$number" needs-owner changes-requested
  gh pr comment "$number" --repo "$REPO" \
    --body "The worker stopped without a correction (exit code $status)." >/dev/null
  echo "Pull request $number: no correction. The worktree stays at $worktree." >&2
  exit 1
fi
