#!/usr/bin/env bash
# Prints one provision for a tax year, from each version of that year.
# Usage: scripts/law.sh 2022 estg "§ 35a"
set -euo pipefail

if [ "$#" -ne 3 ]; then
  echo "Usage: scripts/law.sh <year> <law> <provision>, for example: scripts/law.sh 2022 estg \"§ 35a\"" >&2
  exit 2
fi
year=$1 law=$2 provision=$3
root=$(cd "$(dirname "$0")/.." && pwd)

extract() {
  awk -v want="## $provision" '
    /^#/ { on = ($0 == want || index($0, want " ") == 1) }
    on' "$1"
}

previous=""
found=0
for file in "$root"/law/text/"$year"-*/"$law".md; do
  if [ ! -f "$file" ]; then
    echo "No text for $law in $year. Run: java scripts/FetchLaws.java" >&2
    exit 1
  fi
  snapshot=$(basename "$(dirname "$file")")
  text=$(extract "$file")
  if [ -z "$text" ]; then
    echo "--- $snapshot: $provision not found"
  elif [ "$text" = "$previous" ]; then
    echo "--- $snapshot: same text as the version above"
    found=1
  else
    echo "--- $snapshot"
    echo "$text"
    found=1
  fi
  previous=$text
done

[ "$found" -eq 1 ] || exit 1
if [ "$law" = "estg" ]; then
  echo "--- Check the application rule for this provision in § 52 EStG: scripts/law.sh $year estg \"§ 52\""
fi
