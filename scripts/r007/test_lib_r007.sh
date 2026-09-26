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
      "if pidof "*) [ -n "${STUB_PIDOF_FAIL:-}" ] && exit 1; echo "${STUB_APP_PROC-absent}" ;;
      "am force-stop "*) [ -n "${STUB_STOP_FAIL:-}" ] && exit 1; echo "force-stop" >> "$STUB_DEVICE/ops" ;;
      "dumpsys activity activities")
        [ -n "${STUB_DUMPSYS_FAIL:-}" ] && exit 1
        printf '  KeyguardController:\n%s\n' "${STUB_ACTIVITIES-    mKeyguardShowing=false}" ;;
      "dumpsys window") [ -n "${STUB_DUMPSYS_FAIL:-}" ] && exit 1; printf '%s\n' "${STUB_WINDOW-}" ;;
      *".bak ]; then echo present"*) [ -n "${STUB_QUERY_FAIL:-}" ] && exit 1; echo "${STUB_BAK-absent}" ;;
      "run-as "*" sha256sum "*)
        [ -n "${STUB_HASH_FAIL:-}" ] && exit 1
        f="$STUB_DEVICE/${line##* }"; [ -f "$f" ] || exit 1
        h="$(sha256sum < "$f")"; echo "${h%% *}  ${line##* }" ;;
      "run-as "*"sh -c "*"cat "*" > "*)
        [ -n "${STUB_COPY_FAIL:-}" ] && exit 1
        src="$(sed -E "s/.*cat ([^ ]+) > ([^' ]+)'.*/\1/" <<< "$line")"
        dst="$(sed -E "s/.*cat ([^ ]+) > ([^' ]+)'.*/\2/" <<< "$line")"
        # As in a real shell, the redirection empties the target before cat reads, unless a "[ -f src ] &&" guard
        # stops the command first.
        case "$line" in *"[ -f $src ] && "*) [ -f "$STUB_DEVICE/$src" ] || exit 1 ;; esac
        mkdir -p "$STUB_DEVICE/$(dirname "$dst")"; : > "$STUB_DEVICE/$dst"
        cat "$STUB_DEVICE/$src" > "$STUB_DEVICE/$dst" 2>/dev/null || exit 1 ;;
      "run-as "*" cat "*) cat "$STUB_DEVICE/${line##* }" ;;
      "run-as "*" mv -f "*)
        set -- $line
        mv -f "$STUB_DEVICE/${@: -2:1}" "$STUB_DEVICE/${@: -1}" && echo "mv ${@: -1}" >> "$STUB_DEVICE/ops" ;;
      "am instrument "*)
        [ -n "${STUB_INSPECT_WRITES:-}" ] \
          && echo "<!-- rewritten -->" >> "$STUB_DEVICE/shared_prefs/applock_lockout.xml"
        printf '%s\n' "${STUB_INSTRUMENT:-}" ;;
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

# A store file in the shape that EncryptedSharedPreferences writes: two keysets and two encrypted entries.
STORE_XML="<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name=\"__androidx_security_crypto_encrypted_prefs_key_keyset__\">12a9017c89dd</string>
    <string name=\"ARvWAU0hJztGu9JPCFYD2+/ST84nIyK+xUvgOaZsbRT2pg==\">AT6IpST5vIhIq0Lm8Tz4Kx==</string>
    <string name=\"__androidx_security_crypto_encrypted_prefs_value_keyset__\">12880161d5a9</string>
    <string name=\"ARvWAU2YWvROlzabWFmSEm4G6aRlerLZdG3stk9i9ICFyg==\">AT6IpSQ4o5cjZZ9a</string>
</map>"
STORE="shared_prefs/applock_lockout.xml"

reset_device() {
  rm -rf "$STUB_DEVICE"; mkdir -p "$STUB_DEVICE/shared_prefs"
  : > "$STUB_DEVICE/logcat"; : > "$STUB_DEVICE/ops"
  printf '%s\n' "$STORE_XML" > "$STUB_DEVICE/$STORE"
}
store_is() { [ "$(cat "$STUB_DEVICE/$STORE" 2>/dev/null)" = "$1" ]; }

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
reset_device; printf '%s\n' "$MARKERS" > "$STUB_DEVICE/logcat"
STUB_INSTRUMENT="$INSPECTION" STUB_INSPECT_WRITES=1 \
  expect_fail "an inspection that changes the store file is rejected" r007_inspect_checked
reset_device; printf '%s\n' "$MARKERS" > "$STUB_DEVICE/logcat"
STUB_INSTRUMENT="$INSPECTION" STUB_APP_PROC=present expect_fail "an inspection while the app runs is rejected" \
  r007_inspect_checked
reset_device; printf '%s\n' "$MARKERS" > "$STUB_DEVICE/logcat"; rm "$STUB_DEVICE/$STORE"
STUB_INSTRUMENT="$INSPECTION" expect_fail "an inspection of a store that cannot be hashed is rejected" \
  r007_inspect_checked
reset_device; printf '%s\n' "$MARKERS" > "$STUB_DEVICE/logcat"
STUB_INSTRUMENT="$INSPECTION" STUB_HASH_FAIL=1 expect_fail "a failed hash query rejects the inspection" \
  r007_inspect_checked
reset_device
printf '%s\n' "1.0 778 778 I R007Inspect: pid=778 phase=INSPECT_BEGIN" \
  "1.1 778 778 I R007Inspect: pid=778 phase=INSPECT_END r007_pid=778 r007_read_error=java.lang.SecurityException" \
  > "$STUB_DEVICE/logcat"
READ_ERROR=$'INSTRUMENTATION_STATUS: r007_pid=778\n'
READ_ERROR+=$'INSTRUMENTATION_STATUS: r007_read_error=java.lang.SecurityException\nINSTRUMENTATION_CODE: -1'
STUB_INSTRUMENT="$READ_ERROR" expect_ok "a read-error inspection with no count is accepted" r007_inspect_checked

echo "store file"
reset_device
expect_ok "the store is hashed" r007_store_hash
grep -qE '^[0-9a-f]{64}$' "$WORK/out" && ok "the store hash is one SHA-256" || bad "the store hash: $(cat "$WORK/out")"
reset_device; rm "$STUB_DEVICE/$STORE"
expect_fail "a missing store has no hash" r007_store_hash
reset_device
STUB_HASH_FAIL=1 expect_fail "a failed hash query gives no hash" r007_store_hash
reset_device
expect_ok "the store is saved" r007_store_save
cmp -s "$STUB_DEVICE/$STORE" "$STUB_DEVICE/files/r007/lockout.orig" \
  && ok "the copy equals the store" || bad "the copy differs from the store"
reset_device
STUB_BAK=present expect_fail "a store with a backup file is not saved" r007_store_save
[ ! -e "$STUB_DEVICE/files/r007/lockout.orig" ] && ok "no copy after the refusal" || bad "a copy after the refusal"
reset_device
STUB_QUERY_FAIL=1 expect_fail "a failed backup-file query refuses the save" r007_store_save
reset_device
STUB_APP_PROC=present expect_fail "the store is not saved while the app runs" r007_store_save
reset_device
expect_ok "an encrypted value is tampered" r007_store_tamper
value="AT6IpST5vIhIq0Lm8Tz4Kx=="; mid=$(( ${#value} / 2 ))
store_is "${STORE_XML/"$value"/"${value:0:mid}A${value:mid+1}"}" \
  && ok "only the middle character of the first encrypted value changes" \
  || bad "unexpected tampered store: $(cat "$STUB_DEVICE/$STORE")"
reset_device
printf '%s\n' "<map>" \
  '    <string name="__androidx_security_crypto_encrypted_prefs_key_keyset__">12a9017c89dd</string>' "</map>" \
  > "$STUB_DEVICE/$STORE"
expect_fail "a store with keysets only is not tampered" r007_store_tamper
reset_device
STUB_APP_PROC=present expect_fail "the store is not tampered while the app runs" r007_store_tamper
reset_device
( source "$HERE/lib_r007.sh"; h="$(r007_store_save)" && r007_store_tamper >/dev/null && r007_store_restore "$h" ) \
  > "$WORK/out" 2>&1 && ok "the saved store is restored after a tamper" || bad "the restore after a tamper failed"
store_is "$STORE_XML" && ok "the restored store equals the original" || bad "the restored store differs"
reset_device
( source "$HERE/lib_r007.sh"; h="$(r007_store_save)" && r007_store_tamper >/dev/null \
  && rm "$STUB_DEVICE/files/r007/lockout.orig" && r007_store_restore "$h" ) > "$WORK/out" 2>&1 \
  && bad "a restore without the copy is refused" || ok "a restore without the copy is refused"
[ -s "$STUB_DEVICE/$STORE" ] && ok "a missing copy leaves the store in place" || bad "a missing copy emptied the store"
reset_device
( source "$HERE/lib_r007.sh"; r007_store_save >/dev/null && r007_store_restore "$(printf '0%.0s' {1..64})" ) \
  > "$WORK/out" 2>&1 && bad "a restore to another hash fails" || ok "a restore to another hash fails"
reset_device
( source "$HERE/lib_r007.sh"; R007_STORE_SAVED="$(r007_store_save)" || exit 9; trap r007_restore_pending EXIT
  r007_store_tamper >/dev/null; exit 3 ) > "$WORK/out" 2>&1
store_is "$STORE_XML" && ok "an early exit restores the saved store" || bad "an early exit left the tampered store"
reset_device
( source "$HERE/lib_r007.sh"; R007_STORE_SAVED="$(r007_store_save)" || exit 9; r007_store_tamper >/dev/null
  export STUB_COPY_FAIL=1; r007_restore_pending; [ -n "$R007_STORE_SAVED" ] ) > "$WORK/out" 2>&1 \
  && ok "a failed restore keeps the restore pending" || bad "a failed restore cleared the pending restore"
reset_device
( source "$HERE/lib_r007.sh"; R007_STORE_SAVED="$(r007_store_save)" || exit 9; r007_store_tamper >/dev/null
  r007_restore_pending && [ -z "$R007_STORE_SAVED" ] ) > "$WORK/out" 2>&1 \
  && ok "a verified restore clears the pending restore" || bad "a verified restore left the restore pending"

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

echo "preconditions"
reset_device
expect_ok "a keyguard that is not showing is unlocked" r007_unlocked
reset_device
STUB_ACTIVITIES="    mKeyguardShowing=true" expect_fail "a showing keyguard is locked" r007_unlocked
reset_device
STUB_ACTIVITIES="" STUB_WINDOW="    isKeyguardShowing=false" \
  expect_ok "the window state is used when the activity state has no keyguard field" r007_unlocked
reset_device
STUB_ACTIVITIES="" STUB_WINDOW="    isKeyguardShowing=true" \
  expect_fail "a showing keyguard in the window state is locked" r007_unlocked
reset_device
STUB_ACTIVITIES="" STUB_WINDOW="" expect_fail "a missing keyguard field is not unlocked" r007_unlocked
reset_device
STUB_DUMPSYS_FAIL=1 expect_fail "a failed keyguard query is not unlocked" r007_unlocked
reset_device
expect_ok "a force-stop with an explicit absent answer stops the app" r007_stop_app
[ "$(cat "$STUB_DEVICE/ops")" = "force-stop" ] && ok "the app is stopped with am force-stop" \
  || bad "unexpected stop sequence: $(tr '\n' ';' < "$STUB_DEVICE/ops")"
reset_device
STUB_STOP_FAIL=1 expect_fail "a failed force-stop is not a stop" r007_stop_app
reset_device
STUB_APP_PROC=present R007_KILL_POLLS=2 expect_fail "a process that stays present is not stopped" r007_stop_app
reset_device
STUB_APP_PROC="" R007_KILL_POLLS=2 expect_fail "an empty answer is not a stop" r007_stop_app
reset_device
STUB_PIDOF_FAIL=1 R007_KILL_POLLS=2 expect_fail "a failed process query is not a stop" r007_stop_app

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
