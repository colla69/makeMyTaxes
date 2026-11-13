#!/usr/bin/env bash
# Tests the scripts of the working loop. Needs no network and never starts Claude.
# Each case runs the real scripts in a copy of the repository, with a fake gh and a fake claude
# first on PATH. The fake gh answers from the rules of the case and logs each call.
set -uo pipefail

source_root=$(cd "$(dirname "$0")/.." && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
failures=0
LOOP_SCRIPTS="agent-lib.sh agent-loop.sh work-next-issue.sh review-next-pr.sh fix-next-pr.sh label-merged-issues.sh update-pr-branches.sh"

check() { # description, command...
  local description=$1
  shift
  if "$@"; then
    echo "ok: $description"
  else
    echo "FAILED: $description" >&2
    failures=$((failures + 1))
  fi
}
logged() { grep -qF -- "$1" "$case_dir/gh.log"; }
not_logged() { ! grep -qF -- "$1" "$case_dir/gh.log" 2>/dev/null; }
claude_ran() { grep -qF -- "$1" "$case_dir/claude.log" 2>/dev/null; }
claude_never_ran() { [[ ! -s "$case_dir/claude.log" ]]; }

# Creates a copy of the repository with an origin, the fakes and stubs for the deploy and the scans.
new_case() { # name
  case_dir=$work/$1
  repo=$case_dir/repo
  mkdir -p "$case_dir/bin" "$case_dir/cache" "$repo/scripts"
  : >"$case_dir/gh.log"

  cat >"$case_dir/bin/gh" <<'EOF'
#!/usr/bin/env bash
echo "$*" >>"$FAKE/gh.log"
source "$FAKE/rules.sh"
respond "$@"
EOF
  cat >"$case_dir/bin/claude" <<'EOF'
#!/usr/bin/env bash
echo "$*" >>"$FAKE/claude.log"
if [[ -f "$FAKE/claude-action.sh" ]]; then source "$FAKE/claude-action.sh"; fi
EOF
  chmod +x "$case_dir/bin/gh" "$case_dir/bin/claude"

  local script
  for script in $LOOP_SCRIPTS; do cp "$source_root/scripts/$script" "$repo/scripts/"; done
  printf '#!/usr/bin/env bash\necho deployed >>"$FAKE/deploy.log"\n' >"$repo/scripts/deploy-dev.sh"
  printf '#!/usr/bin/env bash\nexit 0\n' >"$repo/scripts/privacy-scan.sh"
  printf '#!/usr/bin/env bash\necho true\n' >"$repo/scripts/ensure-gitleaks.sh"
  chmod +x "$repo/scripts/"*.sh
  printf '.privacy/\nlaw/text/\n' >"$repo/.gitignore"
  mkdir -p "$repo/.privacy" "$repo/law/text"
  echo "made-up-term" >"$repo/.privacy/terms.txt"

  git init -q -b dev "$repo"
  git -C "$repo" config user.email test@example.org
  git -C "$repo" config user.name Test
  git -C "$repo" config core.hooksPath .githooks
  git -C "$repo" add -A
  git -C "$repo" commit -qm "made-up start"
  git init -q --bare "$case_dir/origin.git"
  git -C "$repo" remote add origin "$case_dir/origin.git"
  git -C "$repo" push -q origin dev
  git -C "$repo" push -q origin dev:refs/pull/116/head
  git -C "$repo" fetch -q origin
  # The dev container already runs this commit, so only a new commit deploys.
  mkdir -p "$case_dir/cache/makemytaxes-worker"
  git -C "$repo" rev-parse origin/dev >"$case_dir/cache/makemytaxes-worker/dev-deployed"
  sha=$(git -C "$repo" rev-parse dev)
}

run() { # script, arguments...
  local script=$1
  shift
  FAKE=$case_dir PATH="$case_dir/bin:$PATH" XDG_CACHE_HOME=$case_dir/cache \
    "$repo/scripts/$script" "$@" >"$case_dir/out.log" 2>&1
}

# A loop round with no work at all calls no Claude.
new_case idle
cat >"$case_dir/rules.sh" <<'EOF'
respond() { :; }
EOF
run agent-loop.sh
check "an idle round calls no Claude" claude_never_ran
check "an idle round deploys nothing" test ! -e "$case_dir/deploy.log"

# The worker names its branch issue-104-annexes. The script still finds the pull request.
new_case branch-suffix
cat >"$case_dir/rules.sh" <<'EOF'
respond() {
  case "$*" in
    "issue list"*) echo 104 ;;
    *dependencies/blocked_by*) echo 0 ;;
    "issue view 104"*) echo true ;;
    "pr list"*) printf 'issue-10-old\thttps://example.org/pull/10\nissue-104-annexes\thttps://example.org/pull/116\n' ;;
  esac
}
EOF
run work-next-issue.sh
status=$?
check "a worker branch issue-N-suffix ends with exit code 0" test "$status" -eq 0
check "a worker branch issue-N-suffix gets needs-review" logged "pr edit https://example.org/pull/116 --repo colla69/makeMyTaxes --add-label needs-review"
check "a worker branch issue-N-suffix sets no needs-input" not_logged "--add-label needs-input"
check "the issue loses in-progress after the run" logged "issue edit 104 --repo colla69/makeMyTaxes --remove-label in-progress"

# A pull request of another issue that starts with the same digits is not taken.
new_case other-issue
cat >"$case_dir/rules.sh" <<'EOF'
respond() {
  case "$*" in
    "issue list"*) echo 10 ;;
    *dependencies/blocked_by*) echo 0 ;;
    "issue view 10"*) echo true ;;
    "pr list"*) printf 'issue-104\thttps://example.org/pull/116\n' ;;
  esac
}
EOF
run work-next-issue.sh
check "the pull request of issue 104 does not count for issue 10" not_logged "pull/116 --repo colla69/makeMyTaxes --add-label needs-review"
check "issue 10 without its own pull request gets needs-input" logged "issue edit 10 --repo colla69/makeMyTaxes --add-label needs-input"

# A blocked issue waits, and the next unblocked one runs.
new_case blocked
cat >"$case_dir/rules.sh" <<'EOF'
respond() {
  case "$*" in
    "issue list"*) printf '107\n108\n' ;;
    *issues/107/dependencies/blocked_by*) echo 1 ;;
    *issues/108/dependencies/blocked_by*) echo 0 ;;
    "issue view 108"*) echo true ;;
    "pr list"*) printf 'issue-108\thttps://example.org/pull/120\n' ;;
  esac
}
EOF
run work-next-issue.sh
check "a blocked issue is skipped" claude_ran "/work-issue 108"
check "a blocked issue gets no label" not_logged "issue edit 107"

# A failed lookup of the blockers counts as blocked.
new_case blocker-lookup-fails
cat >"$case_dir/rules.sh" <<'EOF'
respond() {
  case "$*" in
    "issue list"*) echo 107 ;;
    *dependencies/blocked_by*) return 1 ;;
  esac
}
EOF
run work-next-issue.sh
check "a failed lookup of the blockers starts no worker" claude_never_ran

# The worker fails without a pull request. No label stays behind except needs-input.
new_case worker-fails
cat >"$case_dir/rules.sh" <<'EOF'
respond() {
  case "$*" in
    "issue list"*) echo 105 ;;
    *dependencies/blocked_by*) echo 0 ;;
    "issue view 105"*) echo true ;;
  esac
}
EOF
echo "exit 1" >"$case_dir/claude-action.sh"
run work-next-issue.sh
status=$?
check "a failed worker ends with exit code 1" test "$status" -eq 1
check "a failed worker removes in-progress" logged "issue edit 105 --repo colla69/makeMyTaxes --remove-label in-progress"
check "a failed worker sets needs-input" logged "issue edit 105 --repo colla69/makeMyTaxes --add-label needs-input"
check "a failed worker comments on the issue" logged "issue comment 105"

# A change to the script during a run does not break the run.
new_case edit-during-run
cat >"$case_dir/rules.sh" <<'EOF'
respond() {
  case "$*" in
    "issue list"*) echo 106 ;;
    *dependencies/blocked_by*) echo 0 ;;
    "issue view 106"*) echo true ;;
    "pr list"*) printf 'issue-106\thttps://example.org/pull/121\n' ;;
  esac
}
EOF
cat >"$case_dir/claude-action.sh" <<'EOF'
script=$FAKE/repo/scripts/work-next-issue.sh
{ echo '#!/usr/bin/env bash'; echo 'echo "the new version must not run"; exit 7'; } >"$script.new"
mv "$script.new" "$script"
printf '\n%.0s' {1..200} >>"$script"
echo 'if then fi ((' >>"$script"
EOF
run work-next-issue.sh
status=$?
check "a script changed during a run ends with exit code 0" test "$status" -eq 0
check "a script changed during a run still labels the pull request" logged "pull/121 --repo colla69/makeMyTaxes --add-label needs-review"

# The review merges only an approval of the exact commit.
review_rules() { # head commit
  cat >"$case_dir/rules.sh" <<EOF
respond() {
  case "\$*" in
    "pr list"*) echo 116 ;;
    "pr view 116 --repo colla69/makeMyTaxes --json headRefOid,baseRefName"*) echo "dev $1" ;;
    "pr view 116 --repo colla69/makeMyTaxes --json headRefOid"*) echo $1 ;;
    "pr view 116 --repo colla69/makeMyTaxes --json mergeable"*) echo MERGEABLE ;;
    "pr view 116 --repo colla69/makeMyTaxes --json reviews,comments"*) echo 0 ;;
    "pr checks"*) echo pass ;;
    "pr diff"*) echo app/src/main/java/Example.java ;;
  esac
}
EOF
}

new_case review-approves
review_rules "$sha"
echo "echo 'VERDICT: APPROVE $sha'" >"$case_dir/claude-action.sh"
run review-next-pr.sh
check "an approval of the exact commit merges" logged "pr merge 116 --repo colla69/makeMyTaxes --merge --match-head-commit $sha"

new_case review-approves-other-commit
review_rules "$sha"
echo "echo 'VERDICT: APPROVE 0000000000000000000000000000000000000000'" >"$case_dir/claude-action.sh"
run review-next-pr.sh
check "an approval of another commit does not merge" not_logged "pr merge"
check "an approval of another commit goes to the owner" logged "--add-label needs-owner"

new_case review-guarded-file
review_rules "$sha"
sed -i 's|echo app/src/main/java/Example.java|echo scripts/agent-lib.sh|' "$case_dir/rules.sh"
run review-next-pr.sh
check "a change to scripts/ goes to the owner without a review" claude_never_ran
check "a change to scripts/ gets needs-owner" logged "--add-label needs-owner"

# The owner merges a pull request by hand. The next round updates the local dev branch and deploys.
new_case owner-merge
cat >"$case_dir/rules.sh" <<'EOF'
respond() { :; }
EOF
git clone -q -b dev "$case_dir/origin.git" "$case_dir/other"
git -C "$case_dir/other" -c user.email=test@example.org -c user.name=Test commit -q --allow-empty -m "made-up merge"
git -C "$case_dir/other" push -q origin dev
run agent-loop.sh
check "a merge by the owner reaches the local dev branch" \
  test "$(git -C "$repo" rev-parse dev)" = "$(git -C "$case_dir/other" rev-parse dev)"
check "a merge by the owner deploys the dev container once" test "$(wc -l <"$case_dir/deploy.log")" -eq 1
run agent-loop.sh
check "the next round does not deploy again" test "$(wc -l <"$case_dir/deploy.log")" -eq 1

if [ "$failures" -ne 0 ]; then
  echo "agent-loop-test: $failures failure(s)" >&2
  exit 1
fi
