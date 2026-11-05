# makeMyTaxes

A local app that prepares a German income tax return. It is my personal project, and also a
showcase for full agentic development, end to end.

**This is not a product.** I build it to prepare my own returns. It is not released for other
people to file with, it covers only part of the tax law, and it gives no tax advice. It is not
an official ELSTER program, and it sends no data to the tax office.

## What it does

- Collects expenses with their dates, amounts and receipts
- Checks whether a return is mandatory, with the legal source for each reason
- Holds form modules, and shows each value with its line and evidence for entry in Mein ELSTER
- Holds pinned, per-year versions of the tax laws, so each tax rule can cite the right wording

## How it is built

- Java 25, Spring Boot, Thymeleaf, htmx and an encrypted SQLite database
- The tax logic is in a `core` module with no framework
- The app binds to `127.0.0.1`, and it rejects requests from other sites
- Every feature that a user can see has a Playwright test
- A commit hook stops personal data and secrets before they enter the history

## Run it

```
./mvnw -q -B -DskipTests package
MAKEMYTAXES_DATABASE_PASSPHRASE=<passphrase> java -jar app/target/app-0.1.0-SNAPSHOT.jar
```

`CLAUDE.md` holds the other commands and the rules of the project.
