# makeMyTaxes: rules for the worker

These rules add to the skill `work-issue`.

- The repository is public. Issues hold no personal facts, and neither do code, tests, commits or
  pull requests.
- CAUTION: Never run the full test suite on this machine, because it can crash the machine. Run only
  the test classes that your change needs. CI runs the full suite on the pull request.
- Run tests like this, and name each class:
  `scripts/build.sh ./mvnw -q -B test -Dtest=FirstTest,SecondTest -Dsurefire.failIfNoSpecifiedTests=false > target-test.log 2>&1`
  Read only the failures, and delete the log before you commit. If no test covers the change, run
  `scripts/build.sh ./mvnw -q -B -DskipTests test-compile`.
- A feature that a user can see gets a Playwright test that fails if the feature breaks.
- A tax rule gets a test that cites its provision and the date of the version. Read the law with
  `scripts/law.sh` first.
- If the change touches a tax rule, a year pack, a deadline or a legal citation, start the
  `tax-law-checker` agent with the diff before you commit. Fix each `WRONG`. Do not continue with a
  `NO SOURCE`: comment on the issue with the rule and the source that is missing, and stop.
- Code that reads from an external source: check the status codes, a few of the values, and that a
  link it follows opens, with a pause between requests. A test with a saved page alone does not prove
  that the code works. Save a real page as a test case when it shows a problem.
- The pull request body also lists the legal sources, with the version dates, if the change has a
  tax rule.
