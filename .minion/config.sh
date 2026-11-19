# makeMyTaxes in the minion-army working loop. The other settings keep the engine's defaults.

# The owner reviews changes here: they can weaken the guards or send data off the machine.
OWNER_ONLY_PATHS='^(scripts/|(.*/)?pom\.xml$|app/src/main/resources/application\.properties$|.*/LocalRequestFilter\.java$|.*/storage/(EncryptedDatabase|DatabaseConfiguration)\.java$)'

# The worker may go online, to test a script against its real source.
WORKER_TOOLS=(
  "WebSearch" "WebFetch" "Bash(curl:*)" "Bash(python3:*)" "Bash(claude -p:*)"
  "Bash(scripts/build.sh ./mvnw -q -B test -Dtest=*)" "Bash(scripts/build.sh ./mvnw -q -B test -pl * -Dtest=*)" "Bash(scripts/build.sh ./mvnw -q -B -DskipTests test-compile)"
  "Bash(java scripts/*)" "Bash(scripts/*-test.sh)"
  "Bash(scripts/law.sh:*)" "Bash(scripts/law-check.sh:*)" "Bash(scripts/guidance.sh show:*)" "Bash(scripts/guidance.sh list:*)" "Bash(scripts/guidance.sh check)" "Bash(rm target-test.log)"
)
REVIEWER_TOOLS=("Bash(scripts/law.sh:*)")
