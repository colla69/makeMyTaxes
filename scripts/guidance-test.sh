#!/usr/bin/env bash
# Proves that scripts/guidance.sh checks letters against the handbook text, names missing and
# changed files, pins hashes and prints text, with made-up data in a throwaway folder.
# Needs pdftotext, and no network.
set -uo pipefail

script="$(cd "$(dirname "$0")" && pwd)/guidance.sh"
export GUIDANCE_ROOT
GUIDANCE_ROOT=$(mktemp -d)
trap 'rm -rf "$GUIDANCE_ROOT"' EXIT
mkdir -p "$GUIDANCE_ROOT/law/text" "$GUIDANCE_ROOT/source"

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

pdf "$GUIDANCE_ROOT/source/form.pdf" "Made-up form instructions"
annex=https://esth.example.invalid/esth/2030/B-Anhaenge/Anhang-99/II
cat >"$GUIDANCE_ROOT/law/text/esth-2030.md" <<EOF
Source: https://esth.example.invalid/esth/2030/B-Anhaenge/Anhang-99/I/inhalt.html

## Another made-up letter
Text of another letter

Source: $annex/inhalt.html

## A made-up letter
BMF vom 01.02.2021 (BStBl I S. 1)
IV X – S 0000/00/00000 :001 – 2021/0000001
Made-up letter text

Source: https://esth.example.invalid/esth/2030/B-Anhaenge/Anhang-99/II-alt/inhalt.html

Text of an older version

Source: $annex/anlage-1.html

Made-up attachment text
EOF
{
  echo "# A comment line"
  # Like law/guidance.tsv, a row without a hash ends after the download column.
  # The letter applies to 2029 but is printed in the edition of 2030, which its URL names.
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    test-letter letter 2029 01.02.2021 "IV X – S 0000/00/00000 :001 – 2021/0000001" \
    "A made-up letter" "EStH 2030 Anhang 99 II; BStBl I S. 1" "$annex/inhalt.html" handbook -
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    test-form-2031 instructions 2031 - - "Made-up form instructions 2031" \
    "Example > Menu > 2031" "https://example.invalid/forms" browser \
    test-gap letter 2031 - - "A letter that no source prints" "No handbook prints it" - gap
} >"$GUIDANCE_ROOT/law/guidance.tsv"

failures=0

expect() { # expected exit code, text the output must hold, description, command arguments...
  local code=$1 text=$2 description=$3 output actual
  shift 3
  output=$(bash "$script" "$@" 2>&1)
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

expect 1 "ok: test-letter" "the check finds a letter in the handbook text" check
expect 1 "gap: test-gap. No handbook prints it" "the check names a gap" check
expect 1 "MISSING: law/guidance/test-form-2031.pdf" "the check names the missing form instructions" check
expect 1 "From:     https://example.invalid/forms" "the check says where to download a missing file" check
expect 0 "Save as:  law/guidance/test-form-2031.pdf" "fetch tells the owner where to save a browser file" fetch
cp "$GUIDANCE_ROOT/source/form.pdf" "$GUIDANCE_ROOT/law/guidance/test-form-2031.pdf"
expect 1 "NOT PINNED: law/guidance/test-form-2031.pdf" "the check fails for a file without a hash" check
expect 0 "pinned: test-form-2031" "fetch --pin records the hash of a present file" fetch --pin
expect 0 "ok: test-form-2031" "the check passes when each entry is present and pinned" check
if grep -q '^# A comment line$' "$GUIDANCE_ROOT/law/guidance.tsv"; then
  echo "ok: pinning keeps the comments of the pin file"
else
  echo "FAILED: pinning removed the comment line"
  failures=$((failures + 1))
fi
if grep -q "^test-letter.*handbook	-$" "$GUIDANCE_ROOT/law/guidance.tsv"; then
  echo "ok: pinning leaves a letter row as it is"
else
  echo "FAILED: pinning changed the letter row"
  failures=$((failures + 1))
fi

expect 0 "Made-up letter text" "show prints the text of a letter" show test-letter
expect 0 "Made-up attachment text" "show prints the attachment of a letter" show test-letter
expect 0 "Reference: IV X – S 0000/00/00000 :001" "show prints the reference of a letter" show test-letter
output=$(bash "$script" show test-letter 2>&1)
if [[ $output == *"another letter"* || $output == *"older version"* ]]; then
  echo "FAILED: show must print only the pages of the annex of the letter"
  echo "$output"
  failures=$((failures + 1))
else
  echo "ok: show prints only the pages of the annex of the letter"
fi
expect 0 "Made-up form instructions" "show prints the text of form instructions" show test-form-2031
expect 1 "No official source prints this entry yet" "show names a gap" show test-gap
expect 1 "No entry nothing" "show names an unknown entry" show nothing

output=$(bash "$script" list 2031)
if [[ $output == *test-letter* || $output != *test-form-2031* ]]; then
  echo "FAILED: list for 2031 must hold only the entries of 2031"
  failures=$((failures + 1))
else
  echo "ok: list for 2031 holds only the entries of 2031"
fi

echo "changed" >>"$GUIDANCE_ROOT/law/guidance/test-form-2031.pdf"
expect 1 "CHANGED: law/guidance/test-form-2031.pdf" "the check fails for a changed file" check
sed -i 's/BMF vom 01.02.2021/BMF vom 02.02.2021/' "$GUIDANCE_ROOT/law/text/esth-2030.md"
expect 1 "CHANGED: $annex/inhalt.html does not print the date 01.02.2021" "the check fails when the annex prints another date" check
rm "$GUIDANCE_ROOT/law/text/esth-2030.md"
expect 1 "MISSING: law/text/esth-2030.md, for test-letter" "the check names a missing handbook text" check

if [[ $failures -ne 0 ]]; then
  echo "guidance-test: $failures failure(s)"
  exit 1
fi
