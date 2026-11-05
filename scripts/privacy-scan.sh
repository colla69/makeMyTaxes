#!/usr/bin/env bash
# Keeps personal data out of the repository. See docs/adr/0007-real-data-and-claude.md.
#
#   privacy-scan.sh --staged          what the next commit adds (the pre-commit hook)
#   privacy-scan.sh --message <file>  a commit message (the commit-msg hook)
#   privacy-scan.sh --tracked         every tracked file as it is now
#   privacy-scan.sh --history         every file and message in every commit (CI)
#
# Private terms come from .privacy/terms.txt, or from PRIVATE_TERMS in CI. They are the things
# a pattern cannot know, such as real names and addresses.
#
# A finding names the file, the line and the rule, and never the value. CI logs can be public.
set -euo pipefail

BLOCKED_FILES='\.(db|sqlite|sqlite3|db-journal|db-wal|db-shm|pdf|jpe?g|png|heic|tiff?|docx?|xlsx?|ods|odt|csv|p12|pfx)$'
FIXTURES='(^|/)src/test/resources/fixtures/'
TAX_NUMBER='(^|[^0-9/])[0-9]{2,3}/[0-9]{3,4}/[0-9]{4,5}([^0-9/]|$)'
IBAN='(^|[^A-Za-z0-9])DE[0-9]{2}( ?[0-9]{4}){4} ?[0-9]{2}([^0-9]|$)'
# A digit run inside a word, such as a hash, is not a tax ID.
TAX_ID_CANDIDATE='(?<![0-9A-Za-z.,/-])[1-9][0-9]( ?[0-9]{3}){3}(?![0-9A-Za-z.,/-])'
ALLOW_MARKER='privacy-scan:allow'

root=$(git rev-parse --show-toplevel)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
findings=0

report() { # location, rule
  echo "PRIVACY: $1: $2" >&2
  findings=$((findings + 1))
}

load_private_terms() {
  : >"$work/terms"
  if [[ -f "$root/.privacy/terms.txt" ]]; then
    cat "$root/.privacy/terms.txt" >>"$work/terms"
  fi
  printf '%s\n' "${PRIVATE_TERMS:-}" >>"$work/terms"
  # A blank line would match every line, so blank lines and comments must go.
  grep -vE '^[[:space:]]*(#|$)' "$work/terms" | sed 's/\r$//' >"$work/terms.clean" || true
  if [[ ! -s "$work/terms.clean" ]]; then
    echo "privacy-scan: no private terms, so real names and addresses are not checked." >&2
  fi
}

is_valid_tax_id() { # 11 digits, ISO 7064 MOD 11,10, as the Steuer-ID uses
  local digits=$1 product=10 sum i check
  for ((i = 0; i < 10; i++)); do
    sum=$(((${digits:i:1} + product) % 10))
    ((sum == 0)) && sum=10
    product=$(((sum * 2) % 11))
  done
  check=$((11 - product))
  ((check == 10)) && check=0
  [[ $check -eq ${digits:10:1} ]]
}

check_tax_ids() { # location, file
  local number line digits
  while IFS=: read -r number line; do
    while read -r digits; do
      digits=${digits//[^0-9]/}
      if [[ ${#digits} -eq 11 ]] && is_valid_tax_id "$digits"; then
        report "$1:$number" "looks like a tax ID (Steuer-ID)"
      fi
    done < <(grep -oP "$TAX_ID_CANDIDATE" <<<"$line" || true)
  done < <(grep -nIP "$TAX_ID_CANDIDATE" "$2" | grep -v "$ALLOW_MARKER" || true)
}

check_pattern() { # location, file, pattern, rule
  local number
  while IFS=: read -r number _; do
    report "$1:$number" "$4"
  done < <(grep -nIE "$3" "$2" | grep -v "$ALLOW_MARKER" || true)
}

check_private_terms() { # location, file
  [[ -s "$work/terms.clean" ]] || return 0
  local number
  while IFS=: read -r number _; do
    report "$1:$number" "matches a private term"
  done < <(grep -nIiF -f "$work/terms.clean" "$2" || true)
}

check_content() { # location, file
  check_pattern "$1" "$2" "$IBAN" "looks like an IBAN"
  check_pattern "$1" "$2" "$TAX_NUMBER" "looks like a tax number (Steuernummer)"
  check_tax_ids "$1" "$2"
  check_private_terms "$1" "$2"
}

check_path() { # path
  if [[ $1 =~ $BLOCKED_FILES ]] && ! [[ $1 =~ $FIXTURES ]]; then
    report "$1" "this file type can hold real data, and only src/test/resources/fixtures/ can have it"
  fi
  if [[ -s "$work/terms.clean" ]] && grep -qiF -f "$work/terms.clean" <<<"$1"; then
    report "$1" "the file name matches a private term"
  fi
}

scan_staged() {
  local path
  while IFS= read -r -d '' path; do
    check_path "$path"
    git show ":$path" >"$work/blob"
    check_content "$path" "$work/blob"
  done < <(git diff --cached --name-only --diff-filter=ACMR -z)
}

scan_tracked() {
  local path
  while IFS= read -r -d '' path; do
    [[ -f "$root/$path" ]] || continue
    check_path "$path"
    check_content "$path" "$root/$path"
  done < <(git ls-files -z)
}

scan_history() {
  local blob path
  while read -r blob path; do
    [[ -n "$path" ]] || continue
    [[ $(git cat-file -t "$blob") == blob ]] || continue
    check_path "$path"
    git cat-file blob "$blob" >"$work/blob"
    check_content "$path (blob ${blob:0:8})" "$work/blob"
  done < <(git rev-list --all --objects)

  local commit
  while read -r commit; do
    git log -1 --format=%B "$commit" >"$work/message"
    check_content "commit message ${commit:0:8}" "$work/message"
  done < <(git rev-list --all)
}

load_private_terms
case "${1:-}" in
  --staged) scan_staged ;;
  --message) check_content "commit message" "$2" ;;
  --tracked) scan_tracked ;;
  --history) scan_history ;;
  *)
    echo "Usage: privacy-scan.sh --staged | --message <file> | --tracked | --history" >&2
    exit 2
    ;;
esac

if ((findings > 0)); then
  echo "privacy-scan: $findings finding(s). Remove the data. If a finding is not personal data," >&2
  echo "add $ALLOW_MARKER to that line. A private term cannot be allowed." >&2
  exit 1
fi
echo "privacy-scan: clean (${1})"
