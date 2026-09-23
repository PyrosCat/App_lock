#!/usr/bin/env bash
# R-007 test plan phase P1, device part: validates the device harness itself, not the lockout candidates.
#
# Checks, on a disposable debug install (the script creates the PIN on the first run):
#   V0  the build is debuggable, the androidTest APK is installed, the wrapper is present;
#   V1  fixture: a correct PIN resets the store, and a fresh-process inspection confirms count 0;
#   V2  positive control: a healthy wrong-PIN commit survives an abrupt kill (count 1 after restart);
#   V3  a write held before its commit and killed leaves the old durable state (count still 1);
#   V4  a write killed after its commit, before it returns, leaves the new durable state (count 2);
#   V5  a real platform file fault (read-only preferences directory) makes commit() return false, and the
#       durable state keeps the old count (count still 2);
#   V6  every kill gets an explicit "absent" answer for the process, and every inspection has its begin and end
#       markers in logcat and runs in a new process in which the graph did not touch the store;
#   V7  cleanup: a correct PIN resets the store to count 0, and the control directory is removed.
# The script never clears app data, reinstalls, or re-provisions the PIN between an action and its inspection.
#
# Evidence: every case's wrapper and inspector lines go to $R007_LOG_OUT, or by default to
# build/r007-evidence/p1_validate-<serial>-<UTC time>.log. The run stops before V0 when that file cannot be written.
# A wrapper line that reports a fault-script error fails the run.
#
# Usage: scripts/r007/p1_validate.sh [-s SERIAL]   (APP_ID defaults to com.applock, the prodDebug build)
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; source "$HERE/lib_r007.sh"
while getopts "s:" opt; do case "$opt" in s) SERIAL="$OPTARG";; *) exit 2;; esac; done

require_device || exit 2
r007_evidence_init "p1_validate" || { summary "R-007 P1 device harness"; exit 1; }
info "app=$APP_ID test=$TEST_APP_ID boot=$(r007_boot_id)"

inspected_count() { # label expected ; inspects and checks the stored count
  local out count
  out="$(r007_inspect_checked)" || { fail "$1: inspection failed"; return 1; }
  info "$1: $(tr '\n' ' ' <<< "$out")"
  count="$(r007_field "$out" r007_count)"
  if [ "$count" = "$2" ]; then pass "$1: stored count $count"
  else fail "$1: stored count ${count:-none}, expected $2"; fi
}

killed_cleanly() { # label ; kills the app and checks that the process is gone
  local pid
  pid="$(r007_kill)" || { fail "$1: kill failed"; return 1; }
  pass "$1: process $pid ended"
}

step "V0 preconditions"
r007_debuggable || { fail "run-as does not work for $APP_ID (not a debuggable build?)"; summary "P1"; exit 1; }
r007_test_apk_installed \
  || { fail "$TEST_APP_ID is not installed (gradlew installProdDebugAndroidTest)"; summary "P1"; exit 1; }
r007_clear_faults || { summary "P1"; exit 1; }
screen_stayon
r007_clear_log "before-V0" || { summary "P1"; exit 1; }
home; sleep 1; r007_launch_main; sleep 2; dismiss_anr
if ui_has "$UI_PIN_SETUP_SIGNAL"; then
  _screen_wh; enter_pin "$PIN"; sleep 1; enter_pin "$PIN"; sleep 2   # first run: create and confirm the PIN
  info "PIN created ($PIN)"
  home; sleep 1; r007_launch_main; sleep 2; dismiss_anr
fi
ui_has "$UI_PIN_SIGNAL" || { fail "the self-gate PIN prompt did not show"; summary "P1"; exit 1; }
_screen_wh
created=""
for (( i=0; i<20; i++ )); do
  if r007_logged "op=INIT phase=CREATED"; then created=1; break; fi
  sleep 0.25
done
[ -n "$created" ] \
  || { fail "the fault wrapper did not log its creation (release build installed?)"; summary "P1"; exit 1; }
pass "the debug fault wrapper is present"

step "V1 fixture: correct PIN, then a fresh-process inspection"
r007_enter_right_pin
r007_wait_phase WRITE 0 RETURNED >/dev/null || fail "V1: the reset write did not return"
killed_cleanly "V1"
inspected_count "V1" 0

step "V2 positive control: a healthy commit survives an abrupt kill"
r007_clear_log "V0-V1"
r007_open_self_gate && r007_enter_wrong_pin
r007_wait_phase WRITE 0 RETURNED >/dev/null || fail "V2: the failure write did not return"
killed_cleanly "V2"
inspected_count "V2" 1

step "V3 a write held before its commit dies with the process"
r007_set_faults "WRITE 0 HoldBeforeCommit Normal"
r007_clear_log "V2"
r007_open_self_gate && r007_enter_wrong_pin
if held_pid="$(r007_wait_phase WRITE 0 HELD)"; then info "V3: write held in process $held_pid"
else fail "V3: the write did not hold"; fi
killed_cleanly "V3"
inspected_count "V3" 1

step "V4 a write killed after its commit and before it returns is durable"
r007_set_faults "WRITE 0 CommitThenHold"
r007_clear_log "V3"
r007_open_self_gate && r007_enter_wrong_pin
r007_wait_phase WRITE 0 HELD >/dev/null || fail "V4: the write did not hold after its commit"
r007_logged "op=WRITE index=0 phase=REAL_RESULT script=CommitThenHold result=true" \
  || fail "V4: the real commit did not report true"
killed_cleanly "V4"
inspected_count "V4" 2

step "V5 real platform fault: read-only preferences directory"
r007_clear_faults
r007_clear_log "V4"
r007_open_self_gate
r007_run_as chmod 500 shared_prefs || fail "V5: the preferences directory could not be made read-only"
r007_enter_wrong_pin
if r007_wait_phase WRITE 0 REAL_RESULT >/dev/null \
  && r007_logged "op=WRITE index=0 phase=REAL_RESULT script=Normal result=false"; then
  pass "V5: commit() returned false on the read-only directory"
else
  fail "V5: commit() did not return false: $(r007_log | grep -E 'op=WRITE index=0' | tail -2 | tr '\n' ' ')"
fi
r007_run_as chmod 771 shared_prefs || fail "V5: the preferences directory permissions could not be restored"
killed_cleanly "V5"
inspected_count "V5" 2

step "V7 cleanup: reset the store and remove the control directory"
# The V5 failure armed an in-memory degraded lockout. The V5 kill erased it (R-007 residual /2), so the new
# process accepts a PIN at once.
r007_clear_faults
r007_clear_log "V5"
r007_open_self_gate && r007_enter_right_pin
r007_wait_phase WRITE 0 RETURNED >/dev/null || fail "V7: the reset write did not return"
killed_cleanly "V7"
inspected_count "V7" 0
r007_save_log "V7"
r007_remove_control   # the last inspection stopped the app, so no process reads the control directory now

info "evidence: $R007_LOG_OUT"
summary "R-007 P1 device harness"
