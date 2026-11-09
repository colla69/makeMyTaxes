# Shared parts of the agent scripts. Source this file, and do not run it.

REPO=colla69/makeMyTaxes
OWNER=colla69

root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
base=${XDG_CACHE_HOME:-$HOME/.cache}/makemytaxes-worker
mkdir -p "$base"

export GIT_CONFIG_COUNT=2
export GIT_CONFIG_KEY_0=credential.helper GIT_CONFIG_VALUE_0=
export GIT_CONFIG_KEY_1=credential.helper GIT_CONFIG_VALUE_1='!gh auth git-credential'

# One agent run at a time, so two runs cannot take the same work or pass the budget.
take_lock() {
  exec 9>"$base/lock"
  flock -n 9 || { echo "Another agent run is active."; exit 0; }
}

# Creates a worktree of a ref, with the files that git ignores and that the checks need.
prepare_worktree() { # directory, git ref
  if [[ ! -s "$root/.privacy/terms.txt" ]]; then
    echo "The private terms are missing, so the privacy scan cannot check them." >&2
    exit 1
  fi
  git -C "$root" worktree remove --force "$1" 2>/dev/null || true
  git -C "$root" worktree add -q --detach "$1" "$2"
  cp -r "$root/.privacy" "$1/.privacy"
  if [[ -d "$root/law/text" ]]; then
    mkdir -p "$1/law"
    cp -r "$root/law/text" "$1/law/text"
  else
    (cd "$1" && java scripts/FetchLaws.java)
  fi
  if [[ "$(git -C "$1" config core.hooksPath)" != ".githooks" ]]; then
    echo "The commit hook is not active." >&2
    exit 1
  fi
}

# Prints the number of blockers of an issue that are not done. A blocker is done when it is closed or
# has the label on-dev, because GitHub closes an issue only when its change reaches main.
unfinished_blockers() { # issue
  gh api "repos/$REPO/issues/$1/dependencies/blocked_by?per_page=100" --jq \
    '[.[] | select(.state == "open" and ((.labels | map(.name) | index("on-dev")) | not))] | length'
}

# Prints the oldest issue with the label that has no unfinished blocker.
# A failed lookup prints no count, so the issue counts as blocked until the next round.
next_unblocked_issue() { # label
  local candidate
  for candidate in $(gh issue list --repo "$REPO" --state open --label "$1" --author "$OWNER" --limit 100 \
    --search "-label:in-progress -label:needs-input" --json number --jq 'sort_by(.number) | .[].number'); do
    if [[ "$(unfinished_blockers "$candidate")" == 0 ]]; then
      echo "$candidate"
      return
    fi
  done
}

remove_worktree() { # directory
  git -C "$root" worktree remove --force "$1" 2>/dev/null || true
}

set_pr_label() { # pull request, label to add, the rest are labels to remove
  local pull_request=$1 add=$2 label
  shift 2
  for label in "$@"; do
    gh pr edit "$pull_request" --repo "$REPO" --remove-label "$label" >/dev/null
  done
  gh pr edit "$pull_request" --repo "$REPO" --add-label "$add" >/dev/null
}
