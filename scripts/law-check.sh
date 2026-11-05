#!/usr/bin/env bash
# Checks the law library against wording that is known to differ between versions.
# Run after java scripts/FetchLaws.java. A failure means that the library is wrong.
set -euo pipefail

root=$(cd "$(dirname "$0")/.." && pwd)
failures=0

expect() { # snapshot, law, text, description
  if grep -qF "$3" "$root/law/text/$1/$2.md"; then
    echo "ok: $4"
  else
    echo "FAILED: $4" >&2
    failures=$((failures + 1))
  fi
}

reject() { # snapshot, law, text, description
  if grep -qF "$3" "$root/law/text/$1/$2.md"; then
    echo "FAILED: $4" >&2
    failures=$((failures + 1))
  else
    echo "ok: $4"
  fi
}

expect 2022-12-31 estg "bis 10 347 Euro (Grundfreibetrag)" "the 2022 basic allowance is in the year-end text"
expect 2022-12-31 estg "mehr als 410 Euro beträgt" "the 410 euro limit of § 46 is in the 2022 text"
expect 2022-06-30 estg "einen Betrag von 5 Euro abziehen, höchstens 600 Euro" "the mid-2022 text has the 2022 home office amount"
reject 2022-06-30 estg "6 Euro (Tagespauschale)" "the mid-2022 text does not have the 2023 home office amount"
expect 2022-12-31 estg "6 Euro (Tagespauschale)" "the year-end 2022 text already has the 2023 home office amount"
expect 2022-12-31 estg "in der Fassung des Artikels 1 des Gesetzes vom 16. Dezember 2022 (BGBl. I S. 2294) ist für nach dem 31. Dezember 2022 in der häuslichen Wohnung ausgeübte Tätigkeiten anzuwenden" "§ 52 applies the 2023 amount only after 31.12.2022"

count=$(grep -c '^## § ' "$root/law/text/2022-12-31/ao_1977.md")
if [ "$count" -gt 400 ]; then echo "ok: the AO has $count provisions"; else echo "FAILED: the AO has $count provisions" >&2; failures=$((failures + 1)); fi

if [ "$failures" -ne 0 ]; then
  echo "law-check: $failures failure(s)" >&2
  exit 1
fi
