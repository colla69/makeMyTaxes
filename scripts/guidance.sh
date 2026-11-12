#!/usr/bin/env bash
# The BMF letters and official form instructions that law/guidance.tsv lists.
# Usage:
#   scripts/guidance.sh fetch [--pin]   name each form instruction that the owner must download in
#                                       a browser. --pin records the hash of each downloaded file
#                                       that has no hash yet.
#   scripts/guidance.sh check           check that each entry is present and matches its pin
#   scripts/guidance.sh list [year]     list the entries, or only those of one year
#   scripts/guidance.sh show <id>       print the text of one entry
# A letter is read from the handbook text in law/text, which java scripts/FetchHandbooks.java
# builds. A form instruction is law/guidance/<id>.pdf. Git ignores both folders.
# Set GUIDANCE_ROOT to use another folder in place of the repository, as the test does.
set -euo pipefail

root=${GUIDANCE_ROOT:-$(cd "$(dirname "$0")/.." && pwd)}
pins="$root/law/guidance.tsv"
files="$root/law/guidance"
text="$root/law/text"

usage() {
  sed -n '3,9p' "$0" | sed 's/^# \{0,1\}//' >&2
  exit 2
}

sha() { sha256sum "$1" | cut -d' ' -f1; }

# Reads the pin file. Calls the function named in $1 once per entry, with these variables set.
each() {
  local id kind year date reference title source url download sha256
  while IFS=$'\t' read -r id kind year date reference title source url download sha256 || [[ -n $id ]]; do
    [[ -z $id || $id == \#* ]] && continue
    "$1"
  done <"$pins"
}

# The handbook text that holds a letter. The URL names the handbook and its edition, such as
# law/text/lsth-2024.md for https://lsth.bundesfinanzministerium.de/lsth/2024/...
handbook_file() {
  local path=${url#https://*/}
  local edition=${path#*/}
  echo "$text/${path%%/*}-${edition%%/*}.md"
}

# Prints each page of a handbook text whose URL is in the folder of the annex page of a letter,
# such as the page itself and its attachments. Fails if no page matches.
annex_pages() {
  awk -v folder="${url%/*}/" '
    /^Source: / { on = index(substr($0, 9), folder) == 1; if (on) found = 1 }
    on
    END { if (!found) exit 1 }
  ' "$(handbook_file)"
}

download_hint() {
  echo "  Download: $title"
  echo "  From:     $url"
  echo "  Find it:  $source"
  echo "  Save as:  law/guidance/$id.pdf"
}

fetch_one() {
  [[ $download == browser && ! -f $files/$id.pdf ]] || return 0
  echo "The owner must download $id in a browser:"
  download_hint
}

fetch() {
  case "${1:-}" in
    --pin | "") ;;
    *) usage ;;
  esac
  mkdir -p "$files"
  each fetch_one
  if [[ ${1:-} == --pin ]]; then
    pin_present
  fi
}

# Writes the hash of each downloaded file that has none yet into the pin file.
pin_present() {
  local line id file fields out
  out=$(mktemp)
  while IFS= read -r line || [[ -n $line ]]; do
    id=${line%%$'\t'*}
    file="$files/$id.pdf"
    IFS=$'\t' read -r -a fields <<<"$line"
    if [[ $line != \#* && -n $line && ${fields[8]:-} == browser && -z ${fields[9]:-} && -f $file ]]; then
      line="${line%$'\t'}"$'\t'"$(sha "$file")"
      echo "pinned: $id" >&2
    fi
    printf '%s\n' "$line" >>"$out"
  done <"$pins"
  mv "$out" "$pins"
}

failures=0

fail() {
  echo "$1" >&2
  failures=$((failures + 1))
}

check_letter() {
  local file pages
  file=$(handbook_file)
  if [[ ! -f $file ]]; then
    fail "MISSING: ${file#"$root"/}, for $id. Run: java scripts/FetchHandbooks.java"
  elif ! pages=$(annex_pages); then
    fail "MISSING: $url is not in ${file#"$root"/}, for $id"
  elif ! grep -qF "BMF vom $date " <<<"$pages" || ! grep -qF "$reference" <<<"$pages"; then
    fail "CHANGED: $url does not print the date $date and the reference $reference of $id"
  else
    echo "ok: $id"
  fi
}

check_instructions() {
  local file="$files/$id.pdf"
  if [[ ! -f $file ]]; then
    fail "MISSING: law/guidance/$id.pdf"
    download_hint >&2
  elif [[ -z $sha256 ]]; then
    fail "NOT PINNED: law/guidance/$id.pdf. Run: scripts/guidance.sh fetch --pin"
  elif [[ $(sha "$file") != "$sha256" ]]; then
    fail "CHANGED: law/guidance/$id.pdf does not match its hash in law/guidance.tsv"
  else
    echo "ok: $id"
  fi
}

check_one() {
  case "$download" in
    gap) echo "gap: $id. $source" ;;
    handbook) check_letter ;;
    browser) check_instructions ;;
    *) fail "UNKNOWN: $id has the download type $download" ;;
  esac
}

check() {
  each check_one
  if [[ $failures -ne 0 ]]; then
    echo "guidance-check: $failures failure(s)" >&2
    exit 1
  fi
}

list_one() {
  if [[ -z $want_year || $year == "$want_year" ]]; then
    printf '%s\t%s\t%s\t%s\n' "$id" "$year" "$date" "$title"
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
  echo "Date: $date. Reference: $reference. Source: $source. Year: $year."
  echo "URL: $url"
  echo "---"
  case "$download" in
    gap)
      echo "No official source prints this entry yet." >&2
      exit 1
      ;;
    handbook)
      annex_pages || {
        echo "The handbook text is missing. Run: java scripts/FetchHandbooks.java" >&2
        exit 1
      }
      ;;
    *)
      if [[ ! -f $files/$id.pdf ]]; then
        echo "The file is missing. Run: scripts/guidance.sh fetch" >&2
        exit 1
      fi
      pdftotext -layout "$files/$id.pdf" -
      ;;
  esac
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
