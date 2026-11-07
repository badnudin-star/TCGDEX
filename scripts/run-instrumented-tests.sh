#!/usr/bin/env bash
# Runs the connected tests and reports the cause as a check annotation.
#
# Why a wrapper: reactivecircus/android-emulator-runner executes `script:` with /usr/bin/sh, so
# bash-only features (PIPESTATUS) are unavailable inline, and a plain failure leaves only the job
# log - which is not downloadable from every environment. Everything relevant is therefore turned
# into an ::error:: annotation and a step summary here.
set -u

LOG=/tmp/gradle-instrumented.log
SUMMARY_FILE="${GITHUB_STEP_SUMMARY:-/dev/null}"

set +e
./gradlew --console=plain connectedDebugAndroidTest 2>&1 | tee "$LOG"
outcome=${PIPESTATUS[0]}
set -e

if [ "$outcome" -eq 0 ]; then
  exit 0
fi

# 1) Test-level failures, straight from the connected-test XML results.
python3 scripts/report_instrumented_failures.py . || true

# 2) Everything else: compilation errors, emulator boot problems, Gradle task failures.
problems=$(grep -E '^e: |error:|What went wrong|Execution failed|> Task .* FAILED| FAILED$|There were failing tests|Timed out waiting|PANIC|adb: ' "$LOG" \
  | head -12 | tr '\n' '|' | sed 's/%/%25/g; s/\r//g')
echo "::error::connectedDebugAndroidTest fehlgeschlagen (exit $outcome): $problems"

{
  echo "### connectedDebugAndroidTest fehlgeschlagen"
  echo '```'
  grep -E '^e: |error:|What went wrong|Execution failed|> Task .* FAILED| FAILED$|There were failing tests|Timed out waiting|PANIC|adb: ' -A 6 "$LOG" | head -80
  echo '```'
} >> "$SUMMARY_FILE"

exit "$outcome"
