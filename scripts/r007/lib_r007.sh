#!/usr/bin/env bash
# R-007 device harness controller (test plan phase P1): shared library.
# Sourced by the scripts/r007 checks, on top of scripts/e2e/lib.sh (adb plumbing, PIN entry, APP_ID, SERIAL).
#
# It controls the debug fault wrapper (FaultInjectingLockoutStorage) through files in the app's private directory,
# written with `run-as`; reads the wrapper's evidence from logcat (tag R007Fault); kills the app with SIGKILL through
# `run-as`, or through `su 0` after the SELinux denial of that kill on Android 11; and runs the fresh-process inspector
# (LockoutStoreInspector) with `am instrument`. For the unknown-state case it also saves, tampers, and restores the
# lockout store file. It records, changes, and restores the stay-awake and rotation settings of the device, and it
# records and restores the accessibility settings that the phase P2 library (lib_p2.sh) changes.
#
# Every check captures a command's output first and fails when the command fails. It then matches the captured
# text, never a live pipe: lib.sh sets pipefail, so `producer | grep -q` can report a match as a failure when grep
# exits early and the producer gets SIGPIPE.
#
# It needs a debuggable build (run-as) and the matching androidTest APK. It writes the app's private files, so use
# it only on a disposable install, never on a personal one.

R007_HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$R007_HERE/../e2e/lib.sh"

: "${TEST_APP_ID:=${APP_ID}.test}"
: "${R007_RUNNER:=androidx.test.runner.AndroidJUnitRunner}"
: "${WRONG_PIN:=0000}"
: "${R007_KILL_POLLS:=40}"   # r007_kill polls /proc this many times, 0.25 s apart
: "${R007_DENIAL_POLLS:=8}"  # r007_kill_denial reads the events log this many times, 0.25 s apart
R007_CONTROL="files/r007"
R007_INSPECTOR="com.applock.r007.LockoutStoreInspector"
R007_STORE="shared_prefs/applock_lockout.xml"   # the lockout store file, relative to the app's data directory
R007_STORE_COPY="$R007_CONTROL/lockout.orig"      # the saved copy that r007_store_restore writes back

# The script names that DeviceFaultScript accepts. FaultScriptHostListTest checks these lists against the enums.
R007_READ_SCRIPTS="Normal Throw HoldThenRead HoldThenThrow ReadThenHold"
R007_WRITE_SCRIPTS="Normal ReturnFalseBeforeCommit Throw HoldBeforeCommit CommitThenHold CommitThenReportFalse"
R007_HOLD_CONTINUATIONS="Normal ReturnFalseBeforeCommit Throw CommitThenReportFalse"

r007_run_as() { sh_ run-as "$APP_ID" "$@"; }

# True when the extended regex PATTERN matches a line of TEXT. It reads a here-string, so no producer can fail.
r007_has() { grep -qE -- "$2" <<< "$1"; }

# True when WORD is one of the space-separated words in LIST.
r007_in_list() { case " $2 " in *" $1 "*) return 0;; *) return 1;; esac; }

# True when run-as works, that is, the installed app is debuggable.
r007_debuggable() {
  local out
  out="$(r007_run_as id)" || return 1
  r007_has "$out" 'uid='
}

r007_test_apk_installed() {
  local out
  out="$(sh_ pm list packages "$TEST_APP_ID")" || return 1
  r007_has "$out" "^package:${TEST_APP_ID//./\\.}$"
}

# ---- evidence ----------------------------------------------------------------------------------
# Opens the evidence file of this run: $R007_LOG_OUT when it is set, otherwise
# build/r007-evidence/<run>-<serial>-<UTC time>.log in the repository. Fails when the file cannot be written. Writes
# a header with the host revision, the device, and the hashes of the installed APKs.
r007_evidence_init() { # run-name
  local root rev changed
  root="$(cd "$R007_HERE/../.." && pwd)"
  : "${R007_LOG_OUT:=$root/build/r007-evidence/$1-${SERIAL}-$(date -u +%Y%m%dT%H%M%SZ).log}"
  if ! mkdir -p "$(dirname "$R007_LOG_OUT")" || ! : >> "$R007_LOG_OUT" || [ ! -w "$R007_LOG_OUT" ]; then
    fail "the evidence file cannot be written: $R007_LOG_OUT"; return 1
  fi
  # git runs inside the repository, with no path argument: lib.sh sets MSYS_NO_PATHCONV=1, so a Git Bash path such
  # as /c/... would reach a native git unconverted. A failed read is recorded as "unknown", never as a clean tree.
  if rev="$(cd "$root" && git rev-parse HEAD)" && changed="$(cd "$root" && git status --porcelain)"; then
    changed="$(grep -c . <<< "$changed")"
  else
    rev="unknown"; changed="unknown"; info "warning: the host revision could not be read"
  fi
  {
    printf '# R-007 evidence: %s\n' "$1"
    printf '# utc=%s host_rev=%s host_changed_files=%s\n' "$(date -u +%FT%TZ)" "$rev" "$changed"
    printf '# serial=%s model=%s sdk=%s boot_id=%s\n' "$SERIAL" "$(sh_ getprop ro.product.model)" \
      "$(sh_ getprop ro.build.version.sdk)" "$(r007_boot_id)"
    printf '# app=%s apk_sha256=%s\n' "$APP_ID" "$(r007_apk_sha256 "$APP_ID")"
    printf '# test=%s apk_sha256=%s\n' "$TEST_APP_ID" "$(r007_apk_sha256 "$TEST_APP_ID")"
  } >> "$R007_LOG_OUT" || { fail "the evidence header could not be written"; return 1; }
  info "evidence: $R007_LOG_OUT"
}

# The SHA-256 of the installed base APK of PACKAGE, or "unknown".
r007_apk_sha256() { # package
  local path hash
  path="$(sh_ pm path "$1")" || { printf 'unknown'; return 0; }
  path="$(sed -n 's/^package://p' <<< "$path" | head -1)"
  hash="$(sh_ sha256sum "$path")" || { printf 'unknown'; return 0; }
  printf '%s' "${hash%% *}"
}

# Runs `adb logcat` with ARGS. For a device that is not connected, `adb logcat` waits until the device connects, so a
# run that loses its device would block there. This function fails at once instead.
r007_logcat() { adbx get-state >/dev/null 2>&1 || return 1; adbx logcat "$@"; }

# Reads the wrapper, inspector, and fixture-writer lines, with epoch timestamps, into R007_CAPTURE. Returns 1 when
# logcat cannot be read, so a failed read is never taken for an empty log.
r007_capture_log() {
  local out
  out="$(r007_logcat -d -v epoch -s R007Fault:I R007Inspect:I R007Fixture:I)" || { R007_CAPTURE=""; return 1; }
  R007_CAPTURE="$(tr -d '\r' <<< "$out" | sed '/^-----/d')"
}

# Prints the captured lines. Returns 1 when logcat cannot be read.
r007_log() { r007_capture_log && printf '%s\n' "$R007_CAPTURE"; }

# True when a line of the current log matches PATTERN. False when logcat cannot be read, so it suits checks that
# require a line; a check that requires the absence of a line must capture and test the capture itself.
r007_logged() { r007_capture_log && r007_has "$R007_CAPTURE" "$1"; }

# Appends the current lines to the evidence file under a case marker. A failed capture fails the case, and so does a
# wrapper line that reports a fault-script error: a run with an unreadable script has no valid fault evidence.
r007_save_log() { # case-label
  [ -n "${R007_LOG_OUT:-}" ] || { fail "no evidence file (r007_evidence_init was not called)"; return 1; }
  r007_capture_log || { fail "$1: logcat could not be read; the case evidence is lost"; return 1; }
  printf '## %s\n%s\n' "$1" "$R007_CAPTURE" >> "$R007_LOG_OUT" || { fail "$1: the evidence was not saved"; return 1; }
  if r007_has "$R007_CAPTURE" 'op=SCRIPT phase=ERROR'; then
    fail "$1: the wrapper rejected the fault script"; return 1
  fi
}

# Saves the lines of the case that ends, then clears logcat for the case that starts.
r007_clear_log() { # label-of-the-case-that-ends
  r007_save_log "${1:-unlabelled}" || return 1
  r007_logcat -c || { fail "logcat could not be cleared"; return 1; }
}

# Waits until the wrapper logs PHASE for OP#INDEX (optionally in process PID). Prints the pid of that line.
r007_wait_phase() { # op index phase [timeout-s=15] [pid]
  local op="$1" index="$2" phase="$3" t="${4:-15}" pid="${5:-[0-9]+}" i line
  for (( i=0; i<t*4; i++ )); do
    if r007_capture_log; then
      line="$(grep -E "R007Fault: pid=$pid .*op=$op index=$index phase=$phase( |$)" <<< "$R007_CAPTURE" | tail -1)"
      if [ -n "$line" ]; then sed -E 's/.*R007Fault: pid=([0-9]+).*/\1/' <<< "$line"; return 0; fi
    fi
    sleep 0.25
  done
  return 1
}

# ---- fault script ------------------------------------------------------------------------------
# True when RULE is a rule that DeviceFaultScript accepts: "READ <index|*> <read script>",
# "WRITE <index|*> <write script>", or "WRITE <index|*> HoldBeforeCommit [<continuation>]".
r007_valid_rule() { # rule
  local op index script then extra
  read -r op index script then extra <<< "$1"
  [ -z "${extra:-}" ] || return 1
  [[ "${index:-}" =~ ^([0-9]+|\*)$ ]] || return 1
  case "${op:-}" in
    READ) [ -z "${then:-}" ] && r007_in_list "${script:-}" "$R007_READ_SCRIPTS" ;;
    WRITE)
      r007_in_list "${script:-}" "$R007_WRITE_SCRIPTS" || return 1
      if [ "$script" = HoldBeforeCommit ]; then
        [ -z "${then:-}" ] || r007_in_list "$then" "$R007_HOLD_CONTINUATIONS"
      else
        [ -z "${then:-}" ]
      fi ;;
    *) return 1 ;;
  esac
}

# Prints the content of the app-private FILE (run-as cat). A run-as read can fail once without a known cause, so a
# failed read is tried once more after 1 s. The answer of the first failed read (the exit status and the error output
# of adb and run-as) goes to the console and, as a "## read-retry" line, to the evidence file. When the second read
# also fails, the function prints its answer instead of the content and fails.
r007_read_private() { # file
  local out rc errfile answer try
  errfile="$(mktemp)" || { printf 'no temporary file for the error output of the read'; return 1; }
  for try in 1 2; do
    out="$(adbx shell run-as "$APP_ID" cat "$1" 2>"$errfile" | tr -d '\r')"; rc=$?
    [ "$rc" = 0 ] && { rm -f "$errfile"; printf '%s' "$out"; return 0; }
    answer="status $rc: $(tr -d '\r' < "$errfile" | tr '\n' ' ')${out:+ $(tr '\n' ' ' <<< "$out")}"
    [ "$try" = 2 ] && break
    info "the read of $1 failed ($answer); one more read in 1 s" >&2
    if [ -n "${R007_LOG_OUT:-}" ]; then
      printf '## read-retry file=%s answer=%s\n' "$1" "$answer" >> "$R007_LOG_OUT" \
        || { rm -f "$errfile"; printf '%s' "$answer; the read-retry line could not be saved"; return 1; }
    fi
    sleep 1
  done
  rm -f "$errfile"; printf '%s' "$answer"; return 1
}

# Publishes the fault script atomically. Each argument is one rule; no argument publishes an empty script. The
# script goes to a temporary file first; after its content is verified, a rename replaces the live file, so an
# operation that starts during the update reads the complete old script or the complete new one.
r007_set_faults() { # rule...
  local rule content="" expected actual
  for rule in "$@"; do
    r007_valid_rule "$rule" || { fail "invalid fault rule: '$rule'"; return 1; }
    content+="$rule"$'\n'
  done
  expected="$(printf '%s' "$content")"
  printf '%s' "$content" \
    | adbx exec-in "run-as $APP_ID sh -c 'mkdir -p $R007_CONTROL && cat > $R007_CONTROL/faults.tmp'" \
    || { fail "the fault script could not be written"; return 1; }
  actual="$(r007_read_private "$R007_CONTROL/faults.tmp")" \
    || { fail "the fault script could not be read back ($actual)"; return 1; }
  [ "$actual" = "$expected" ] || { fail "the fault script on the device differs from the rules"; return 1; }
  R007_FAULTS_OWNED=1
  r007_run_as mv -f "$R007_CONTROL/faults.tmp" "$R007_CONTROL/faults" \
    || { fail "the fault script could not be published"; return 1; }
  actual="$(r007_read_private "$R007_CONTROL/faults")" \
    || { fail "the published fault script could not be read ($actual)"; return 1; }
  [ "$actual" = "$expected" ] || { fail "the published fault script differs from the rules"; return 1; }
}

R007_FAULTS_OWNED=""   # set when this run published a fault script

# Removes the fault script, its temporary file, and the release files, and checks that the script is gone. Use it
# while no app process runs: without a script, every storage operation passes through.
r007_faults_remove() {
  r007_run_as rm -rf "$R007_CONTROL/faults" "$R007_CONTROL/faults.tmp" "$R007_CONTROL/release" \
    || { fail "the fault script could not be removed"; return 1; }
  [ "$(r007_file_state "$R007_CONTROL/faults")" = absent ] || { fail "the fault script is not removed"; return 1; }
  R007_FAULTS_OWNED=""
}

# Stops the app and removes the fault script when this run published one, so that no later start of the app meets a
# fault of this run. r007_finish calls it.
r007_faults_restore_owned() {
  [ -n "$R007_FAULTS_OWNED" ] || return 0
  r007_stop_app && r007_faults_remove
}

# Publishes an empty script and removes stale release files. Use while the app can run.
r007_clear_faults() {
  r007_set_faults || return 1
  r007_run_as rm -rf "$R007_CONTROL/release" || { fail "the release files could not be removed"; return 1; }
}

# Removes the whole control directory. Use only while no app process runs, at the end of a run. The directory stays
# while a marker of a pending change exists, because it holds the copies that the undo needs.
r007_remove_control() {
  local pending
  pending="$(r007_pending_list)" \
    || { fail "the pending markers could not be queried, so the control directory stays"; return 1; }
  [ -z "$pending" ] || { fail "the control directory stays, because changes are pending: $pending"; return 1; }
  r007_run_as rm -rf "$R007_CONTROL" || { fail "the control directory could not be removed"; return 1; }
  R007_FAULTS_OWNED=""
}

# Lets the operation that holds at OP#INDEX in process PID continue.
r007_release() { # pid op index
  r007_run_as sh -c "'mkdir -p $R007_CONTROL/release && touch $R007_CONTROL/release/$1_$2_$3'" \
    || { fail "the release file for $1 $2#$3 could not be written"; return 1; }
}

# ---- process -----------------------------------------------------------------------------------
# Prints the pid of the app process. Fails unless pidof succeeds and finds exactly one process.
r007_pid() {
  local out
  out="$(sh_ pidof "$APP_ID")" || return 1
  [[ "$out" =~ ^[0-9]+$ ]] || return 1
  printf '%s' "$out"
}

# Prints the boot id of the device. Fails when the read fails or the answer is not one id.
r007_boot_id() {
  local id
  id="$(sh_ cat /proc/sys/kernel/random/boot_id)" && [[ "$id" =~ ^[A-Za-z0-9-]+$ ]] || return 1
  printf '%s' "$id"
}

# True when the device reports that its keyguard is not showing. It reads `mKeyguardShowing` in the KeyguardController
# part of `dumpsys activity activities` (API 26 and later), otherwise `isKeyguardShowing` in `dumpsys window`. A
# failed query or a missing field counts as locked, so an unknown state never passes.
r007_unlocked() {
  local out
  out="$(sh_ dumpsys activity activities)" || return 1
  if ! r007_has "$out" 'mKeyguardShowing=(true|false)'; then
    out="$(sh_ dumpsys window)" || return 1
  fi
  r007_has "$out" '(mKeyguardShowing|isKeyguardShowing)=false' \
    && ! r007_has "$out" '(mKeyguardShowing|isKeyguardShowing)=true'
}

# True when the device answers explicitly that no app process runs. A failed query or no answer is not an absence.
r007_app_absent() {
  local state
  state="$(sh_ "if pidof $APP_ID >/dev/null; then echo present; else echo absent; fi")" || return 1
  [ "$state" = absent ]
}

# Waits for a confirmed absence of the app process. A failed query or no answer is not an absence.
r007_wait_absent() {
  local i
  for (( i=0; i<R007_KILL_POLLS; i++ )); do
    r007_app_absent && return 0
    sleep 0.25
  done
  return 1
}

# Stops the app with `am force-stop` and waits for a confirmed absence of its process. Use it before a case starts, to
# clean up, or after the abrupt death of a case (r007_quiesce). Never use it instead of r007_kill between an action
# and its inspection: the cases need the abrupt kill. A later launch must be explicit, because a force-stop leaves the
# app in the stopped state.
r007_stop_app() {
  sh_ am force-stop "$APP_ID" >/dev/null || { fail "r007_stop_app: am force-stop failed"; return 1; }
  r007_wait_absent || { fail "r007_stop_app: no confirmed absence of $APP_ID"; return 1; }
}

R007_PROC_STATE=""   # the last /proc answer of r007_wait_dead

# Prints the /proc answer for process PID: "present", "absent", or "query-failed".
r007_proc_state() { # pid
  local out
  out="$(sh_ "if [ -d /proc/$1 ]; then echo present; else echo absent; fi")" || out="query-failed"
  printf '%s' "$out"
}

# Waits until /proc answers that process PID is gone. A failed query or no answer is not an absence. Sets
# R007_PROC_STATE to the last answer.
r007_wait_dead() { # pid
  local i
  R007_PROC_STATE=""
  for (( i=0; i<R007_KILL_POLLS; i++ )); do
    R007_PROC_STATE="$(r007_proc_state "$1")"
    [ "$R007_PROC_STATE" = absent ] && return 0
    sleep 0.25
  done
  return 1
}

# The audit line of the SELinux denial that Android 11 (API 30) logs when run-as sends SIGKILL to the app: the run-as
# domain has no sigkill permission on the app domain. r007_kill sends a kill through `su 0` only after this denial.
R007_KILL_DENIAL='type=1400 audit\([0-9.:]+\): avc: denied \{ sigkill \} for comm="kill" scontext=u:r:runas_app:[^ ]+ '
R007_KILL_DENIAL+='tcontext=u:r:untrusted_app:[^ ]+ tclass=process permissive=0( |$)'

# The audit time of a denial comes from the coarse kernel clock, which lags the clock that `date` reads by less than
# one tick (10 ms at HZ=100). r007_kill_denial accepts a denial up to this many ms before the start of the attempt.
# The pid of the attempt cannot belong to an earlier process in that time: the device would have to start about 32000
# processes first (pid_max 32768).
R007_AUDIT_SLACK_MS=100

# Prints the line of the events log that shows the denial R007_KILL_DENIAL for the kill command with pid SENDER,
# logged at or after the device time START (ms) of the attempt, less R007_AUDIT_SLACK_MS. The log entry of an audit
# line carries the pid of the denied process and the time of the denial. The events buffer keeps old lines, and an
# older line with the same pid belongs to an earlier process with that pid. The read asks logcat only for the lines
# since that boundary (-t), and the time check here still applies. The line can arrive after the command has
# returned, so the log is read up to R007_DENIAL_POLLS times. Fails when no such line arrives, when the log is
# unreadable, or when SENDER or START is not valid.
r007_kill_denial() { # sender start-ms
  local i out line lines boundary since pattern time_re='^ *([0-9]+)\.([0-9]{3}) '
  [[ "$1" =~ ^[0-9]+$ && "$2" =~ ^[0-9]{13}$ ]] || return 1
  pattern="^ *[0-9]+\.[0-9]{3} +$1 +[0-9]+ I auditd *: $R007_KILL_DENIAL"
  boundary=$(( $2 - R007_AUDIT_SLACK_MS )); printf -v since '%d.%03d' $(( boundary / 1000 )) $(( boundary % 1000 ))
  for (( i=0; i<R007_DENIAL_POLLS; i++ )); do
    if out="$(r007_logcat -b events -d -v epoch -t "$since" -s auditd:I)"; then
      lines="$(tr -d '\r' <<< "$out" | grep -E -- "$pattern")"
      while IFS= read -r line; do
        [[ "$line" =~ $time_re ]] && (( BASH_REMATCH[1] * 1000 + 10#${BASH_REMATCH[2]} >= boundary )) \
          && { printf '%s' "$line"; return 0; }
      done <<< "$lines"
    fi
    sleep 0.25
  done
  return 1
}

# Sends SIGKILL to PID through `su 0`, after the denial line DENIAL of its run-as kill (see r007_kill). A root kill is
# not limited to the app's uid, so the root shell checks just before the kill that PID is still the one app process.
# The evidence file gets a "## kill-via-su" line and DENIAL for each such kill. Without an evidence file, no kill is
# sent, and a failed write fails the kill.
r007_kill_su() { # pid denial
  local cmd out rc
  [ -n "${R007_LOG_OUT:-}" ] \
    || { fail "r007_kill: no evidence file (r007_evidence_init was not called), so no kill through su 0"; return 1; }
  cmd="p=\$(pidof $APP_ID); if [ \"\$p\" != $1 ]; then echo \"moved \${p:-none}\""
  cmd+="; elif kill -9 $1; then echo killed; else exit 1; fi"
  out="$(sh_ "su 0 sh -c '$cmd'")"; rc=$?
  if [ "$rc" = 0 ] && [ "${out%% *}" = moved ]; then
    fail "r007_kill: pid $1 is no longer the one app process (pidof: ${out#moved }), so su 0 killed nothing"
    return 1
  fi
  # A kill counts only with both the answer and a successful exit: an answer alone can precede a failure.
  [ "$rc" = 0 ] && [ "$out" = killed ] \
    || { fail "r007_kill: kill -9 $1 through su 0 failed (exit status $rc, answer: ${out:-none})"; return 1; }
  printf '## kill-via-su pid=%s\n%s\n' "$1" "$2" >> "$R007_LOG_OUT" \
    || { fail "r007_kill: pid $1 was killed through su 0, but the kill-via-su line could not be saved"; return 1; }
}

# Kills the app process with SIGKILL, as its own uid, and waits for an explicit "absent" answer for its /proc entry.
# Prints the killed pid. A failed kill, a failed query, or no answer is a failure, never a confirmed death. This is
# an abrupt death: no onDestroy, no flush. It is not `am force-stop`, which also changes later launches.
# On Android 11 (API 30), SELinux denies the run-as kill. After this denial only, the same SIGKILL goes through `su 0`
# (r007_kill_su), which a userdebug image such as the AOSP emulator has. Any other failure of the run-as kill, such
# as a lost connection, a run-as error, or a process that is gone, fails without a kill through su 0.
r007_kill() {
  local pid out sender start denial answer
  pid="$(r007_pid)" || { fail "r007_kill: $APP_ID does not run as exactly one process"; return 1; }
  # The device shell prints its pid and the device time (ms), then replaces itself with run-as, which runs kill in the
  # same process. So the sender is the pid of the kill command, and the audit line of a denial carries this pid. The
  # start time comes before the kill.
  if ! out="$(sh_ "echo sender=\$\$ start=\$(date +%s%3N); exec run-as $APP_ID kill -9 $pid 2>&1")"; then
    answer="$(grep -v '^sender=' <<< "$out" | tr '\n' ' ')"; answer="${answer% }"
    read -r sender start <<< "$(sed -n -E 's/^sender=([0-9]+) start=([0-9]{13})$/\1 \2/p' <<< "$out")"
    if [ -z "$start" ]; then
      fail "r007_kill: kill -9 $pid failed, and the device shell gave no pid and time (answer: ${answer:-none})"
      return 1
    fi
    if ! denial="$(r007_kill_denial "$sender" "$start")"; then
      fail "r007_kill: kill -9 $pid failed (answer: ${answer:-none}), and the events log shows no SELinux denial of it"
      return 1
    fi
    r007_kill_su "$pid" "$denial" || return 1
  fi
  r007_wait_dead "$pid" \
    || { fail "r007_kill: no confirmed absence of pid $pid (last answer: ${R007_PROC_STATE:-none})"; return 1; }
  printf '%s' "$pid"
}

# ---- device settings ---------------------------------------------------------------------------
# A run keeps the screen on and locks the display rotation to 0 (portrait on a phone). The PIN entry taps the keys
# that a UI dump reports, and a rotated PIN pad can hide keys. The original values are recorded on the device before
# any change, so a later run from any host can see a cleanup that an earlier run did not finish. The record also
# holds the two accessibility settings, which the P2 legacy-caller cases change to grant the detector.
R007_SETTINGS="global:stay_on_while_plugged_in system:accelerometer_rotation system:user_rotation"
R007_SETTINGS+=" secure:enabled_accessibility_services secure:accessibility_enabled"
# The values that r007_settings_apply sets and then checks. The accessibility settings are not in this list.
R007_SETTINGS_TARGET="global:stay_on_while_plugged_in=7 system:accelerometer_rotation=0 system:user_rotation=0"
R007_SETTINGS_RECORD="/data/local/tmp/r007_settings.pending"
: "${R007_ROTATION_POLLS:=20}"   # r007_wait_rotation polls the display this many times, 0.25 s apart
: "${R007_WAKE_POLLS:=8}"       # r007_wake_screen polls the power state this many times, 0.25 s apart
R007_SETTINGS_OWNED=""          # set while this run owns the record, so that it restores only its own changes

# True when VALUE is a valid value of SETTING: "null" for an unset setting, otherwise an integer. The list of enabled
# accessibility services is a colon-separated list of component names instead, and it can be an empty string.
r007_setting_value_ok() { # setting value
  local services='^(null|[A-Za-z0-9._/:$]*)$'
  case "$1" in
    secure:enabled_accessibility_services) [[ "$2" =~ $services ]] ;;
    *) [[ "$2" =~ ^(-?[0-9]+|null)$ ]] ;;
  esac
}

# Writes VALUE to SETTING: `settings delete` for "null", otherwise `settings put`. The single quotes reach the device
# shell, so an empty string stays one argument.
r007_setting_write() { # setting value
  if [ "$2" = null ]; then sh_ settings delete "${1%%:*}" "${1#*:}" >/dev/null
  else sh_ settings put "${1%%:*}" "${1#*:}" "'$2'" >/dev/null; fi
}

# Prints the current values, one "namespace:key=value" line each (see r007_setting_value_ok). Fails when a read fails
# or returns anything else.
r007_settings_read() {
  local s v out=""
  for s in $R007_SETTINGS; do
    v="$(sh_ settings get "${s%%:*}" "${s#*:}")" || return 1
    r007_setting_value_ok "$s" "$v" || return 1
    out+="$s=$v"$'\n'
  done
  printf '%s' "$out"
}

# Prints "present" or "absent" for the settings record. Fails when the query fails.
r007_settings_record_state() {
  local state
  state="$(sh_ "if [ -e $R007_SETTINGS_RECORD ]; then echo present; else echo absent; fi")" || return 1
  case "$state" in present|absent) printf '%s' "$state" ;; *) return 1 ;; esac
}

# Prints the rotation of the default display (0 to 3). It reads the `mRotation` field of the DisplayRotation part in
# the display 0 section of `dumpsys window displays` (API 29 and later). Display sizes cannot show the rotation,
# because a display at 180 degrees has its natural size. Fails when the field is missing or holds another value.
r007_display_rotation() {
  local out rot
  out="$(sh_ dumpsys window displays)" || return 1
  rot="$(awk '
    /Display: mDisplayId=/ { display0 = ($0 ~ /Display: mDisplayId=0([^0-9]|$)/); inrotation = 0 }
    display0 && /^ *DisplayRotation *$/ { inrotation = 1; next }
    display0 && inrotation && match($0, /mRotation=[0-9]+/) { print substr($0, RSTART + 10, RLENGTH - 10); exit }
  ' <<< "$out")"
  [[ "$rot" =~ ^[0-3]$ ]] || return 1
  printf '%s' "$rot"
}

# Waits until the default display has ROTATION (0 to 3). Fails when the display does not reach it within
# R007_ROTATION_POLLS polls.
r007_wait_rotation() { # rotation
  local i rot=""
  for (( i=0; i<R007_ROTATION_POLLS; i++ )); do
    rot="$(r007_display_rotation)" || rot="unreadable"
    [ "$rot" = "$1" ] && return 0
    sleep 0.25
  done
  fail "the default display did not reach rotation $1 (last value: $rot)"; return 1
}

# Writes the settings record atomically: a temporary file first, then a rename after its content is verified. The
# record is then absent or complete. restore_settings.sh cannot use a partial record.
r007_settings_write_record() { # content
  local written
  printf '%s' "$1" | adbx exec-in "sh -c 'cat > $R007_SETTINGS_RECORD.tmp'" || return 1
  written="$(sh_ cat "$R007_SETTINGS_RECORD.tmp")" && [ "$written" = "$1" ] || return 1
  sh_ mv -f "$R007_SETTINGS_RECORD.tmp" "$R007_SETTINGS_RECORD" >/dev/null || return 1
  written="$(sh_ cat "$R007_SETTINGS_RECORD")" && [ "$written" = "$1" ]
}

# Ends an apply that changed no setting: removes the record and its temporary file, and reports CAUSE. The run gives
# up its ownership only when the device confirms that the record is gone. Otherwise r007_finish writes the unchanged
# values back and removes the record.
r007_settings_abort() { # cause
  sh_ rm -f "$R007_SETTINGS_RECORD" "$R007_SETTINGS_RECORD.tmp" >/dev/null
  if [ "$(r007_settings_record_state)" = absent ]; then
    R007_SETTINGS_OWNED=""; fail "$1; nothing was changed"
  else
    fail "$1; nothing was changed, but the settings record is not confirmed removed, so it stays for the exit cleanup"
  fi
}

# Records the original settings on the device and in the evidence file, then keeps the screen on and locks the
# rotation to 0. It changes nothing when no evidence file is open, when a record of an earlier run exists, or when
# the capture, the record write, or the evidence write fails. It fails unless the settings read back as set and the
# default display reaches rotation 0.
r007_settings_apply() {
  local state orig s k now
  [ -n "${R007_LOG_OUT:-}" ] \
    || { fail "no evidence file (r007_evidence_init was not called); nothing was changed"; return 1; }
  state="$(r007_settings_record_state)" \
    || { fail "the settings record could not be queried; nothing was changed"; return 1; }
  if [ "$state" = present ]; then
    fail "an earlier run did not restore the device settings; run scripts/r007/restore_settings.sh first"
    return 1
  fi
  orig="$(r007_settings_read)" || { fail "the device settings could not be read; nothing was changed"; return 1; }
  R007_SETTINGS_OWNED=1
  r007_settings_write_record "$orig" \
    || { r007_settings_abort "the settings record could not be written"; return 1; }
  printf '## settings-before\n%s\n' "$orig" >> "$R007_LOG_OUT" \
    || { r007_settings_abort "the original settings could not be saved in the evidence file"; return 1; }
  for s in $R007_SETTINGS_TARGET; do
    k="${s%%=*}"
    sh_ settings put "${k%%:*}" "${k#*:}" "${s#*=}" >/dev/null \
      || { fail "the device settings could not be changed"; return 1; }
  done
  now="$(r007_settings_read)" || { fail "the changed settings could not be read back"; return 1; }
  for s in $R007_SETTINGS_TARGET; do
    grep -qxF -- "$s" <<< "$now" \
      || { fail "the device settings did not take the new values: $(tr '\n' ' ' <<< "$now")"; return 1; }
  done
  r007_wait_rotation 0
}

# Wakes the screen and checks that it is on, that the stay-awake setting is in effect, and that the keyguard is not
# showing. Call it after r007_settings_apply: the stay-awake setting keeps a screen on, but it does not turn on a
# screen that went off before, and the keyguard locks a few seconds after the screen goes off. The wake key turns the
# screen on without unlocking a keyguard.
r007_wake_screen() {
  local out awake="" i
  sh_ input keyevent KEYCODE_WAKEUP >/dev/null || { fail "the wake key could not be sent"; return 1; }
  for (( i=0; i<R007_WAKE_POLLS; i++ )); do
    if out="$(sh_ dumpsys power)" && r007_has "$out" '^ *mWakefulness=Awake$'; then awake=1; break; fi
    sleep 0.25
  done
  [ -n "$awake" ] || { fail "the screen did not turn on after the wake key"; return 1; }
  r007_has "$out" '^ *mStayOn=true$' \
    || { fail "the stay-awake setting has no effect (the device reports no power source)"; return 1; }
  r007_unlocked || { fail "the device locked before the screen was kept on (unlock it and run again)"; return 1; }
}

# True when RECORD holds exactly one valid line for each setting of R007_SETTINGS.
r007_settings_record_ok() { # record
  local line valid=yes
  [ "$(sed 's/=.*//' <<< "$1" | sort | tr '\n' ' ')" = "$(tr ' ' '\n' <<< "$R007_SETTINGS" | sort | tr '\n' ' ')" ] \
    || return 1
  while IFS= read -r line; do
    [[ "$line" =~ ^[a-z]+:[a-z_]+= ]] && r007_setting_value_ok "${line%%=*}" "${line#*=}" || valid=no
  done <<< "$1"
  [ "$valid" = yes ]
}

# Writes the recorded list LIST of enabled accessibility services back. A list that differs from the current one is
# written. An equal list is written only when it names a service of the app: after a force-stop, an identical write
# does not bind that service again, so the list is deleted first and written again. The delete unbinds every listed
# service, so an equal list without a service of the app stays untouched. A list that cannot be read counts as
# different.
r007_services_restore() { # list
  local now
  now="$(sh_ settings get secure enabled_accessibility_services)" || now="unreadable"
  if [ "$now" = "$1" ]; then
    [[ ":$1:" == *":$APP_ID/"* ]] || return 0
    sh_ settings delete secure enabled_accessibility_services >/dev/null || return 1
  fi
  r007_setting_write secure:enabled_accessibility_services "$1"
}

# Writes the recorded settings back, reads them again, and appends the comparison to the evidence file. A "null"
# value is restored with `settings delete`. The record is removed only when every value reads back as recorded and
# the comparison is saved. When the comparison cannot be saved, the record stays, so that
# scripts/r007/restore_settings.sh can produce the comparison again, and the restore fails.
r007_settings_restore() {
  local record line s v now report="" match=yes lost=""
  record="$(sh_ cat "$R007_SETTINGS_RECORD")" || { fail "the settings record could not be read"; return 1; }
  if ! r007_settings_record_ok "$record"; then
    fail "the settings record is not valid, so nothing was restored; set the settings by hand, then remove" \
      "$R007_SETTINGS_RECORD: $(tr '\n' ' ' <<< "$record")"
    return 1
  fi
  # The loop reads the record on descriptor 3, because `adb shell` reads its stdin and would take the other lines.
  while IFS= read -r line <&3; do
    if [ "${line%%=*}" = secure:enabled_accessibility_services ]; then
      r007_services_restore "${line#*=}" || match=no
    else
      r007_setting_write "${line%%=*}" "${line#*=}" || match=no
    fi
  done 3<<< "$record"
  now="$(r007_settings_read)" || { fail "the restored settings could not be read back"; return 1; }
  # The keys hold only letters, colons, and underscores, so a key is a literal sed pattern. An empty string shows
  # as ''.
  while IFS= read -r line; do
    s="${line%%=*}"; v="$(sed -n "s/^$s=//p" <<< "$now")"
    report+="$s recorded=${line#*=} now=$v"$'\n'
    [ "${line#*=}" = "$v" ] || match=no
  done <<< "$record"
  report="$(sed -E "s/(recorded|now)=( |$)/\1=''\2/g" <<< "$report")"$'\n'
  if [ -z "${R007_LOG_OUT:-}" ]; then lost="no evidence file (r007_evidence_init was not called)"
  elif ! printf '## settings-restore\n%smatch=%s\n' "$report" "$match" >> "$R007_LOG_OUT"; then
    lost="the evidence file could not be written"
  fi
  report="${report%$'\n'}"; report="${report//$'\n'/; }"
  [ -z "$lost" ] \
    || fail "the comparison is not saved ($lost); the record stays for scripts/r007/restore_settings.sh: $report"
  if [ "$match" != yes ]; then
    fail "the device settings differ from the record, which stays on the device: $report"
    return 1
  fi
  [ -z "$lost" ] || return 1
  sh_ rm -f "$R007_SETTINGS_RECORD" >/dev/null
  [ "$(r007_settings_record_state)" = absent ] \
    || { fail "the settings are restored, but the record could not be removed"; return 1; }
  pass "the device settings are restored: $report"
}

# Restores the settings when this run owns the record, and does nothing otherwise, so a record that an earlier run
# left stays for scripts/r007/restore_settings.sh. r007_finish calls it.
r007_settings_restore_owned() {
  [ -n "$R007_SETTINGS_OWNED" ] || return 0
  r007_settings_restore && R007_SETTINGS_OWNED=""
}

# ---- pending app-data changes ------------------------------------------------------------------
# A run writes a marker for each change of app data that it must undo: "store" holds the hash of the saved store copy,
# "app_settings" the state of the saved app settings file (lib_p2.sh), and "prefs_mode" marks a read-only preferences
# directory. The markers are in the app's private directory, so they survive a crash of the host. A change does not
# start while its marker exists, r007_remove_control keeps the control directory while any marker exists, and
# scripts/r007/restore_settings.sh undoes the changes that an unfinished run left.
R007_PENDING="$R007_CONTROL/pending"
R007_PENDING_NAMES="store app_settings prefs_mode"

# Prints the SHA-256 of the app-relative file PATH, or "absent" when the device answers that it does not exist.
# Fails when the query fails or gives another answer.
r007_file_state() { # path
  local out
  out="$(r007_run_as sh -c "'if [ -e $1 ]; then sha256sum $1; else echo absent; fi'")" || return 1
  [ "$out" = absent ] && { printf absent; return 0; }
  out="${out%% *}"
  [[ "$out" =~ ^[0-9a-f]{64}$ ]] || return 1
  printf '%s' "$out"
}

# Prints the value of the marker NAME, or "absent". Fails when a query fails.
r007_pending_get() { # name
  local state
  state="$(r007_file_state "$R007_PENDING/$1")" || return 1
  if [ "$state" = absent ]; then printf absent; else r007_run_as cat "$R007_PENDING/$1"; fi
}

# Writes the marker NAME with VALUE and checks it by a read.
r007_pending_set() { # name value
  local actual
  printf '%s' "$2" | adbx exec-in "run-as $APP_ID sh -c 'mkdir -p $R007_PENDING && cat > $R007_PENDING/$1'" \
    || return 1
  actual="$(r007_run_as cat "$R007_PENDING/$1")" && [ "$actual" = "$2" ]
}

# Removes the marker NAME and checks that it is gone.
r007_pending_clear() { # name
  r007_run_as rm -f "$R007_PENDING/$1" || return 1
  [ "$(r007_file_state "$R007_PENDING/$1")" = absent ]
}

# Prints the names of the existing markers, separated by spaces, or nothing. Fails when a query fails.
r007_pending_list() {
  local name state out=""
  for name in $R007_PENDING_NAMES; do
    state="$(r007_file_state "$R007_PENDING/$name")" || return 1
    [ "$state" = absent ] || out+="${out:+ }$name"
  done
  printf '%s' "$out"
}

R007_PREFS_OWNED=""   # set while this run has made the preferences directory read-only

# Makes the preferences directory read-only (mode 500), for a real platform write fault. The marker comes first, so
# a crash never leaves a read-only directory without a marker.
r007_prefs_readonly() {
  r007_pending_set prefs_mode 500 || { fail "the prefs_mode marker could not be written"; return 1; }
  R007_PREFS_OWNED=1
  r007_run_as chmod 500 shared_prefs || { fail "the preferences directory could not be made read-only"; return 1; }
}

# Makes the preferences directory writable again (mode 771) and removes the marker.
r007_prefs_writable() {
  r007_run_as chmod 771 shared_prefs || { fail "the preferences directory could not be made writable"; return 1; }
  r007_pending_clear prefs_mode || { fail "the prefs_mode marker could not be removed"; return 1; }
  R007_PREFS_OWNED=""
}

# Makes the preferences directory writable when this run made it read-only. r007_finish calls it first, so that the
# other cleanups can write.
r007_prefs_restore_owned() {
  [ -n "$R007_PREFS_OWNED" ] || return 0
  r007_prefs_writable
}

# ---- store file --------------------------------------------------------------------------------
# Prints the SHA-256 of the app-relative file PATH (default: the store). Fails when run-as fails, when the file is
# missing, or when the answer is not one hash, so two failed reads never compare equal.
r007_store_hash() { # [path]
  local out hash
  out="$(r007_run_as sha256sum "${1:-$R007_STORE}")" || return 1
  hash="${out%% *}"
  [[ "$hash" =~ ^[0-9a-f]{64}$ ]] || return 1
  printf '%s' "$hash"
}

# Saves a copy of the store in the control directory, writes the "store" marker, and prints the store hash. It refuses
# while an app process runs, while a store copy of an earlier run is pending, and when a backup file of the store
# exists, because SharedPreferences would load that backup instead of the store.
r007_store_save() {
  local hash copy bak pending
  r007_app_absent || { fail "r007_store_save: $APP_ID runs"; return 1; }
  pending="$(r007_pending_get store)" || { fail "r007_store_save: the store marker could not be queried"; return 1; }
  [ "$pending" = absent ] \
    || { fail "r007_store_save: a store copy of an earlier run is pending (run restore_settings.sh)"; return 1; }
  bak="$(r007_run_as sh -c "'if [ -e $R007_STORE.bak ]; then echo present; else echo absent; fi'")" \
    || { fail "r007_store_save: the backup-file query failed"; return 1; }
  [ "$bak" = absent ] || { fail "r007_store_save: a backup file of the store exists (answer: ${bak:-none})"; return 1; }
  hash="$(r007_store_hash)" || { fail "r007_store_save: the store could not be hashed"; return 1; }
  r007_run_as sh -c "'mkdir -p $R007_CONTROL && cat $R007_STORE > $R007_STORE_COPY'" \
    || { fail "r007_store_save: the copy could not be written"; return 1; }
  copy="$(r007_store_hash "$R007_STORE_COPY")" || { fail "r007_store_save: the copy could not be hashed"; return 1; }
  [ "$copy" = "$hash" ] || { fail "r007_store_save: the copy differs from the store"; return 1; }
  r007_pending_set store "$hash" || { fail "r007_store_save: the store marker could not be written"; return 1; }
  printf '%s' "$hash"
}

# Changes one character in the middle of the first encrypted value of the store to another Base64 character. The XML
# and the Base64 stay valid, so a read fails authentication, not parsing (a file that does not parse reads as an empty
# store). Prints the new store hash. Use it only while no app process runs, after r007_store_save.
r007_store_tamper() {
  local content line value i new out="" done="" expected actual
  local re='<string name="([^"]+)">([A-Za-z0-9+/]+=*)</string>'
  r007_app_absent || { fail "r007_store_tamper: $APP_ID runs"; return 1; }
  content="$(r007_run_as cat "$R007_STORE")" || { fail "r007_store_tamper: the store could not be read"; return 1; }
  while IFS= read -r line; do
    if [ -z "$done" ] && [[ "$line" =~ $re ]] \
      && [[ "${BASH_REMATCH[1]}" != __androidx_security_crypto_encrypted_prefs_* ]]; then
      value="${BASH_REMATCH[2]}"; i=$(( ${#value} / 2 ))
      new=A; [ "${value:i:1}" = A ] && new=B
      line="${line/"$value"/"${value:0:i}$new${value:i+1}"}"
      done=1
    fi
    out+="$line"$'\n'
  done <<< "$content"
  [ -n "$done" ] || { fail "r007_store_tamper: the store has no encrypted value"; return 1; }
  printf '%s' "$out" | adbx exec-in "run-as $APP_ID sh -c 'cat > $R007_STORE'" \
    || { fail "r007_store_tamper: the tampered store could not be written"; return 1; }
  expected="$(printf '%s' "$out" | sha256sum)"; expected="${expected%% *}"
  actual="$(r007_store_hash)" || { fail "r007_store_tamper: the tampered store could not be hashed"; return 1; }
  [ "$actual" = "$expected" ] \
    || { fail "r007_store_tamper: the store on the device differs from the tampered text"; return 1; }
  printf '%s' "$actual"
}

# Stops the app, writes the saved copy back over the store, checks that the store hash equals HASH, and removes the
# "store" marker. The write runs only when the copy exists, so a missing copy never empties the store.
r007_store_restore() { # hash
  local actual
  r007_stop_app || return 1
  r007_run_as sh -c "'[ -f $R007_STORE_COPY ] && cat $R007_STORE_COPY > $R007_STORE'" \
    || { fail "r007_store_restore: the copy could not be written back"; return 1; }
  actual="$(r007_store_hash)" || { fail "r007_store_restore: the restored store could not be hashed"; return 1; }
  [ "$actual" = "$1" ] \
    || { fail "r007_store_restore: the store hash $actual differs from the saved hash $1"; return 1; }
  r007_pending_clear store || { fail "r007_store_restore: the store marker could not be removed"; return 1; }
}

# The hash of the saved store while a restore is pending. The caller sets it from r007_store_save.
R007_STORE_SAVED=""

# Restores the saved store once, when a restore is pending. A failed restore keeps the copy in the control directory
# and the pending hash, so the caller must not remove the control directory, and r007_finish tries the restore again.
r007_restore_pending() {
  [ -n "$R007_STORE_SAVED" ] || return 0
  if r007_store_restore "$R007_STORE_SAVED"; then
    R007_STORE_SAVED=""; pass "the saved store file is restored"
  else
    info "the store copy stays in $R007_STORE_COPY"; return 1   # r007_store_restore reported the failure
  fi
}

# ---- run end -----------------------------------------------------------------------------------
R007_RUN_STOPPED=""   # set by r007_stop_run

# Stops the run after a failure that the caller has counted. r007_finish then does the cleanup.
r007_stop_run() { R007_RUN_STOPPED=1; exit 1; }

# Makes r007_finish the EXIT trap. INT, TERM, and HUP become exits with status 128 plus the signal number. Without
# this, r007_finish can see the status of the command before the signal, often 0.
r007_trap_finish() { # summary-label
  trap "r007_finish $(printf '%q' "$1")" EXIT
  trap 'exit 130' INT
  trap 'exit 143' TERM
  trap 'exit 129' HUP
}

# The EXIT trap of a run (see r007_trap_finish). The handler first captures the exit status and disables the EXIT
# trap. It reports a nonzero exit that no counted failure explains, such as an unexpected stop or a signal. Then it
# attempts each pending cleanup in this order, also when an earlier one fails:
#   1. a read-only preferences directory of this run;
#   2. a fault script of this run;
#   3. the cleanup of the caller (r007_finish_extra, when the caller defines it);
#   4. the saved store;
#   5. the device settings.
# Last, the handler prints the evidence location and the only summary. A nonzero exit status stays. Otherwise the
# status is 1 after any recorded or cleanup failure.
r007_finish() { # summary-label
  local rc=$? status=0
  trap - EXIT
  if [ "$rc" -ne 0 ] && { [ -z "$R007_RUN_STOPPED" ] || [ "$FAIL_COUNT" -eq 0 ]; }; then
    fail "the run stopped with exit status $rc, and no counted failure explains it"
  fi
  r007_prefs_restore_owned || status=1
  r007_faults_restore_owned || status=1
  if declare -F r007_finish_extra >/dev/null; then r007_finish_extra || status=1; fi
  r007_restore_pending || status=1
  r007_settings_restore_owned || status=1
  [ "$FAIL_COUNT" -eq 0 ] || status=1
  [ "$rc" -eq 0 ] || status=$rc
  info "evidence: ${R007_LOG_OUT:-none}"
  summary "$1"
  exit "$status"
}

# ---- inspector ---------------------------------------------------------------------------------
# Prints the r007_* status fields of the raw `am instrument -r` output as key=value lines. Fails unless the run
# completed.
r007_instrument_fields() { # raw
  r007_has "$1" '^INSTRUMENTATION_CODE: -1$' || return 1
  sed -n -E 's/^INSTRUMENTATION_STATUS: (r007_[a-z_]+)=(.*)$/\1=\2/p' <<< "$1"
}

# Runs the fresh-process inspector (`am instrument` stops the app first). Prints the r007_* status fields as
# key=value lines. Fails when am instrument fails or does not report a completed run.
r007_inspect() {
  local raw
  raw="$(sh_ am instrument -w -r -e r007 inspect -e class "$R007_INSPECTOR" "$TEST_APP_ID/$R007_RUNNER")" || return 1
  r007_instrument_fields "$raw"
}

r007_field() { # inspection-output field
  sed -n "s/^$2=//p" <<< "$1" | head -1
}

# Makes sure that no app process can change the store before an inspection. A running app process that has logged no
# wrapper line has not built the lockout store, so it is stopped: the system starts the sticky ProtectionWatchdogService
# in a new process about 1 s after a kill (probe of 2026-09-30). A running process with a wrapper line fails the
# check, because its load can have changed the store file. The stop goes to the evidence file.
r007_quiesce() {
  local pid
  r007_app_absent && return 0
  pid="$(r007_pid)" || { fail "the store is not quiescent: $APP_ID runs, but not as one process"; return 1; }
  r007_capture_log \
    || { fail "the store is not quiescent: the log of the running process $pid is unreadable"; return 1; }
  ! r007_has "$R007_CAPTURE" "R007Fault: pid=$pid " \
    || { fail "the store is not quiescent: the running app process $pid has used the store"; return 1; }
  r007_stop_app || return 1
  r007_capture_log \
    || { fail "the store is not quiescent: the log of the stopped app process $pid is unreadable"; return 1; }
  ! r007_has "$R007_CAPTURE" "R007Fault: pid=$pid " \
    || { fail "the store is not quiescent: the app process $pid used the store before its stop"; return 1; }
  printf '## quiesce stopped_pid=%s store_lines=0\n' "$pid" >> "${R007_LOG_OUT:-/dev/null}"
}

# Runs the inspector and checks its evidence. The store must be quiescent (see r007_quiesce), so only the inspection
# can change the store file between the two hashes. It requires an unchanged store file and the evidence that
# r007_inspect_evidence checks. It fails when the store cannot be hashed. Prints the inspection.
r007_inspect_checked() {
  local out before after
  r007_quiesce || return 1
  before="$(r007_store_hash)" || { fail "the store could not be hashed before the inspection"; return 1; }
  out="$(r007_inspect)" || { fail "the inspector did not complete (is $TEST_APP_ID installed?)"; return 1; }
  after="$(r007_store_hash)" || { fail "the store could not be hashed after the inspection"; return 1; }
  [ "$after" = "$before" ] || { fail "the inspection changed the store file"; return 1; }
  r007_inspect_evidence "$out"
}

# Checks the evidence of the inspection OUT: a completed inspection with a process id, the inspector's begin and end
# markers for that process in logcat, and an end marker that repeats the reported count. It fails when logcat cannot
# be read, or when the wrapper logged any storage operation in the inspector process. Prints the inspection.
r007_inspect_evidence() { # inspection
  local out="$1" pid count
  pid="$(r007_field "$out" r007_pid)"
  [[ "$pid" =~ ^[0-9]+$ ]] || { fail "the inspector reported no process id"; return 1; }
  r007_capture_log || { fail "logcat could not be read, so the inspection is unverified"; return 1; }
  r007_has "$R007_CAPTURE" "R007Inspect: pid=$pid phase=INSPECT_BEGIN( |$)" \
    || { fail "no INSPECT_BEGIN marker for the inspector process $pid"; return 1; }
  r007_has "$R007_CAPTURE" "R007Inspect: pid=$pid phase=INSPECT_END " \
    || { fail "no INSPECT_END marker for the inspector process $pid"; return 1; }
  count="$(r007_field "$out" r007_count)"
  if [ -n "$count" ] \
    && ! r007_has "$R007_CAPTURE" "R007Inspect: pid=$pid phase=INSPECT_END .*r007_count=$count( |$)"; then
    fail "the INSPECT_END marker does not repeat the reported count $count"; return 1
  fi
  if r007_has "$R007_CAPTURE" "R007Fault: pid=$pid "; then
    fail "the graph touched the store in the inspector process $pid"; return 1
  fi
  printf '%s\n' "$out"
}

# ---- self-gate ---------------------------------------------------------------------------------
# Starts MainActivity by its explicit component. launch_main in lib.sh resolves the LAUNCHER intent, and while the
# WP0 SpikeLauncherActivity also declares LAUNCHER, that resolution opens the system chooser instead.
r007_launch_main() { sh_ am start -W -n "$MAIN_ACTIVITY" >/dev/null; }

# Opens the self-gate (MainActivity) and waits until its PIN prompt shows. The prompt builds the lockout manager.
r007_open_self_gate() {
  home; sleep 1; r007_launch_main; sleep 2; dismiss_anr
  ui_has "$UI_PIN_SIGNAL" || { fail "the self-gate PIN prompt did not show"; return 1; }
  _screen_wh
}

r007_enter_wrong_pin() { enter_pin "$WRONG_PIN"; }
r007_enter_right_pin() { enter_pin "$PIN"; }
