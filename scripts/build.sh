#!/usr/bin/env bash
# Runs a Maven command while no other build of the working loop runs on this machine.
# Two builds at the same time can crash this machine, so a second build waits here for the first.
# Usage: scripts/build.sh ./mvnw <arguments>
set -euo pipefail

if [[ "${1:-}" != ./mvnw ]]; then
  echo "Usage: scripts/build.sh ./mvnw <arguments>" >&2
  exit 2
fi
lock=${XDG_CACHE_HOME:-$HOME/.cache}/makemytaxes-worker/build.lock
mkdir -p "$(dirname "$lock")"
exec flock "$lock" "$@"
