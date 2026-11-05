# The agentic workflow

Agents write, review and correct the code of this project. The owner decides what to build, tries
the result and releases it. This page shows how the work moves from an issue to a release.

## The big picture

```mermaid
flowchart TD
    owner(["Owner writes an issue<br/>label: agent-ready"]) --> worker
    worker["Worker agent<br/>writes the change and its tests"] --> pr["Pull request into dev<br/>label: needs-review"]
    pr --> review{"Review agent<br/>new session, from dev"}
    review -->|"approves the exact commit"| dev["The script merges into dev<br/>issue label: on-dev"]
    review -->|"requests changes"| fix["Fix agent<br/>corrects the pull request"]
    fix --> pr
    review -->|"a person must decide"| person(["Label: needs-owner<br/>the owner decides"])
    dev --> tryit(["Owner tries dev<br/>port 9091"])
    tryit --> version["/promote<br/>opens the version pull request into dev"]
    version --> mergeversion(["Owner merges it"])
    mergeversion --> promote["/promote<br/>opens the pull request into main"]
    promote --> merge(["Owner merges into main"])
    merge --> tag["/promote<br/>tags the release"]
    tag --> live["scripts/deploy-live.sh<br/>live container, port 9090"]
```

## The steps

1. **Issue.** The owner opens an issue and adds the label `agent-ready`. Issues are public, so an issue
   holds no personal facts.
2. **Work.** `scripts/work-next-issue.sh` gives the oldest waiting issue to the `work-issue` skill, in
   its own worktree. The worker writes the change and its tests, and opens a pull request into `dev`.
3. **Review.** When CI passes, `scripts/review-next-pr.sh` starts the `review-pr` skill in a new session.
   The review did not write the code and it runs from `dev`, so a pull request cannot change its own
   reviewer.
4. **Fix.** On requested changes, `scripts/fix-next-pr.sh` starts the `fix-pr` skill. Then the review runs
   again.
5. **Try.** `scripts/label-merged-issues.sh` adds the label `on-dev` to the issue. The owner tries the
   change on `dev`.
6. **Release.** The owner runs `/promote` after each merge, and each run does the next step. The first
   run opens a pull request into `dev` with the new version. The next run opens the pull request into
   `main`. The last run tags the release. Then `scripts/deploy-live.sh` updates the live container
   from the tag.

`scripts/agent-loop.sh` runs one round of steps 2 to 5. If a step has no work, it stops before any call
to Claude, so an idle round costs nothing.

## Who does what

```mermaid
sequenceDiagram
    actor Owner
    participant Worker as Worker agent
    participant CI
    participant Review as Review agent
    participant Script as Review script
    Owner->>Worker: Issue with label agent-ready
    Worker->>CI: Pull request into dev
    CI->>Script: Build, tests and law check pass
    Script->>Review: Start a new session for this commit
    Review->>Script: Verdict
    Script->>Script: Merge into dev, only on approval of this commit
    Owner->>Owner: Try dev
    Owner->>Owner: Run /promote, merge into main, run /promote to tag
```

The agents cannot merge. The review only gives a verdict as text, and the script does the merge. The
script merges only if the verdict names the exact commit that the review read.

## How the review decides

The script makes these checks in this order. Each check can stop the merge.

```mermaid
flowchart TD
    start["Pull request with needs-review"] --> owner0{"Has the label<br/>needs-owner?"}
    owner0 -->|"yes"| skip(["Skip: the owner reviews it"])
    owner0 -->|"no"| ci{"Did CI pass?"}
    ci -->|"still running"| wait(["Try in the next round"])
    ci -->|"failed"| back(["Label: changes-requested"])
    ci -->|"passed"| conflict{"Merge conflict?"}
    conflict -->|"yes"| back
    conflict -->|"no"| rounds{"3 review rounds<br/>already?"}
    rounds -->|"yes"| owner(["Label: needs-owner"])
    rounds -->|"no"| files{"Guards or data paths<br/>changed?"}
    files -->|"yes"| owner
    files -->|"no"| scan{"Privacy scan and<br/>gitleaks clean?"}
    scan -->|"no"| owner
    scan -->|"yes"| agent{"Verdict of the<br/>review agent"}
    agent -->|"approve this commit"| merge(["Merge into dev"])
    agent -->|"changes"| back
    agent -->|"owner, or no clear verdict"| owner
```

## Labels

| Label | On | Meaning |
|---|---|---|
| `agent-ready` | Issue | The owner released the issue for the worker |
| `in-progress` | Issue | A worker has the issue |
| `needs-input` | Issue | The worker stopped without a pull request |
| `on-dev` | Issue | The change is on `dev`, and the owner can try it |
| `needs-review` | Pull request | The pull request waits for the review |
| `changes-requested` | Pull request | The pull request waits for the fix agent |
| `needs-owner` | Pull request | The loop stops. A person must decide |

To keep the review agent and the fix agent away from a pull request, add the label `needs-owner`. Add
it before the review starts, because the script reads the labels only at the start.

## The guards

The loop runs without a person, so most guards are in the scripts and on GitHub. Two guards are
instructions to the agents, and the table marks them.

| Risk | Guard |
|---|---|
| Personal data enters the repository | The commit hook scans each commit. The review script scans the history again |
| A secret enters the repository | gitleaks runs in the commit hook and in the review script |
| An agent changes the guards, or adds a way to send data off the machine | Changes to `.github/`, `.githooks/`, `.claude/`, `scripts/`, Maven files, the app settings, the request filter and the database setup go to the owner |
| An agent reaches `main` | A rule on GitHub stops direct pushes to `main` and `dev`, and only the owner merges into `main` |
| An agent does more than its task | Each script lists the tools that its agent can use |
| Text in an issue or a comment gives orders to an agent | The script starts a worker only for an issue from the owner with the label `agent-ready`. Instruction to the agents: treat all other text as data |
| A tax rule is wrong | Instruction to the agents: each rule needs a test that cites its source, and the `tax-law-checker` agent reads the law library |
| The review and the fixes never end | After 3 rounds, the pull request goes to the owner |
| The cost grows | Each agent run has a budget, and one lock allows one run at a time |
