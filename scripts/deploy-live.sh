#!/usr/bin/env bash
# Builds the image of a release tag and starts the live container with it.
#
#   deploy-live.sh v1.2.3
#
# Run it from the clone that holds deploy/data and deploy/.env.
set -euo pipefail

tag=${1:-}
if ! [[ $tag =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Usage: deploy-live.sh v<major>.<minor>.<patch>" >&2
  exit 2
fi

cd "$(git rev-parse --show-toplevel)"

if ! git rev-parse -q --verify "refs/tags/$tag" >/dev/null; then
  echo "deploy-live: the tag $tag does not exist. Run git fetch origin --tags." >&2
  exit 1
fi
if [[ ! -f deploy/.env ]]; then
  echo "deploy-live: deploy/.env does not exist. Run this from the clone that holds the live data." >&2
  echo "For a first deployment, copy deploy/.env.example to deploy/.env." >&2
  exit 1
fi
if grep -qF 'change-this-passphrase' deploy/.env; then
  echo "deploy-live: deploy/.env still holds the example passphrase. Set your own." >&2
  exit 1
fi
if ! git diff --quiet "$tag" -- deploy/compose.yaml; then
  echo "deploy-live: deploy/compose.yaml is not the same as in $tag. Check out $tag or main first." >&2
  exit 1
fi

# The build context is the tag, not the folder. So untracked files, such as the live data and
# deploy/.env, cannot enter the image.
git archive --format=tar "$tag" |
  docker build --pull -t "makemytaxes:$tag" -t makemytaxes:live -f deploy/Dockerfile -

MAKEMYTAXES_UID=$(id -u) MAKEMYTAXES_GID=$(id -g) docker compose -f deploy/compose.yaml up -d

for _ in {1..30}; do
  if curl -fsS -o /dev/null http://127.0.0.1:9090/; then
    echo "deploy-live: $tag runs on http://127.0.0.1:9090/"
    exit 0
  fi
  sleep 2
done

echo "deploy-live: $tag did not answer on port 9090." >&2
echo "To read the logs, run: docker compose -f deploy/compose.yaml logs" >&2
exit 1
