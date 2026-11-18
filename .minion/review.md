# makeMyTaxes: rules for the review

These rules add to the skill `review-pr`.

- The app holds tax data, so judge security strictly: access to other returns, weaker checks on
  requests, and anything that could send data off the machine.
- The repository is public. Do not quote personal data in a comment.
- A feature that a user can see needs a Playwright test.
- If the diff touches a tax rule, a year pack value, a deadline or a legal citation, start the
  `tax-law-checker` agent with those parts of the diff. Give `VERDICT: APPROVE` only if it reports
  `CORRECT` for each part. Give `VERDICT: OWNER` for a `WRONG` or a `NO SOURCE` that the worker cannot
  settle.
