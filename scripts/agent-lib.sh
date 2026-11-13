# Shared parts of the agent scripts. Source this file, and do not run it.

REPO=colla69/makeMyTaxes
OWNER=colla69

root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
base=${XDG_CACHE_HOME:-$HOME/.cache}/makemytaxes-worker
mkdir -p "$base"

export GIT_CONFIG_COUNT=2
export GIT_CONFIG_KEY_0=credential.helper GIT_CONFIG_VALUE_0=
export GIT_CONFIG_KEY_1=credential.helper GIT_CONFIG_VALUE_1='!gh auth git-credential'

# A slot is one place for an agent run. The loop starts the slots review-1, work-1 and so on, and a run
# by hand gets a slot of its own. A slot runs one script at a time.
AGENT_SLOT=${AGENT_SLOT:-manual-$$}
mkdir -p "$base/claims"

take_lock() { # name
  exec 9>"$base/$1.lock"
  flock -n 9 || { echo "$1 is busy."; exit 0; }
}
take_slot() { take_lock "slot-$AGENT_SLOT"; }

# Runs git while no other agent run changes the shared refs and worktrees of the main checkout.
git_locked() { flock "$base/git.lock" git "$@"; }

# Only one slot chooses work at a time. The lock holds from the choice until claim, or until the script ends.
lock_choice() {
  exec 7>"$base/choice.lock"
  flock 7
}

# Notes the work of this slot, so that no other slot takes it. The note goes when the script ends.
claim() { # "issue N" or "pr N"
  echo "$1" >"$base/claims/$AGENT_SLOT"
  trap release EXIT
  flock -u 7
}
release() { rm -f "$base/claims/$AGENT_SLOT"; }
is_claimed() { cat "$base"/claims/* 2>/dev/null | grep -qx "$1"; }

# Removes the claim of a slot that stopped without its cleanup, for example after a kill.
# The slot lock is free only when no run of that slot is active.
release_stale_claims() {
  local note slot kind number
  for note in "$base"/claims/*; do
    [[ -e "$note" ]] || continue
    slot=${note##*/}
    flock -n "$base/slot-$slot.lock" true || continue
    read -r kind number <"$note"
    if [[ "$kind" == issue ]]; then
      gh issue edit "$number" --repo "$REPO" --remove-label in-progress >/dev/null || continue
    fi
    rm -f "$note"
    echo "Slot $slot stopped during $kind $number. Its claim is gone."
  done
}

# Creates a worktree of a ref, with the files that git ignores and that the checks need.
prepare_worktree() { # directory, git ref
  if [[ ! -s "$root/.privacy/terms.txt" ]]; then
    echo "The private terms are missing, so the privacy scan cannot check them." >&2
    exit 1
  fi
  git_locked -C "$root" worktree remove --force "$1" 2>/dev/null || true
  git_locked -C "$root" worktree add -q --detach "$1" "$2"
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
    is_claimed "issue $candidate" && continue
    if [[ "$(unfinished_blockers "$candidate")" == 0 ]]; then
      echo "$candidate"
      return
    fi
  done
}

remove_worktree() { # directory
  git_locked -C "$root" worktree remove --force "$1" 2>/dev/null || true
}

set_pr_label() { # pull request, label to add, the rest are labels to remove
  local pull_request=$1 add=$2 label
  shift 2
  for label in "$@"; do
    gh pr edit "$pull_request" --repo "$REPO" --remove-label "$label" >/dev/null
  done
  gh pr edit "$pull_request" --repo "$REPO" --add-label "$add" >/dev/null
}

# Brings the local dev branch up to date and deploys origin/dev to the dev container once per commit.
# It runs after the loop merges a pull request and at the start of each round, so a merge by the owner
# also reaches the dev container. A failure here does not stop the round.
# Two slots can merge at the same time, so only one of them syncs. The other one skips it.
sync_dev() {
  (
    flock -n 6 || exit 0
    sync_dev_now
  ) 6>"$base/dev.lock"
}
sync_dev_now() {
  git_locked -C "$root" fetch -q origin dev || { echo "The fetch of dev failed." >&2; return 0; }
  local target
  target=$(git -C "$root" rev-parse origin/dev)
  if [[ "$(git -C "$root" rev-parse HEAD)" != "$target" ]]; then
    if [[ "$(git -C "$root" branch --show-current)" == dev && -z "$(git -C "$root" status --porcelain)" ]]; then
      git_locked -C "$root" merge -q --ff-only origin/dev || echo "The local dev branch did not fast-forward." >&2
    else
      echo "The main checkout is not a clean dev branch, so it stays as it is." >&2
    fi
  fi
  # The commit is noted even when the deploy fails, so a broken deploy does not run every minute.
  if [[ "$(cat "$base/dev-deployed" 2>/dev/null)" != "$target" ]]; then
    echo "$target" >"$base/dev-deployed"
    "$root/scripts/deploy-dev.sh" || echo "The dev container did not update." >&2
  fi
}
