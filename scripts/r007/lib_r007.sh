#!/usr/bin/env bash
# R-007 device harness controller (test plan phase P1): shared library.
# Sourced by the scripts/r007 checks, on top of scripts/e2e/lib.sh (adb plumbing, PIN entry, APP_ID, SERIAL).
#
# It controls the debug fault wrapper (FaultInjectingLockoutStorage) through files in the app's private directory,
# written with `run-as`; reads the wrapper's evidence from logcat (tag R007Fault); kills the app with SIGKILL through
# `run-as`; and runs the fresh-process inspector (LockoutStoreInspector) with `am instrument`.
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
R007_CONTROL="files/r007"
R007_INSPECTOR="com.applock.r007.LockoutStoreInspector"

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

# Reads the wrapper and inspector lines, with epoch timestamps, into R007_CAPTURE. Returns 1 when logcat cannot be
# read, so a failed read is never taken for an empty log.
r007_capture_log() {
  local out
  out="$(adbx logcat -d -v epoch -s R007Fault:I R007Inspect:I)" || { R007_CAPTURE=""; return 1; }
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
  adbx logcat -c || { fail "logcat could not be cleared"; return 1; }
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
  actual="$(r007_run_as cat "$R007_CONTROL/faults.tmp")" \
    || { fail "the fault script could not be read back"; return 1; }
  [ "$actual" = "$expected" ] || { fail "the fault script on the device differs from the rules"; return 1; }
  r007_run_as mv -f "$R007_CONTROL/faults.tmp" "$R007_CONTROL/faults" \
    || { fail "the fault script could not be published"; return 1; }
  actual="$(r007_run_as cat "$R007_CONTROL/faults")" \
    || { fail "the published fault script could not be read"; return 1; }
  [ "$actual" = "$expected" ] || { fail "the published fault script differs from the rules"; return 1; }
}

# Publishes an empty script and removes stale release files. Use while the app can run.
r007_clear_faults() {
  r007_set_faults || return 1
  r007_run_as rm -rf "$R007_CONTROL/release" || { fail "the release files could not be removed"; return 1; }
}

# Removes the whole control directory. Use only while no app process runs, at the end of a run.
r007_remove_control() {
  r007_run_as rm -rf "$R007_CONTROL" || { fail "the control directory could not be removed"; return 1; }
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

r007_boot_id() { sh_ cat /proc/sys/kernel/random/boot_id; }

# Kills the app process with SIGKILL, as its own uid, and waits for an explicit "absent" answer for its /proc entry.
# Prints the killed pid. A failed kill, a failed query, or no answer is a failure, never a confirmed death. This is
# an abrupt death: no onDestroy, no flush. It is not `am force-stop`, which also changes later launches.
r007_kill() {
  local pid i state=""
  pid="$(r007_pid)" || { fail "r007_kill: $APP_ID does not run as exactly one process"; return 1; }
  r007_run_as kill -9 "$pid" || { fail "r007_kill: kill -9 $pid failed"; return 1; }
  for (( i=0; i<R007_KILL_POLLS; i++ )); do
    state="$(sh_ "if [ -d /proc/$pid ]; then echo present; else echo absent; fi")" || state="query-failed"
    if [ "$state" = absent ]; then printf '%s' "$pid"; return 0; fi
    sleep 0.25
  done
  fail "r007_kill: no confirmed absence of pid $pid (last answer: ${state:-none})"; return 1
}

# ---- inspector ---------------------------------------------------------------------------------
# Runs the fresh-process inspector (`am instrument` stops the app first). Prints the r007_* status fields as
# key=value lines. Fails when am instrument fails or does not report a completed run.
r007_inspect() {
  local raw
  raw="$(sh_ am instrument -w -r -e r007 inspect -e class "$R007_INSPECTOR" "$TEST_APP_ID/$R007_RUNNER")" || return 1
  r007_has "$raw" '^INSTRUMENTATION_CODE: -1$' || return 1
  sed -n -E 's/^INSTRUMENTATION_STATUS: (r007_[a-z_]+)=(.*)$/\1=\2/p' <<< "$raw"
}

r007_field() { # inspection-output field
  sed -n "s/^$2=//p" <<< "$1" | head -1
}

# Runs the inspector and checks its evidence. It requires a completed inspection with a process id, the inspector's
# begin and end markers for that process in logcat, and an end marker that repeats the reported count. It fails when
# logcat cannot be read or when the wrapper logged any storage operation in the inspector process. Prints the
# inspection.
r007_inspect_checked() {
  local out pid count
  out="$(r007_inspect)" || { fail "the inspector did not complete (is $TEST_APP_ID installed?)"; return 1; }
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
