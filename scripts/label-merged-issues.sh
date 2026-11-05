#!/usr/bin/env bash
# Adds the label on-dev to each open issue that a pull request merged into dev names with "Closes #N".
# GitHub closes such an issue only when the change reaches main, so the label shows what the owner can try.
# It labels the issues of each pull request once, so the owner can remove the label. It never calls Claude.
# Usage: scripts/label-merged-issues.sh
set -euo pipefail

LABEL=on-dev

source "$(dirname "$0")/agent-lib.sh"
take_lock

seen=$base/on-dev-seen
touch "$seen"

merged=$(gh pr list --repo "$REPO" --base dev --state merged --limit 50 --json number,body --jq '
  sort_by(.number) | .[]
  | "\(.number) \([.body | scan("(?i)\\b(?:close[sd]?|fix(?:e[sd])?|resolve[sd]?):?\\s+#([0-9]+)") | .[0]] | unique | join(" "))"')

open_issues=
while read -r pull_request issues; do
  [[ -n "$pull_request" ]] || continue
  grep -qx "$pull_request" "$seen" && continue
  for issue in $issues; do
    # gh issue list leaves out pull requests, so a number that names a pull request gets no label.
    [[ -n "$open_issues" ]] || open_issues=" $(gh issue list --repo "$REPO" --state open --limit 500 \
      --json number --jq '[.[].number] | join(" ")' </dev/null) "
    if [[ "$open_issues" == *" $issue "* ]]; then
      gh issue edit "$issue" --repo "$REPO" --add-label "$LABEL" </dev/null >/dev/null
      echo "Issue $issue: $LABEL (pull request $pull_request)."
    fi
  done
  # A failed call stops the script before this line, so the next round tries this pull request again.
  echo "$pull_request" >>"$seen"
done <<<"$merged"
