#!/usr/bin/env bash
# Host-only tests of lib_r007.sh with a stub adb: no device is needed. Each case sets the stub's behaviour through
# environment variables and checks that the library fails closed: a failed capture, kill, query, or script write is
# never taken for success. Each library call runs in a subshell, so its own PASS/FAIL counters stay separate.
#
# Usage: scripts/r007/test_lib_r007.sh
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/bin"
export STUB_DEVICE="$WORK/device"

# The stub adb. The app's private directory is $STUB_DEVICE; logcat reads $STUB_DEVICE/logcat.
cat > "$WORK/bin/adb" <<'STUB'
#!/usr/bin/env bash
[ "${1:-}" = -s ] && shift 2
cmd="$1"; shift
case "$cmd" in
  logcat)
    [ -n "${STUB_LOGCAT_FAIL:-}" ] && { echo "error: device offline" >&2; exit 1; }
    case " $* " in *" -c "*) : > "$STUB_DEVICE/logcat" ;; *) cat "$STUB_DEVICE/logcat" ;; esac ;;
  exec-in)
    [ -n "${STUB_WRITE_FAIL:-}" ] && exit 1
    target="$(sed -E "s/.*cat > ([^' ]+)'.*/\1/" <<< "$*")"
    mkdir -p "$STUB_DEVICE/$(dirname "$target")"
    cat > "$STUB_DEVICE/$target"
    echo "exec-in $target" >> "$STUB_DEVICE/ops" ;;
  shell)
    line="$*"
    case "$line" in
      "run-as "*" kill -9 "*) [ -n "${STUB_KILL_FAIL:-}" ] && exit 1; echo "kill" >> "$STUB_DEVICE/ops" ;;
      *"/proc/"*) [ -n "${STUB_PROC_FAIL:-}" ] && exit 1; echo "${STUB_PROC_ANSWER-absent}" ;;
      "pidof "*) echo "${STUB_PID:-4242}" ;;
      "run-as "*" cat "*) cat "$STUB_DEVICE/${line##* }" ;;
      "run-as "*" mv -f "*)
        set -- $line
        mv -f "$STUB_DEVICE/${@: -2:1}" "$STUB_DEVICE/${@: -1}" && echo "mv ${@: -1}" >> "$STUB_DEVICE/ops" ;;
      "am instrument "*) printf '%s\n' "${STUB_INSTRUMENT:-}" ;;
      *) : ;;
    esac ;;
esac
STUB
chmod +x "$WORK/bin/adb"
export PATH="$WORK/bin:$PATH"
export SERIAL=stub

TESTS=0 BAD=0
ok()  { TESTS=$((TESTS+1)); printf '  ok   %s\n' "$1"; }
bad() { TESTS=$((TESTS+1)); BAD=$((BAD+1)); printf '  FAIL %s\n' "$1"; }

# Runs a library call in a fresh subshell with the library loaded; its output goes to $WORK/out.
lib() { ( source "$HERE/lib_r007.sh"; "$@" ) > "$WORK/out" 2>&1; }
expect_ok()   { local label="$1"; shift; if lib "$@"; then ok "$label"; else bad "$label"; fi; }
expect_fail() { local label="$1"; shift; if lib "$@"; then bad "$label"; else ok "$label"; fi; }

reset_device() { rm -rf "$STUB_DEVICE"; mkdir -p "$STUB_DEVICE"; : > "$STUB_DEVICE/logcat"; : > "$STUB_DEVICE/ops"; }

INSPECTION=$'INSTRUMENTATION_STATUS: r007_pid=777\nINSTRUMENTATION_STATUS: r007_count=3\n'
INSPECTION+=$'INSTRUMENTATION_STATUS_CODE: 7\nINSTRUMENTATION_CODE: -1'
MARKERS=$'1.0 777 777 I R007Inspect: pid=777 phase=INSPECT_BEGIN\n'
MARKERS+=$'1.1 777 777 I R007Inspect: pid=777 phase=INSPECT_END r007_count=3 r007_pid=777'

echo "inspection evidence"
reset_device; printf '%s\n' "$MARKERS" > "$STUB_DEVICE/logcat"
STUB_INSTRUMENT="$INSPECTION" expect_ok "a clean inspection is accepted" r007_inspect_checked
reset_device; printf '%s\n' "$MARKERS" > "$STUB_DEVICE/logcat"
STUB_INSTRUMENT="$INSPECTION" STUB_LOGCAT_FAIL=1 expect_fail "an unreadable logcat rejects the inspection" \
  r007_inspect_checked
reset_device
STUB_INSTRUMENT="$INSPECTION" expect_fail "missing inspector markers reject the inspection" r007_inspect_checked
reset_device
{ echo "0.9 777 777 I R007Fault: pid=777 thread=main op=READ index=0 phase=BEGIN script=Normal"
  for (( i=0; i<20000; i++ )); do echo "0.9 1 1 I R007Fault: pid=1 thread=main op=READ index=$i phase=BEGIN"; done
  printf '%s\n' "$MARKERS"; } > "$STUB_DEVICE/logcat"
STUB_INSTRUMENT="$INSPECTION" expect_fail "a graph line early in a long log rejects the inspection" \
  r007_inspect_checked
reset_device
printf '%s\n' "1.1 777 777 I R007Inspect: pid=777 phase=INSPECT_BEGIN" \
  "1.2 777 777 I R007Inspect: pid=777 phase=INSPECT_END r007_count=9" > "$STUB_DEVICE/logcat"
STUB_INSTRUMENT="$INSPECTION" expect_fail "an end marker with another count rejects the inspection" r007_inspect_checked
reset_device; printf '%s\n' "$MARKERS" > "$STUB_DEVICE/logcat"
STUB_INSTRUMENT=$'INSTRUMENTATION_STATUS: r007_pid=777\nINSTRUMENTATION_CODE: 0' \
  expect_fail "an instrumentation run that did not complete is rejected" r007_inspect_checked

echo "process death"
reset_device
expect_ok "an explicit absent answer confirms the death" r007_kill
reset_device
STUB_KILL_FAIL=1 expect_fail "a failed kill is not a death" r007_kill
reset_device
STUB_PROC_FAIL=1 R007_KILL_POLLS=2 expect_fail "a failed /proc query is not a death" r007_kill
reset_device
STUB_PROC_ANSWER=present R007_KILL_POLLS=2 expect_fail "a process that stays present is not a death" r007_kill
reset_device
STUB_PROC_ANSWER="" R007_KILL_POLLS=2 expect_fail "an empty answer is not a death" r007_kill
reset_device
STUB_PID=$'4242\n4343' expect_fail "two app processes are refused" r007_kill

echo "fault rules"
for rule in "READ * Throw" "READ 0 HoldThenThrow" "WRITE 3 HoldBeforeCommit" \
  "WRITE 3 HoldBeforeCommit ReturnFalseBeforeCommit" "WRITE * CommitThenReportFalse"; do
  expect_ok "valid rule: $rule" r007_valid_rule "$rule"
done
for rule in "WRITE 0 Explode" "READ 0 HoldBeforeCommit" "WRITE 0 Normal Throw" \
  "WRITE 0 HoldBeforeCommit CommitThenHold" "WRITE -1 Normal" "READ 0 Throw Normal" "DELETE 0 Normal" \
  "WRITE 0" "WRITE 0 HoldBeforeCommit Normal Normal"; do
  expect_fail "invalid rule: $rule" r007_valid_rule "$rule"
done
reset_device
expect_fail "an invalid rule is not published" r007_set_faults "WRITE 0 Explode"
[ ! -e "$STUB_DEVICE/files/r007/faults" ] \
  && ok "no script file after the rejected rule" || bad "a script file after the rejected rule"

echo "atomic publication"
reset_device
expect_ok "a valid script is published" r007_set_faults "WRITE 0 Throw" "READ * Normal"
[ "$(cat "$STUB_DEVICE/files/r007/faults" 2>/dev/null)" = $'WRITE 0 Throw\nREAD * Normal' ] \
  && ok "the published script holds the rules" || bad "the published script holds the rules"
[ ! -e "$STUB_DEVICE/files/r007/faults.tmp" ] \
  && ok "the temporary file is renamed away" || bad "the temporary file remains"
[ "$(cat "$STUB_DEVICE/ops")" = $'exec-in files/r007/faults.tmp\nmv files/r007/faults' ] \
  && ok "the script is written to a temporary file, then renamed" \
  || bad "unexpected write sequence: $(tr '\n' ';' < "$STUB_DEVICE/ops")"
reset_device
STUB_WRITE_FAIL=1 expect_fail "a failed write is reported" r007_set_faults "WRITE 0 Throw"
reset_device
expect_ok "an empty script is published for a clear" r007_set_faults
[ -e "$STUB_DEVICE/files/r007/faults" ] && [ ! -s "$STUB_DEVICE/files/r007/faults" ] \
  && ok "the cleared script is an empty file" || bad "the cleared script is an empty file"

echo "evidence"
reset_device
( unset R007_LOG_OUT; source "$HERE/lib_r007.sh"; r007_save_log "case" ) > "$WORK/out" 2>&1 \
  && bad "saving without an evidence file is refused" || ok "saving without an evidence file is refused"
reset_device
( R007_LOG_OUT="$WORK/evidence/run.log"; source "$HERE/lib_r007.sh"; r007_evidence_init "stub-run" ) \
  > "$WORK/out" 2>&1 && [ -s "$WORK/evidence/run.log" ] \
  && ok "the evidence file is created with a header" || bad "the evidence file is created with a header"
grep -qE '^# utc=[^ ]+ host_rev=[0-9a-f]{40} host_changed_files=[0-9]+$' "$WORK/evidence/run.log" \
  && ok "the header records the host revision and the changed-file count" \
  || bad "the header lacks the host revision: $(grep '^# utc=' "$WORK/evidence/run.log")"
reset_device
printf '%s\n' '1.0 5 5 I R007Fault: pid=5 thread=main op=SCRIPT phase=ERROR message="line 1"' > "$STUB_DEVICE/logcat"
R007_LOG_OUT="$WORK/evidence/run.log" expect_fail "a script error fails the case" r007_save_log "case"
reset_device
STUB_LOGCAT_FAIL=1 R007_LOG_OUT="$WORK/evidence/run.log" expect_fail "an unreadable logcat fails the case" \
  r007_save_log "case"
( unset R007_LOG_OUT; source "$HERE/lib_r007.sh"; r007_evidence_init "stub-run" && printf '%s' "$R007_LOG_OUT" ) \
  > "$WORK/default" 2>/dev/null
default_log="$(tail -1 "$WORK/default")"
case "$default_log" in
  */build/r007-evidence/stub-run-stub-*.log) [ -s "$default_log" ] && ok "the default evidence file is created" \
    || bad "the default evidence file is created"; rm -f "$default_log" ;;
  *) bad "the default evidence path: $default_log" ;;
esac

printf '\n%d tests, %d failed\n' "$TESTS" "$BAD"
[ "$BAD" -eq 0 ]
