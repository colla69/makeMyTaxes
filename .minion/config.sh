# The config of makeMyTaxes for the minion-army working loop: https://github.com/colla69/minion-army
# The engine sources this file from dev only. Every change to .minion/ goes to the owner.

# A change here can weaken the guards or send data off the machine, so the owner reviews it.
# The engine adds .minion/, .github/, .githooks/ and .claude/ by itself.
OWNER_ONLY_PATHS='^(scripts/|pom\.xml$|.*/pom\.xml$|app/src/main/resources/application\.properties$|.*/LocalRequestFilter\.java$|.*/storage/(EncryptedDatabase|DatabaseConfiguration)\.java$)'

# The worker has internet access (WebSearch, WebFetch and curl), so it can send search text and URLs
# off the machine. The owner allows this, so that the worker can test a script against its real source.
# It can run the Java scripts and the script tests, but not the other scripts, such as the deploy.
# It can run python3 and a nested `claude -p`, so it can try out a small server or check how Claude Code
# handles a tool. The nested session uses the same token and counts against the same subscription.
WORKER_TOOLS=(
  "WebSearch" "WebFetch"
  "Bash(scripts/build.sh ./mvnw -q -B test -Dtest=*)" "Bash(scripts/build.sh ./mvnw -q -B -DskipTests test-compile)"
  "Bash(scripts/law.sh:*)" "Bash(scripts/law-check.sh:*)"
  "Bash(curl:*)" "Bash(java scripts/*)" "Bash(scripts/*-test.sh)"
  "Bash(python3:*)" "Bash(claude -p:*)"
  "Bash(rm target-test.log)"
)
REVIEWER_TOOLS=("Bash(scripts/law.sh:*)")

WORKER_EFFORT=medium
REVIEWER_EFFORT=high
WORKER_BUDGET_USD=10
REVIEWER_BUDGET_USD=10
MAX_REVIEW_ROUNDS=10
