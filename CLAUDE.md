# makeMyTaxes

An app like Taxfix that the owner uses to create their German tax report every year. It
is a large personal project, and also a training ground and a showcase for full agentic
development, end to end. So how the work is done with agents matters as much as the app.

The app runs locally for privacy. It serves at most two adults. Each adult files a separate
return, and the returns of one year are linked. The app does not support joint returns.

The repository is public-ready at all times. Personal facts go in `CLAUDE.local.md`, which git
ignores, and never in this file or in a commit message. Judge each public text as a future
employer reads it: a reader must not be able to work out personal facts from it.

## Stack

The reason for each choice is in `docs/adr/`. Git ignores that folder, so it exists only on the
owner's machine.

- Java 25 (`java.version` in `pom.xml`), Spring Boot, Maven through `./mvnw`
- A `core` module with no Spring dependency, and an `app` module with everything else
- Dates and amounts are text in the UI, parsed by `GermanFormats`: 03.02.2022 and 1.234,56
- Thymeleaf and htmx, with no Node build
- SQLite, encrypted with a passphrase, and Flyway

## Commands

| Task | Command |
|---|---|
| Build and check everything | `./mvnw -q -B verify` |
| Test one class | `./mvnw -q -B test -Dtest=TaxYearTest -Dsurefire.failIfNoSpecifiedTests=false` |
| Build the app without tests | `./mvnw -q -B -DskipTests package` |
| Run the built app | `MAKEMYTAXES_DATABASE_PASSPHRASE=<passphrase> java -jar app/target/app-0.1.0-SNAPSHOT.jar` |
| Turn on the commit checks, once per clone | `git config core.hooksPath .githooks` |
| Scan every commit for personal data | `scripts/privacy-scan.sh --history` |
| Test the scanner | `scripts/privacy-scan-test.sh` |
| Build the law library, once per clone | `java scripts/FetchLaws.java` |
| Check the law library | `scripts/law-check.sh` |
| Read a provision for a tax year | `scripts/law.sh 2022 estg "§ 35a"` |

A long check runs through the `marketplace-base:test-runner` agent, which runs it on Haiku
and reports only what failed, so the output stays out of the session.

CI runs the build, the browser tests, the law library check and the privacy scan on each pull
request. The Java version of CI is `java.version` in `pom.xml`.

## Map

| Component | Responsibility | Start here |
|---|---|---|
| `core` | Tax logic, with no framework | `core/src/main/java/io/github/colla69/makemytaxes/core/TaxYear.java` |
| `app` | Web UI and storage | `app/src/main/java/io/github/colla69/makemytaxes/app/web/ExpenseController.java` |

## Conventions

- Each Anlage is a form module with the same Java interface.
- Year packs hold values such as limits and rates. Logic is plain Java, with no custom
  rules language.
- Every number on a return traces back to its evidence: a receipt, a stated basis or a
  legal source. The app never suggests a change to an amount, a date or a day count to get
  a better result.
- The first target is a return for one employee, using the main form, Anlage N, Anlage
  Vorsorgeaufwand, Sonderausgaben and Haushaltsnahe Aufwendungen. Other forms come in later
  year packs.
- Dates in the UI and in docs use the format dd.mm.yyyy.
- Filing starts as a review screen in the order of Mein ELSTER. The user enters the values
  by hand.

## Boundaries

- Real data must not enter the repository: not in code, tests, fixtures, docs or commit
  messages. Tests use made-up data only. The commit hooks and CI scan for this.
- Claude can read real data that the owner gives to a session, to analyse it. Do not copy the
  values anywhere else.
- The app binds to `127.0.0.1` and does not call cloud services. `LocalOnlyArchitectureTest`
  checks the project's own classes, but not the libraries.
- Every feature that a user can see ships with a Playwright test in the same commit. The test
  must fail if the feature breaks.
- A tax rule needs a test case that cites an official source. A reviewer's approval is not
  proof of a tax rule.
- Before you answer a tax question or write a tax rule, read the law in `law/text/` with
  `scripts/law.sh`. Do not answer from memory. The date of a version does not decide which
  wording applies to a year, so for each provision:
  1. Read the provision in all versions of that year.
  2. If the provision is in the EStG, read its application rule in § 52 EStG.
  3. Read the `Stand` and `Hinweis` lines at the top of the file. They name changes that the
     text does not hold yet.
  4. Cite the provision and the date of the version.
- The law library does not hold BMF letters, the EStR guidelines or court rulings. A rule that
  depends on them needs its own source.
- The owner reviews every change that could send data off the machine.
- The UI and the adapters depend on the core. The core does not depend on them. The Maven
  Enforcer Plugin stops Spring, the database driver and Flyway from entering `core`.
- Do not add `org.xerial:sqlite-jdbc`. It has no encryption, and the build bans it.
- The connection pool has one connection. Do not run a query inside a row mapper, because it waits forever for that connection.
