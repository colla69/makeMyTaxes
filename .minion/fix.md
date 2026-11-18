# makeMyTaxes: rules for the fixer

These rules add to the skill `fix-pr`.

- CAUTION: Never run the full test suite on this machine, because it can crash the machine. Run only
  the test classes that your correction needs. CI runs the full suite on the pull request.
- Run tests like this, and name each class:
  `scripts/build.sh ./mvnw -q -B test -Dtest=FirstTest,SecondTest -Dsurefire.failIfNoSpecifiedTests=false > target-test.log 2>&1`
  Read only the failures, and delete the log before you commit. If no test covers the correction,
  run `scripts/build.sh ./mvnw -q -B -DskipTests test-compile`.
- The repository is public. Do not write personal facts in code, tests, commits or the pull request.
- Code that reads from an external source: check the status codes, a few of the values, and that a
  link it follows opens, with a pause between requests. Save a real page as a test case when it shows
  a problem.
