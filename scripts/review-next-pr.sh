#!/usr/bin/env bash
# Reviews the oldest waiting pull request into dev in a new session, and merges it if the review
# approves. This script does the merge, so a review cannot reach main or merge other work.
# It runs the review from dev and not from the pull request, so a pull request cannot change its
# own reviewer. With no waiting pull request, it stops before any call to Claude.
# Usage: scripts/review-next-pr.sh
set -euo pipefail

EFFORT=${REVIEWER_EFFORT:-high}
BUDGET=${REVIEWER_BUDGET_USD:-10}
MAX_ROUNDS=${REVIEWER_MAX_ROUNDS:-3}

# A change here can weaken the guards or send data off the machine, so the owner reviews it.
OWNER_ONLY='^(\.github/|\.githooks/|\.claude/|scripts/|pom\.xml$|.*/pom\.xml$|app/src/main/resources/application\.properties$|.*/LocalRequestFilter\.java$|.*/storage/(EncryptedDatabase|DatabaseConfiguration)\.java$)'

source "$(dirname "$0")/agent-lib.sh"
take_lock

# The worker script adds needs-review to each new pull request, so needs-owner must also stop the review.
number=$(gh pr list --repo "$REPO" --state open --base dev --label needs-review --author "$OWNER" \
  --search "-label:needs-owner" --json number,isCrossRepository \
  --jq '[.[] | select(.isCrossRepository | not)] | sort_by(.number) | .[0].number // empty')
if [[ -z "$number" ]]; then
  echo "No pull request waits for a review."
  exit 0
fi

head=$(gh pr view "$number" --repo "$REPO" --json headRefOid --jq .headRefOid)

hand_to_owner() { # message
  set_pr_label "$number" needs-owner needs-review
  gh pr comment "$number" --repo "$REPO" --body "Automatic review stopped: $1" >/dev/null
  echo "Pull request $number: for the owner. $1"
  exit 0
}

send_back() { # message
  set_pr_label "$number" changes-requested needs-review
  gh pr comment "$number" --repo "$REPO" --body "$1" >/dev/null
  echo "Pull request $number: sent back."
  exit 0
}

# Brings the local dev branch and the dev container up to date. A failure here does not undo the merge.
update_dev_environment() {
  git -C "$root" fetch -q origin dev
  if [[ "$(git -C "$root" branch --show-current)" == dev && -z "$(git -C "$root" status --porcelain)" ]]; then
    git -C "$root" merge -q --ff-only origin/dev || echo "The local dev branch did not fast-forward." >&2
  fi
  "$root/scripts/deploy-dev.sh" || echo "The dev container did not update." >&2
}

checks=$(gh pr checks "$number" --repo "$REPO" --required --json bucket --jq '[.[].bucket] | unique | join(" ")' || true)
if [[ -z "$checks" || "$checks" == *pending* ]]; then
  echo "Pull request $number: CI is still running."
  exit 0
fi
if [[ "$checks" != "pass" ]]; then
  send_back "CI failed. Fix the failure, and the review follows."
fi

if [[ "$(gh pr view "$number" --repo "$REPO" --json mergeable --jq .mergeable)" == "CONFLICTING" ]]; then
  send_back "This pull request has a merge conflict with dev. Merge dev into the branch."
fi

rounds=$(gh pr view "$number" --repo "$REPO" --json reviews \
  --jq '[.reviews[] | select(.body | startswith("Automatic review"))] | length')
if ((rounds >= MAX_ROUNDS)); then
  hand_to_owner "$MAX_ROUNDS review rounds did not end in an approval."
fi

changed=$(gh pr diff "$number" --repo "$REPO" --name-only | grep -E "$OWNER_ONLY" || true)
if [[ -n "$changed" ]]; then
  hand_to_owner "the owner reviews changes to these files: $(tr '\n' ' ' <<<"$changed")"
fi

worktree=$base/review
git -C "$root" fetch -q origin dev "pull/$number/head"
prepare_worktree "$worktree" origin/dev
trap 'remove_worktree "$worktree"' EXIT

# The hook can be skipped, so these scans run again here. They print no values.
if ! (cd "$worktree" && scripts/privacy-scan.sh --history) >/dev/null 2>&1; then
  hand_to_owner "the privacy scan found something. Run scripts/privacy-scan.sh --history locally."
fi
if ! (cd "$worktree" && "$(scripts/ensure-gitleaks.sh)" git --redact --no-banner --log-level error .) >/dev/null 2>&1; then
  hand_to_owner "gitleaks found something. Run it locally."
fi

verdict=$(
  cd "$worktree"
  claude -p "/review-pr $number $head" \
    --effort "$EFFORT" \
    --max-budget-usd "$BUDGET" \
    --permission-mode default \
    --allowedTools "Read" "Glob" "Grep" "Agent" "Skill" \
    "Bash(git show:*)" "Bash(git diff:*)" "Bash(git log:*)" \
    "Bash(gh issue view:*)" "Bash(gh pr view:*)" "Bash(gh pr diff:*)" \
    "Bash(gh pr review $number --comment:*)" \
    "Bash(scripts/law.sh:*)" \
    | tail -n 1
) || verdict=""

# Only an exact approval of the commit that the review read can merge.
case "$verdict" in
  "VERDICT: APPROVE $head")
    current=$(gh pr view "$number" --repo "$REPO" --json headRefOid,baseRefName --jq '.baseRefName + " " + .headRefOid')
    if [[ "$current" != "dev $head" ]]; then
      echo "Pull request $number changed during the review. It stays in the queue."
      exit 0
    fi
    gh pr merge "$number" --repo "$REPO" --merge --match-head-commit "$head"
    gh pr edit "$number" --repo "$REPO" --remove-label needs-review >/dev/null
    echo "Pull request $number: merged into dev."
    update_dev_environment
    ;;
  "VERDICT: CHANGES")
    set_pr_label "$number" changes-requested needs-review
    echo "Pull request $number: changes requested."
    ;;
  "VERDICT: OWNER")
    set_pr_label "$number" needs-owner needs-review
    echo "Pull request $number: for the owner."
    ;;
  *)
    hand_to_owner "the review ended without a clear verdict."
    ;;
esac
