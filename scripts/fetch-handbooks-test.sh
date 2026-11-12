#!/usr/bin/env bash
# Tests the page parsing of scripts/FetchHandbooks.java and the lookup of scripts/handbook.sh.
# Needs no network: it reads a saved page in scripts/testdata/handbook.
set -euo pipefail

root=$(cd "$(dirname "$0")/.." && pwd)
data="$root/scripts/testdata/handbook"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
failures=0

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

java "$root/scripts/FetchHandbooks.java" --convert "$data/page.html" \
  "https://lsth.bundesfinanzministerium.de/lsth/2026/A/R-9-1/inhalt.html" > "$work/page.txt"
check "the page converts to the expected text and links" diff -u "$data/page.expected.txt" "$work/page.txt"

java "$root/scripts/FetchHandbooks.java" --convert "$data/page-base.html" \
  "https://esth.bundesfinanzministerium.de/esth/2025/home.html" > "$work/page-base.txt"
check "links resolve against the base tag of the page" diff -u "$data/page-base.expected.txt" "$work/page-base.txt"

{
  echo "# Amtliches Lohnsteuer-Handbuch 2026"
  echo
  echo "Source: https://lsth.bundesfinanzministerium.de/lsth/2026/A/R-9-1/inhalt.html"
  echo
  grep -v '^Link: ' "$work/page.txt"
  echo
  echo "Source: https://lsth.bundesfinanzministerium.de/lsth/2026/A/R-9-2/inhalt.html"
  echo
  echo "### R 9.2 Folgeseite"
  echo "Text der Folgeseite."
} > "$work/lsth-2026.md"

lookup() { HANDBOOK_TEXT="$work" "$root/scripts/handbook.sh" "$@"; }

lookup 2026 "R 9.1 LStR" > "$work/r91.txt"
check "R 9.1 starts at its heading" grep -qF "### R 9.1 Beispielrichtlinie" "$work/r91.txt"
check "R 9.1 names its page" grep -qF "Source: https://lsth.bundesfinanzministerium.de/lsth/2026/A/R-9-1/inhalt.html" "$work/r91.txt"
check "R 9.1 holds its text" grep -qF "Punkt zwei" "$work/r91.txt"
check "R 9.1 stops before R 9.13" bash -c "! grep -qF 'R 9.13' '$work/r91.txt'"

lookup 2026 "H 9.13 LStH" > "$work/h913.txt"
check "H 9.13 holds its text" grep -qF "Text des Hinweises" "$work/h913.txt"
check "H 9.13 stops at the next page" bash -c "! grep -qF 'Folgeseite' '$work/h913.txt'"

check "an unknown guideline fails" bash -c "! HANDBOOK_TEXT='$work' '$root/scripts/handbook.sh' 2026 'R 99.9 LStR' 2>/dev/null"
check "a year without text fails" bash -c "! HANDBOOK_TEXT='$work' '$root/scripts/handbook.sh' 2026 'H 35a EStH' 2>/dev/null"

if [ "$failures" -ne 0 ]; then
  echo "fetch-handbooks-test: $failures failure(s)" >&2
  exit 1
fi
