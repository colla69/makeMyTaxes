#!/usr/bin/env bash
# Proves that scripts/guidance.sh names missing and changed files, pins hashes and prints text,
# with a made-up pin file in a throwaway folder. Needs curl and pdftotext, and no network.
set -uo pipefail

script="$(cd "$(dirname "$0")" && pwd)/guidance.sh"
export GUIDANCE_ROOT
GUIDANCE_ROOT=$(mktemp -d)
trap 'rm -rf "$GUIDANCE_ROOT"' EXIT
mkdir -p "$GUIDANCE_ROOT/law" "$GUIDANCE_ROOT/source"

# A minimal PDF with one line of text. The reader rebuilds the missing cross-reference table.
pdf() { # file, text
  printf '%s\n' "%PDF-1.4" \
    "1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj" \
    "2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj" \
    "3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 300 100] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >> endobj" \
    "4 0 obj << >> stream" \
    "BT /F1 12 Tf 10 50 Td ($2) Tj ET" \
    "endstream endobj" \
    "5 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj" \
    "trailer << /Root 1 0 R >>" \
    "%%EOF" >"$1"
}

pdf "$GUIDANCE_ROOT/source/letter.pdf" "Made-up letter text"
pdf "$GUIDANCE_ROOT/source/form.pdf" "Made-up form instructions"
{
  echo "# A comment line"
  # Like law/guidance.tsv, a row without a hash ends after the download column.
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    test-letter letter 2022-2023 01.02.2021 "IV X - S 0000/00/00000 :001" "A made-up letter" \
    "BStBl I 2021 S. 1" "file://$GUIDANCE_ROOT/source/letter.pdf" direct \
    test-form-2024 instructions 2024 2024 - "Made-up form instructions 2024" \
    "Example > Menu > 2024" "https://example.invalid/forms" browser
} >"$GUIDANCE_ROOT/law/guidance.tsv"

failures=0

expect() { # expected exit code, text the output must hold, description, command arguments...
  local code=$1 text=$2 description=$3 output actual
  shift 3
  output=$("$script" "$@" 2>&1)
  actual=$?
  if [[ $actual -ne $code ]]; then
    echo "FAILED: $description: expected exit $code, got $actual"
    echo "$output"
    failures=$((failures + 1))
  elif [[ $output != *"$text"* ]]; then
    echo "FAILED: $description: the output does not hold \"$text\""
    echo "$output"
    failures=$((failures + 1))
  else
    echo "ok: $description"
  fi
}

expect 1 "MISSING: law/guidance/test-letter.pdf" "the check names the missing letter" check
expect 1 "MISSING: law/guidance/test-form-2024.pdf" "the check names the missing form instructions" check
expect 1 "From:     https://example.invalid/forms" "the check says where to download a missing file" check
expect 0 "downloaded: test-letter" "fetch downloads a direct file" fetch
expect 0 "Save as:  law/guidance/test-form-2024.pdf" "fetch tells the owner where to save a browser file" fetch
cp "$GUIDANCE_ROOT/source/form.pdf" "$GUIDANCE_ROOT/law/guidance/test-form-2024.pdf"
expect 1 "NOT PINNED: law/guidance/test-letter.pdf" "the check fails for a file without a hash" check
expect 0 "pinned: test-form-2024" "fetch --pin records the hash of a present file" fetch --pin
expect 0 "ok: test-form-2024" "the check passes when each file is present and pinned" check
if grep -q '^# A comment line$' "$GUIDANCE_ROOT/law/guidance.tsv"; then
  echo "ok: pinning keeps the comments of the pin file"
else
  echo "FAILED: pinning removed the comment line"
  failures=$((failures + 1))
fi
expect 0 "Made-up letter text" "show prints the text of a letter" show test-letter
expect 0 "Reference: IV X - S 0000/00/00000 :001" "show prints the reference of a letter" show test-letter
expect 1 "No entry nothing" "show names an unknown entry" show nothing
expect 0 "test-letter" "list for 2023 holds a letter for 2022 to 2023" list 2023
output=$("$script" list 2024)
if [[ $output == *test-letter* || $output != *test-form-2024* ]]; then
  echo "FAILED: list for 2024 must hold only the form instructions of 2024"
  failures=$((failures + 1))
else
  echo "ok: list for 2024 holds only the entries of 2024"
fi
echo "changed" >>"$GUIDANCE_ROOT/law/guidance/test-letter.pdf"
expect 1 "CHANGED: law/guidance/test-letter.pdf" "the check fails for a changed file" check

if [[ $failures -ne 0 ]]; then
  echo "guidance-test: $failures failure(s)"
  exit 1
fi
