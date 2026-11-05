#!/usr/bin/env bash
# Proves that deploy-live.sh stops when it must, and that only the tag enters the image. A throwaway
# repository and fake docker, curl and sleep commands replace the real ones, so nothing is started.
set -uo pipefail

scripts="$(cd "$(dirname "$0")" && pwd)"
deploy="$scripts/deploy-live.sh"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
failures=0

fail() {
  echo "FAILED: $1"
  failures=$((failures + 1))
}

ok() {
  echo "ok: $1"
}

# The real compose file must keep the app closed to other devices.
compose="$scripts/../deploy/compose.yaml"
if grep -qE '^[[:space:]]*network_mode: host$' "$compose" &&
  ! grep -qE '^[[:space:]]*(ports:|SERVER_ADDRESS:)' "$compose"; then
  ok "the app keeps its bind to 127.0.0.1"
else
  fail "the app keeps its bind to 127.0.0.1"
fi

# The real repository must ignore the live data and the passphrase.
if git -C "$scripts/.." check-ignore -q deploy/.env &&
  git -C "$scripts/.." check-ignore -q deploy/data/made-up.txt; then
  ok "git ignores the live data and the passphrase"
else
  fail "git ignores the live data and the passphrase"
fi

mkdir "$work/bin" "$work/repo"
cat >"$work/bin/docker" <<'EOF'
#!/usr/bin/env bash
echo "docker $*" >>"$FAKE_LOG"
if [[ $1 == build ]]; then
  tar -tf - >"$FAKE_LOG.context"
fi
EOF
cat >"$work/bin/curl" <<'EOF'
#!/usr/bin/env bash
exit "$FAKE_CURL_EXIT"
EOF
printf '#!/usr/bin/env bash\n' >"$work/bin/sleep"
chmod +x "$work/bin/"*
export PATH="$work/bin:$PATH" FAKE_LOG="$work/log" FAKE_CURL_EXIT=0

cd "$work/repo" || exit 1
git init -q
git config user.name "Deploy Test"
git config user.email "deploy-test@example.invalid"
git config core.hooksPath /dev/null
mkdir -p deploy/data
echo "services: {}" >deploy/compose.yaml
echo "FROM scratch" >deploy/Dockerfile
git add deploy
git commit -qm "chore: make a test release"
git tag -a v1.2.3 -m "Release 1.2.3"
echo "made-up data" >deploy/data/made-up.db

expect() { # expected exit code, description, deploy arguments...
  local expected=$1 description=$2 actual
  shift 2
  : >"$FAKE_LOG"
  bash "$deploy" "$@" >/dev/null 2>&1
  actual=$?
  if [[ $actual -eq $expected ]]; then
    ok "$description"
  else
    fail "$description: expected exit $expected, got $actual"
  fi
}

expect_no_docker() { # description
  if [[ -s $FAKE_LOG ]]; then
    fail "$1: docker ran"
  else
    ok "$1: docker did not run"
  fi
}

expect 2 "no tag"
expect 2 "a value that is not a version tag" main
expect_no_docker "a value that is not a version tag"
expect 1 "a tag that does not exist" v9.9.9
expect 1 "no deploy/.env" v1.2.3
expect_no_docker "no deploy/.env"

echo "MAKEMYTAXES_DATABASE_PASSPHRASE='change-this-passphrase'" >deploy/.env
expect 1 "the example passphrase" v1.2.3
expect_no_docker "the example passphrase"

echo "MAKEMYTAXES_DATABASE_PASSPHRASE='made-up'" >deploy/.env

echo "services: {other: {}}" >deploy/compose.yaml
expect 1 "a compose file that differs from the tag" v1.2.3
expect_no_docker "a compose file that differs from the tag"
git checkout -q deploy/compose.yaml

expect 0 "a deployment" v1.2.3
if grep -q "^docker build .*-t makemytaxes:v1.2.3 -t makemytaxes:live" "$FAKE_LOG"; then
  ok "the build names the tag and the live image"
else
  fail "the build names the tag and the live image"
fi
if grep -q "^docker compose -f deploy/compose.yaml up -d" "$FAKE_LOG"; then
  ok "the container starts"
else
  fail "the container starts"
fi
if grep -q "deploy/Dockerfile" "$FAKE_LOG.context"; then
  ok "the build context holds the tracked files"
else
  fail "the build context holds the tracked files"
fi
if grep -qE "made-up.db|\.env" "$FAKE_LOG.context"; then
  fail "the build context holds the data or the passphrase"
else
  ok "the build context does not hold the data or the passphrase"
fi

FAKE_CURL_EXIT=7
expect 1 "a container that does not answer" v1.2.3

if ((failures > 0)); then
  echo "$failures test(s) failed."
  exit 1
fi
echo "All tests passed."
