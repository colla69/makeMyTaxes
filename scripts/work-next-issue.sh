#!/usr/bin/env bash
# Gives the oldest waiting issue to Claude, in its own worktree, up to a pull request into dev.
# With no waiting issue, it stops before any call to Claude, so a run costs nothing.
# Usage: scripts/work-next-issue.sh [issue number]
set -euo pipefail

LABEL=agent-ready
EFFORT=${WORKER_EFFORT:-medium}
BUDGET=${WORKER_BUDGET_USD:-10}

source "$(dirname "$0")/agent-lib.sh"
take_lock

issue=${1:-$(gh issue list --repo "$REPO" --state open --label "$LABEL" --author "$OWNER" \
  --search "-label:in-progress -label:needs-input" --json number \
  --jq 'sort_by(.number) | .[0].number // empty')}
if [[ -z "$issue" ]]; then
  echo "No issue waits."
  exit 0
fi

# The label is the guard: on a public repository, only the owner can set it.
labelled=$(gh issue view "$issue" --repo "$REPO" --json labels,author \
  --jq "(.author.login == \"$OWNER\") and (.labels | map(.name) | index(\"$LABEL\") != null)")
if [[ "$labelled" != "true" ]]; then
  echo "Issue $issue is not from $OWNER with the label $LABEL." >&2
  exit 1
fi

branch=issue-$issue
worktree=$base/$branch
git -C "$root" fetch -q origin
prepare_worktree "$worktree" origin/dev
git -C "$worktree" switch -q -C "$branch"

gh issue edit "$issue" --repo "$REPO" --add-label in-progress >/dev/null

# Only the listed tools run, so the worker cannot merge, or fetch and send data from the web.
status=0
(
  cd "$worktree"
  claude -p "/work-issue $issue" \
    --effort "$EFFORT" \
    --max-budget-usd "$BUDGET" \
    --permission-mode acceptEdits \
    --allowedTools "Read" "Edit" "Write" "Glob" "Grep" "Agent" "Skill" \
    "Bash(./mvnw:*)" "Bash(scripts/law.sh:*)" "Bash(scripts/law-check.sh:*)" \
    "Bash(git status:*)" "Bash(git diff:*)" "Bash(git log:*)" "Bash(git show:*)" \
    "Bash(git add:*)" "Bash(git rm:*)" "Bash(git mv:*)" "Bash(git commit:*)" \
    "Bash(git push -u origin HEAD)" \
    "Bash(gh issue view:*)" "Bash(gh issue comment:*)" "Bash(gh pr create:*)" "Bash(gh pr view:*)" \
    "Bash(rm target-verify.log)" \
    --disallowedTools "Bash(*--no-verify*)"
) || status=$?

gh issue edit "$issue" --repo "$REPO" --remove-label in-progress --remove-label "$LABEL" >/dev/null
pull_request=$(gh pr list --repo "$REPO" --head "$branch" --state open --json url --jq '.[0].url // empty')
if [[ -n "$pull_request" ]]; then
  gh pr edit "$pull_request" --repo "$REPO" --add-label needs-review >/dev/null
  echo "Issue $issue: $pull_request"
  remove_worktree "$worktree"
else
  gh issue edit "$issue" --repo "$REPO" --add-label needs-input >/dev/null
  gh issue comment "$issue" --repo "$REPO" \
    --body "The worker stopped without a pull request (exit code $status). To run it again, remove \`needs-input\` and add \`$LABEL\`." >/dev/null
  echo "Issue $issue: no pull request. The worktree stays at $worktree." >&2
  exit 1
fi
