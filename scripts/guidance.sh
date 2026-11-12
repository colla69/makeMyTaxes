#!/usr/bin/env bash
# The library of BMF letters and official form instructions, pinned in law/guidance.tsv.
# Usage:
#   scripts/guidance.sh fetch [--pin]   download the direct files, and name each file that the
#                                       owner must download in a browser. --pin records the
#                                       hash of each present file that has no hash yet.
#   scripts/guidance.sh check           check that each file is present and matches its hash
#   scripts/guidance.sh list [year]     list the entries, or only those for one tax year
#   scripts/guidance.sh show <id>       print the text of one entry
# Each file is law/guidance/<id>.pdf. Git ignores that folder.
set -euo pipefail

root=${GUIDANCE_ROOT:-$(cd "$(dirname "$0")/.." && pwd)}
pins="$root/law/guidance.tsv"
files="$root/law/guidance"

usage() {
  sed -n '3,9p' "$0" | sed 's/^# \{0,1\}//' >&2
  exit 2
}

sha() { sha256sum "$1" | cut -d' ' -f1; }

# Reads the pin file. Calls the function named in $1 once per entry, with these variables set.
each() {
  local id kind years date reference title source url download sha256
  while IFS=$'\t' read -r id kind years date reference title source url download sha256; do
    [[ -z $id || $id == \#* ]] && continue
    "$1"
  done <"$pins"
}

applies() { # year: true if the entry covers this year
  local from=${years%-*} to=${years#*-}
  [[ $1 -ge $from && $1 -le $to ]]
}

download_hint() {
  echo "  Download: $title ($date)"
  echo "  From:     $url"
  echo "  Find it:  $source"
  echo "  Save as:  law/guidance/$id.pdf"
}

fetch_one() {
  local file="$files/$id.pdf" tmp
  [[ -f $file ]] && return
  if [[ $download == direct ]]; then
    tmp=$(mktemp)
    if curl -fsSL --max-time 60 -o "$tmp" "$url" && [[ $(head -c 4 "$tmp") == %PDF ]]; then
      mv "$tmp" "$file"
      echo "downloaded: $id"
      return
    fi
    rm -f "$tmp"
    echo "FAILED to download $id. Download it by hand:"
  else
    echo "The owner must download $id in a browser:"
  fi
  download_hint
}

fetch() {
  local pin=0
  case "${1:-}" in
    --pin) pin=1 ;;
    "") ;;
    *) usage ;;
  esac
  mkdir -p "$files"
  each fetch_one
  if [[ $pin -eq 1 ]]; then
    pin_present
  fi
}

# Writes the hash of each present file that has none yet into the pin file.
pin_present() {
  local line id file tabs out
  out=$(mktemp)
  while IFS= read -r line; do
    id=${line%%$'\t'*}
    file="$files/$id.pdf"
    line=${line%$'\t'}
    tabs=${line//[!$'\t']/}
    if [[ $line != \#* && -n $line && ${#tabs} -eq 8 && -f $file ]]; then
      line="$line"$'\t'"$(sha "$file")"
      echo "pinned: $id" >&2
    fi
    printf '%s\n' "$line" >>"$out"
  done <"$pins"
  mv "$out" "$pins"
}

failures=0

check_one() {
  local file="$files/$id.pdf"
  if [[ ! -f $file ]]; then
    echo "MISSING: law/guidance/$id.pdf" >&2
    download_hint >&2
    failures=$((failures + 1))
  elif [[ -z $sha256 ]]; then
    echo "NOT PINNED: law/guidance/$id.pdf. Run: scripts/guidance.sh fetch --pin" >&2
    failures=$((failures + 1))
  elif [[ $(sha "$file") != "$sha256" ]]; then
    echo "CHANGED: law/guidance/$id.pdf does not match its hash in law/guidance.tsv" >&2
    failures=$((failures + 1))
  else
    echo "ok: $id"
  fi
}

check() {
  each check_one
  if [[ $failures -ne 0 ]]; then
    echo "guidance-check: $failures failure(s)" >&2
    exit 1
  fi
}

list_one() {
  if [[ -z $want_year ]] || applies "$want_year"; then
    printf '%s\t%s\t%s\t%s\n' "$id" "$years" "$date" "$title"
  fi
}

list() {
  want_year=${1:-}
  each list_one
}

show_one() {
  [[ $id == "$want_id" ]] || return 0
  found=1
  echo "$title"
  echo "Date: $date. Reference: $reference. Source: $source. Years: $years."
  echo "URL: $url"
  echo "---"
  local file="$files/$id.pdf"
  if [[ ! -f $file ]]; then
    echo "The file is missing. Run: scripts/guidance.sh fetch" >&2
    exit 1
  fi
  pdftotext -layout "$file" -
}

show() {
  [[ $# -eq 1 ]] || usage
  want_id=$1 found=0
  each show_one
  if [[ $found -eq 0 ]]; then
    echo "No entry $want_id in law/guidance.tsv. Run: scripts/guidance.sh list" >&2
    exit 1
  fi
}

command=${1:-}
[[ $# -gt 0 ]] && shift
case "$command" in
  fetch) fetch "$@" ;;
  check) check ;;
  list) list "$@" ;;
  show) show "$@" ;;
  *) usage ;;
esac
