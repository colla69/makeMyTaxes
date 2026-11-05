#!/usr/bin/env bash
# Proves that each rule of privacy-scan.sh fails when it must, in a throwaway repository.
# The values are made up, and each one is split up so that this file passes its own scan.
set -uo pipefail

scanner="$(cd "$(dirname "$0")" && pwd)/privacy-scan.sh"
repo=$(mktemp -d)
trap 'rm -rf "$repo"' EXIT
cd "$repo" || exit 1
git init -q
git config user.name "Scanner Test"
git config user.email "scanner-test@example.invalid"
unset PRIVATE_TERMS

VALID_TAX_ID="860957""42719"
WRONG_CHECKSUM_TAX_ID="860957""42718"
SPACED_TAX_ID="86 095 ""742 719"
IBAN="DE89 3704 0044 ""0532 0130 00"
FOREIGN_IBAN="GB82 WEST 1234 ""5698 7654 32"
TAX_NUMBER="12/345/""67890"
PRIVATE_TERM="Erika ""Musterfrau"

failures=0

expect() { # expected exit code, description, scan arguments...
  local expected=$1 description=$2 output actual
  shift 2
  output=$("$scanner" "$@" 2>&1)
  actual=$?
  if [[ $actual -ne $expected ]]; then
    echo "FAILED: $description: expected exit $expected, got $actual"
    failures=$((failures + 1))
  else
    echo "ok: $description"
  fi
  LAST_OUTPUT=$output
}

stage() { # file, content
  git reset -q 2>/dev/null
  mkdir -p "$(dirname "$1")"
  printf '%s\n' "$2" >"$1"
  git add -f "$1"
}

expect_staged() { # expected exit code, description, file, content
  stage "$3" "$4"
  expect "$1" "$2" --staged
  git rm -qf --cached "$3"
  rm -f "$3"
}

expect_staged 0 "an ordinary line passes" notes.md "A cost of 1.234,56 euros on 03.02.2022."
expect_staged 1 "a tax ID fails" a.md "ID: $VALID_TAX_ID"
expect_staged 1 "a spaced tax ID fails" a.md "ID: $SPACED_TAX_ID"
expect_staged 0 "11 digits with a wrong checksum pass" a.md "Order $WRONG_CHECKSUM_TAX_ID"
expect_staged 0 "digits inside a hash pass" a.md "SHA-256: a${VALID_TAX_ID}f"
expect_staged 1 "a tax ID at the end of a sentence fails" a.md "My ID is $VALID_TAX_ID."
expect_staged 1 "a tax ID before a comma fails" a.md "ID $VALID_TAX_ID, name"
expect_staged 1 "an IBAN fails" a.md "Account: $IBAN"
expect_staged 1 "a lowercase IBAN fails" a.md "Account: $(tr 'A-Z' 'a-z' <<<"$IBAN")"
expect_staged 1 "a foreign IBAN fails" a.md "Account: $FOREIGN_IBAN"
expect_staged 0 "an invalid IBAN passes" a.md "Code: DE89 3704 0044 0532 0130 01"
stage latin1.txt "$(printf 'Stra\xdfe\nID: %s' "$VALID_TAX_ID")"
expect 1 "a tax ID in a Latin-1 file fails" --staged
git rm -qf --cached latin1.txt
rm -f latin1.txt
expect_staged 1 "a tax number fails" a.md "Number: $TAX_NUMBER"
expect_staged 0 "the allow marker passes" a.md "Number: $TAX_NUMBER privacy-scan:allow"
expect_staged 1 "a PDF fails" receipt.pdf "not really a pdf"
expect_staged 1 "a database fails" data.db "not really a database"
expect_staged 0 "a fixture PDF passes" app/src/test/resources/fixtures/made-up.pdf "made up"

export PRIVATE_TERMS="$PRIVATE_TERM"
expect_staged 1 "a private term fails" a.md "Name: $PRIVATE_TERM"
expect_staged 1 "a private term in another case fails" a.md "Name: ${PRIVATE_TERM^^}"
expect_staged 1 "a private term cannot be allowed" a.md "$PRIVATE_TERM privacy-scan:allow"
expect_staged 1 "a private term in a file name fails" "$PRIVATE_TERM.md" "nothing"

stage a.md "Name: $PRIVATE_TERM"
expect 1 "a private term fails again" --staged
if grep -q "$PRIVATE_TERM" <<<"$LAST_OUTPUT"; then
  echo "FAILED: a finding must not print the value"
  failures=$((failures + 1))
else
  echo "ok: a finding does not print the value"
fi
git rm -qf --cached a.md
rm -f a.md

export PRIVATE_TERMS=$'\n# only a comment\n'
expect_staged 0 "blank lines in the private terms match nothing" a.md "An ordinary line."
unset PRIVATE_TERMS

printf 'Fix for %s\n' "$VALID_TAX_ID" >message.txt
expect 1 "a commit message with a tax ID fails" --message message.txt
rm -f message.txt

stage old.md "Account: $IBAN"
git commit -q --no-verify -m "Add a file"
git rm -q old.md
git commit -q --no-verify -m "Remove the file"
expect 0 "a deleted file passes the scan of tracked files" --tracked
expect 1 "a deleted file fails the scan of the history" --history

if ((failures > 0)); then
  echo "privacy-scan-test: $failures failure(s)"
  exit 1
fi
echo "privacy-scan-test: all checks passed"
