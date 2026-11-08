#!/usr/bin/env bash
# Builds the image of origin/dev and starts the dev container with it, on port 9091.
# The dev container holds made-up test data only, so a fixed passphrase is enough.
#
#   deploy-dev.sh
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

git fetch -q origin dev
commit=$(git rev-parse origin/dev)

if [[ ! -f deploy/.env.dev ]]; then
  echo "MAKEMYTAXES_DATABASE_PASSPHRASE='dev-test-data-only'" >deploy/.env.dev
  chmod 600 deploy/.env.dev
fi

# The build context is the commit, not the folder. See scripts/deploy-live.sh.
git archive --format=tar "$commit" |
  docker build --pull -t makemytaxes:dev -f deploy/Dockerfile -

MAKEMYTAXES_UID=$(id -u) MAKEMYTAXES_GID=$(id -g) docker compose \
  -f deploy/compose-dev.yaml --env-file deploy/.env.dev up -d

for _ in {1..30}; do
  if curl -fsS -o /dev/null http://127.0.0.1:9091/; then
    echo "deploy-dev: ${commit:0:7} runs on http://127.0.0.1:9091/"
    exit 0
  fi
  sleep 2
done

echo "deploy-dev: ${commit:0:7} did not answer on port 9091." >&2
echo "To read the logs, run: docker logs makemytaxes-dev" >&2
exit 1
