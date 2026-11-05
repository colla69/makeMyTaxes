---
name: promote
description: Promotes dev to main. Reads the incoming commits, sets the version number, opens the pull requests and tags the release. Use when the owner says "/promote" or "promote dev".
disable-model-invocation: true
---

# Promote dev to main

Each run does the next step and then stops, so the owner runs this after each merge. The owner merges
every pull request. Never merge, and never push to `main` or `dev`.

## Find the state

Run `git fetch origin --tags`. Read the version from `<version>` near the top of `pom.xml` on each
branch: `git show origin/main:pom.xml` and `git show origin/dev:pom.xml`.

The incoming commits are `git log origin/main..origin/dev --no-merges --format='%h %s%n%b'`, without
the commits whose subject starts with `chore(release):`.

Do the first step that applies.

## 1. Tag a release that has no tag

If the version on `main` has no `-SNAPSHOT` and the tag `v<version>` does not exist:

1. Run `git tag -a v<version> origin/main -m "Release <version>"`.
2. Run `git push origin v<version>`.
3. Tell the owner that this release is ready to deploy. Then continue with the next step.

## 2. Nothing to promote

If there are no incoming commits, tell the owner that `dev` has nothing new, and stop.

## 3. Open the version pull request into dev

If the version on `main` has `-SNAPSHOT`, `main` has no release yet. A version on `dev` without
`-SNAPSHOT` is then higher, whatever its number, so go to step 4.

If the version on `dev` is not higher than the version on `main`, work out the new version:

- If the version on `dev` has `-SNAPSHOT`, this is the first release. Use the version without
  `-SNAPSHOT`.
- Otherwise, apply the highest bump of the incoming commits to the version on `main`:
  - Major for `!` after the type or a `BREAKING CHANGE:` footer
  - Minor for `feat`
  - Patch for all other types

Then:

1. Create the branch `release/<version>` from `origin/dev`.
2. Run `./mvnw -q -B versions:set -DnewVersion=<version> -DgenerateBackupPoms=false`.
3. Commit all `pom.xml` files with the message `chore(release): <version>`.
4. Push the branch, and run `gh pr create --base dev`. The title is `chore(release): <version>`. The
   body lists the commits and says which commit decided the bump.
5. Tell the owner to merge it and run `/promote` again. Stop.

## 4. Open the pull request into main

If the version on `dev` is higher than the version on `main`, run
`gh pr create --base main --head dev`. The title is `release: <version>`. The body lists the incoming
commits under `Features`, `Fixes` and `Other`. GitHub closes an issue only when its change reaches
`main`, so add `Closes #<issue>` for each issue that the incoming pull requests name. Add a section for any database migration in the
incoming commits, because PROD runs it at the next start.

Tell the owner to review and merge it, then run `/promote` again to tag it. Stop.
