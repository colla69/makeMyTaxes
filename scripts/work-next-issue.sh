#!/usr/bin/env bash
# Gives the oldest waiting issue to Claude, in its own worktree, up to a pull request into dev.
# An issue waits until each issue that blocks it is on dev or closed. A number given by hand skips this check.
# With no waiting issue, it stops before any call to Claude, so a run costs nothing.
# Usage: scripts/work-next-issue.sh [issue number]
{ # Bash reads this block in full before it runs it, so a change to this file during a run cannot break the run.
set -euo pipefail

LABEL=agent-ready
EFFORT=${WORKER_EFFORT:-medium}
BUDGET=${WORKER_BUDGET_USD:-10}

source "$(dirname "$0")/agent-lib.sh"
take_slot

lock_choice
issue=${1:-$(next_unblocked_issue "$LABEL")}
if [[ -z "$issue" ]]; then
  echo "No issue waits."
  exit 0
fi
if is_claimed "issue $issue"; then
  echo "Another slot works on issue $issue."
  exit 0
fi
claim "issue $issue"

# The label is the guard: on a public repository, only the owner can set it.
labelled=$(gh issue view "$issue" --repo "$REPO" --json labels,author \
  --jq "(.author.login == \"$OWNER\") and (.labels | map(.name) | index(\"$LABEL\") != null)")
if [[ "$labelled" != "true" ]]; then
  echo "Issue $issue is not from $OWNER with the label $LABEL." >&2
  exit 1
fi

branch=issue-$issue
worktree=$base/$branch
git_locked -C "$root" fetch -q origin
prepare_worktree "$worktree" origin/dev
git_locked -C "$worktree" switch -q -C "$branch"

gh issue edit "$issue" --repo "$REPO" --add-label in-progress >/dev/null

# Only the listed tools run, so the worker cannot merge.
# The worker has internet access (WebSearch, WebFetch and curl), so it can send search text and URLs
# off the machine. The owner allows this, so that the worker can test a script against its real source.
# It can run the Java scripts and the script tests, but not the other scripts, such as the deploy.
# It can run python3 and a nested `claude -p`, so it can try out a small server or check how Claude Code
# handles a tool. The nested session uses the same token and counts against the same subscription.
status=0
(
  cd "$worktree"
  claude -p "/work-issue $issue" \
    --effort "$EFFORT" \
    --max-budget-usd "$BUDGET" \
    --permission-mode acceptEdits \
    --allowedTools "Read" "Edit" "Write" "Glob" "Grep" "Agent" "Skill" "WebSearch" "WebFetch" \
    "Bash(scripts/build.sh ./mvnw -q -B test -Dtest=*)" "Bash(scripts/build.sh ./mvnw -q -B -DskipTests test-compile)" \
    "Bash(scripts/law.sh:*)" "Bash(scripts/law-check.sh:*)" \
    "Bash(curl:*)" "Bash(java scripts/*)" "Bash(scripts/*-test.sh)" \
    "Bash(python3:*)" "Bash(claude -p:*)" \
    "Bash(git status:*)" "Bash(git diff:*)" "Bash(git log:*)" "Bash(git show:*)" \
    "Bash(git add:*)" "Bash(git rm:*)" "Bash(git mv:*)" "Bash(git commit:*)" \
    "Bash(git push:*)" \
    "Bash(gh issue view:*)" "Bash(gh issue comment:*)" "Bash(gh pr create:*)" "Bash(gh pr view:*)" \
    "Bash(rm target-test.log)" \
    --disallowedTools "Bash(*--no-verify*)" "Bash(git push*--force*)" "Bash(git push*-f*)" "Bash(git push*+*)"
) || status=$?

gh issue edit "$issue" --repo "$REPO" --remove-label in-progress --remove-label "$LABEL" >/dev/null
# The worker can name its branch issue-N-something, for example when an old branch issue-N exists.
pull_request=
while IFS=$'\t' read -r head url; do
  if [[ "$head" == "$branch" || "$head" == "$branch"-* ]]; then
    pull_request=$url
    break
  fi
done < <(gh pr list --repo "$REPO" --state open --base dev --author "$OWNER" --limit 100 \
  --json headRefName,url --jq '.[] | [.headRefName, .url] | @tsv')
if [[ -n "$pull_request" ]]; then
  gh pr edit "$pull_request" --repo "$REPO" --add-label needs-review >/dev/null
  echo "Issue $issue: $pull_request"
  remove_worktree "$worktree"
else
  gh issue edit "$issue" --repo "$REPO" --add-label needs-input >/dev/null
  gh issue comment "$issue" --repo "$REPO" \
    --body "**Worker:** I stopped without a pull request (exit code $status). Answer in a comment or in the issue body. Then remove \`needs-input\` and add \`$LABEL\`." >/dev/null
  echo "Issue $issue: no pull request. The worktree stays at $worktree." >&2
  exit 1
fi
exit
}
