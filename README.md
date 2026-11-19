# makeMyTaxes

A local app that prepares a German income tax return. It is my personal project, and also a
showcase for full agentic development, end to end.

**This is not a product.** I build it to prepare my own returns. It is not released for other
people to file with, it covers only part of the tax law, and it gives no tax advice. It is not
an official ELSTER program, and it sends no data to the tax office.

**Status: alpha test.** Versions start at 0.0.1. Features and stored data can change between versions.

## What it does

The UI is in German. A `?` beside each field and question shows a short Italian explanation.

**Years.** For one or two adults, each with a separate return. The returns of one year are linked.
The details of a person fill the main form. A year button creates or opens the return of that
year, from 2022 to 2026. The 2026 review has no line numbers yet.

**Questions.** An interview walks through the topics job, commute and work, insurance, household
and filing. Each answer goes into its form field, and a change in the form shows in the interview.
An uploaded wage tax certificate fills its questions, and the interview skips them.

**Documents.**
- Upload certificates, payslips, receipts and other files, and preview them in the page
- The app recognizes a payslip and its month, and reads the values of a wage tax certificate
- Read values are saved only after you confirm them, and a new version of a document replaces
  the old one and its values after the same check
- The payslips show which months are there, and how their sum compares with the certificate
- Expenses with a date, an amount and their evidence: a receipt or a stated basis

**Forms and review.**
- Forms: main form, Anlage N, Anlage Vorsorgeaufwand, Haushaltsnahe Aufwendungen and
  Außergewöhnliche Belastungen
- A filing check says whether a return is mandatory, with the legal source for each reason
- The pages of a return are tabs, and a tab turns green when its required answers are saved.
  Each field saves when you leave it. Date and amount fields have input masks and a calendar
- The review lists the values in the line order of Mein ELSTER, each with its document or the
  note that you stated it, and marks missing evidence
- An export puts the review and every linked document into one zip file

**Assistant.** Off by default. Each adult can turn it on for their own returns. It then answers
questions in a chat beside the return, checks the return before the review and gives tips with
their sources, and proposes values that reach a form only after you confirm them.

**Law library.** Pinned, per-year versions of the tax laws and of the official BMF handbooks, so
each tax rule can cite the right wording.

## How it is built

```mermaid
flowchart LR
    browser["Browser"] -->|"127.0.0.1 only"| app
    subgraph machine["Your machine"]
        app["app module<br/>Spring Boot, Thymeleaf, htmx"] --> core["core module<br/>tax logic, no framework"]
        app --> db[("Encrypted SQLite")]
    end
```

- Java 25, Spring Boot, Thymeleaf, htmx and an encrypted SQLite database
- The tax logic is in a `core` module with no framework
- The app binds to `127.0.0.1`, and it rejects requests from other sites
- Every feature that a user can see has a Playwright test
- A commit hook stops personal data and secrets before they enter the history

## Use the app

You need Java 25. The Maven wrapper downloads Maven.

```
./mvnw -q -B -DskipTests clean package
MAKEMYTAXES_DATABASE_PASSPHRASE=<passphrase> java -jar app/target/app-*.jar
```

Then open <http://127.0.0.1:9091/>. The database is at `~/.local/share/makemytaxes/makemytaxes.db`.
Keep the passphrase. The app cannot open the database without it. The dev container also uses
port 9091, so stop it first with `docker stop makemytaxes-dev`.

### Run a release in Docker

The live container runs a release tag on <http://127.0.0.1:9090/>, with its data in `deploy/data`:

```
cp deploy/.env.example deploy/.env   # then set the passphrase
chmod 600 deploy/.env
scripts/deploy-live.sh v<version>
```

The container does not read the default database path. To keep an existing database, copy it to
`deploy/data/makemytaxes.db` before the first start, and use the same passphrase.

### Run dev in Docker

The dev container runs the latest `origin/dev` on <http://127.0.0.1:9091/>, with made-up test data
only, in `deploy/data-dev`:

```
scripts/deploy-dev.sh
```

On the first start, the script writes a fixed test passphrase to `deploy/.env.dev`. Do not put real
data in the dev container. The working loop runs `scripts/deploy-dev.sh` after each merge into `dev`.

## Run the agents

Agents build this project. You write an issue with the label `agent-ready`, and agents work on it up
to a merge into `dev`. Only you merge into `main`.

```mermaid
flowchart LR
    issue["Issue"] --> work["Worker agent"] --> review["Review agent"] --> dev["dev"] --> main["main"]
    review -->|"changes"| fix["Fix agent"] --> review
```

The working loop is its own project, [minion-army](https://github.com/colla69/minion-army). It runs on
the owner's machine and works on each repository that has a `.minion/config.sh` on `dev`. The folder
`.minion/` holds what the loop needs to know about makeMyTaxes: the paths that only the owner reviews,
the commands of the agents, and the hooks that prepare a worktree, scan before a review and deploy
the dev container. The hooks read the private scan terms from `~/.config/makemytaxes/privacy/`.

`CLAUDE.md` holds the other commands and the rules of the project.
