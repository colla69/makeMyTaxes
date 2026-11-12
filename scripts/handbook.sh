#!/usr/bin/env bash
# Prints one guideline (R) or note (H) of the official handbooks for a tax year.
# Usage: scripts/handbook.sh 2026 "R 9.13 LStR"   or   scripts/handbook.sh 2025 "H 35a EStH"
# Set HANDBOOK_TEXT to read the text from another folder, as the test does.
set -euo pipefail

if [ "$#" -ne 2 ]; then
  echo "Usage: scripts/handbook.sh <year> \"<R|H> <number> <EStR|EStH|LStR|LStH>\"" >&2
  exit 2
fi
year=$1 wanted=$2
root=$(cd "$(dirname "$0")/.." && pwd)
text=${HANDBOOK_TEXT:-$root/law/text}

source=${wanted##* }
id=${wanted% *}
case "$source" in
  EStR | EStH) handbook=esth ;;
  LStR | LStH) handbook=lsth ;;
  *)
    echo "Name the source at the end: EStR, EStH, LStR or LStH" >&2
    exit 2
    ;;
esac

file="$text/$handbook-$year.md"
if [ ! -f "$file" ]; then
  echo "No $handbook text for $year. Run: java scripts/FetchHandbooks.java, and see the gaps it lists" >&2
  exit 1
fi

# A section starts at a heading that begins with the wanted id, followed by a space, a dot or the
# end. It ends at the next heading of another guideline, note or paragraph, or at the next page.
awk -v id="$id" '
  function starts(line, rest) {
    if (index(line, id) != 1) return 0
    rest = substr(line, length(id) + 1)
    return rest == "" || rest ~ /^[ .:]/ && rest !~ /^\.[0-9]/
  }
  /^Source: / { page = $0; on = 0; next }
  /^#+ / {
    heading = $0
    sub(/^#+ /, "", heading)
    if (starts(heading)) { on = 1; found = 1; print page; print; next }
    if (heading ~ /^(R|H) [0-9]/ || heading ~ /^§/) on = 0
  }
  on
  END { if (!found) exit 1 }
' "$file" || { echo "$wanted not found in $file" >&2; exit 1; }
