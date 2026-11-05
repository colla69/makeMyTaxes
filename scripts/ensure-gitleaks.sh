#!/usr/bin/env bash
# Prints the path of a pinned, checksum-verified gitleaks, and downloads it if needed.
# The checks fail closed: without gitleaks, the hook and CI stop.
set -euo pipefail

VERSION=8.30.1
ARCHIVE="gitleaks_${VERSION}_linux_x64.tar.gz"
SHA256=551f6fc83ea457d62a0d98237cbad105af8d557003051f41f3e7ca7b3f2470eb

root=$(git rev-parse --show-toplevel)
target="$root/.privacy/bin/gitleaks-$VERSION"

if [[ -x "$target" ]]; then
  echo "$target"
  exit 0
fi

if [[ $(uname -sm) != "Linux x86_64" ]]; then
  echo "ensure-gitleaks: this script supports Linux x86_64 only, not $(uname -sm)." >&2
  exit 1
fi

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
curl -fsSL -o "$work/$ARCHIVE" \
  "https://github.com/gitleaks/gitleaks/releases/download/v$VERSION/$ARCHIVE"
echo "$SHA256  $work/$ARCHIVE" | sha256sum --check --quiet >&2
tar -xzf "$work/$ARCHIVE" -C "$work" gitleaks
mkdir -p "$(dirname "$target")"
install -m 755 "$work/gitleaks" "$target"
echo "$target"
