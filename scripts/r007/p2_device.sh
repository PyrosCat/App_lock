#!/usr/bin/env bash
# R-007 test plan phase P2, device lanes: runs one segment of the device cases on a disposable debug install. The
# case matrix, the segments, and the evidence rules are in docs/testing/M7_WP2_F2H_P2_DEVICE_PLAN.md.
#
# Usage: scripts/r007/p2_device.sh [-s SERIAL] [-r MAX_REPEATS] [-c CALLERS] [-k CASES] [-o] SEGMENT
#   SEGMENT   healthy, cold-read, degraded, death, reset, cross, reboot, or biometric
#   -r        caps every repeat count of the plan (the smoke run uses -r 1)
#   -c        the callers, "S L" by default: S is the self-gate, L is the legacy lock screen over Clock
#   -k        runs only these cases of the segment, for example "R1.2a". Only a segment whose file lists its cases in
#             SEGMENT_CASES accepts a case selection.
#   -o        an operator is present. The screen-off steps run only with -o, because on the Moto G the sleep key
#             locks the phone at once and the operator must unlock it. Without -o, a case marker records
#             screen_cycle=skipped. The reboot and biometric segments need -o.
#
# Preflight: the build is debuggable, the androidTest APK is installed, and the device is unlocked. No earlier run left
# app data changed (no marker in files/r007/pending/; see lib_r007.sh), and the device clock prints ms. The run then
# records the device settings (stay-awake, rotation, accessibility) on the device and in the evidence file. It keeps
# the screen on and locks the rotation to 0. It saves the app settings file and sets biometric unlock: off, and on only
# for the biometric segment. It writes the fixture Z. For L, it grants the detector and makes sure that Clock is
# protected.
#
# Each case repeat saves its wrapper, inspector, and fixture-writer lines under its label. It also writes one marker
# line: the case, the caller, the repeat, the fixture, the fault script, the pids, the stored pairs, V with its label,
# and the two verdicts. The evidence file ends with a summary table. A failed case repeat counts as a failure, and so
# does a repeat that is not as predicted. The segment then goes on with the next repeat.
#
# Exit: the exit handler runs these steps in this order, each also after an earlier failure:
#   1. it makes a read-only preferences directory writable again;
#   2. it stops the app and removes the fault script of the run (the stop also ends a detector grant);
#   3. it restores any saved store copy, writes the fixture Z, restores the app settings file, and removes the control
#      directory;
#   4. it restores the device settings.
# A run that the preflight refused changes no app data. After a run that did not finish its cleanup, run
# scripts/r007/restore_settings.sh before the next run. That script restores the device settings and undoes the
# app-data changes that the markers in files/r007/pending/ name.
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; source "$HERE/lib_p2.sh"

R007_MAX_REPEATS=""
R007_CALLERS="S L"
R007_CASES=""        # the selected cases; empty runs every case of the segment
R007_CASES_SET=""    # set by -k, so that an empty case selection is refused
R007_OPERATOR=""
while getopts "s:r:c:k:o" opt; do
  case "$opt" in
    s) SERIAL="$OPTARG" ;;
    r) R007_MAX_REPEATS="$OPTARG" ;;
    c) R007_CALLERS="$OPTARG" ;;
    k) R007_CASES="$OPTARG"; R007_CASES_SET=1 ;;
    o) R007_OPERATOR=1 ;;
    *) exit 2 ;;
  esac
done
shift $((OPTIND - 1))
SEGMENT="${1:-}"
case "$SEGMENT" in
  healthy|cold-read|degraded|death|reset|cross|reboot|biometric) ;;
  *) echo "usage: $0 [-s SERIAL] [-r MAX_REPEATS] [-c CALLERS] [-k CASES] [-o] SEGMENT" >&2; exit 2 ;;
esac
case "$SEGMENT" in
  reboot|biometric) [ -n "$R007_OPERATOR" ] || { echo "the $SEGMENT segment needs an operator (-o)" >&2; exit 2; } ;;
esac
[ -z "$R007_MAX_REPEATS" ] || [[ "$R007_MAX_REPEATS" =~ ^[1-9][0-9]*$ ]] \
  || { echo "-r needs a positive integer" >&2; exit 2; }
# The callers are S, L, or both, each once. An empty selection would run no case and still end with no failure.
callers=""
for caller in $R007_CALLERS; do
  case "$caller" in S|L) ;; *) echo "-c takes S, L, or both" >&2; exit 2 ;; esac
  ! r007_in_list "$caller" "$callers" || { echo "-c names the caller $caller twice" >&2; exit 2; }
  callers+="${callers:+ }$caller"
done
[ -n "$callers" ] || { echo "-c needs at least one caller: S, L, or both" >&2; exit 2; }
R007_CALLERS="$callers"
[ -f "$HERE/p2/${SEGMENT//-/_}.sh" ] || { echo "the segment $SEGMENT has no case file yet" >&2; exit 2; }
# The segment file only defines functions and defaults, so it can load before any device command. A case list from
# the environment must not make a segment without its own list accept a case selection.
SEGMENT_CASES=""
source "$HERE/p2/${SEGMENT//-/_}.sh"
# The case selection names cases of SEGMENT_CASES, each once. A name that the segment does not know would run no case
# and still end with no failure.
if [ -n "$R007_CASES_SET" ]; then
  [ -n "${SEGMENT_CASES:-}" ] || { echo "-k: the segment $SEGMENT has no case selection" >&2; exit 2; }
  cases=""
  for id in $R007_CASES; do
    r007_in_list "$id" "$SEGMENT_CASES" \
      || { echo "-k: the segment $SEGMENT has no case $id (its cases: $SEGMENT_CASES)" >&2; exit 2; }
    ! r007_in_list "$id" "$cases" || { echo "-k names the case $id twice" >&2; exit 2; }
    cases+="${cases:+ }$id"
  done
  [ -n "$cases" ] || { echo "-k needs at least one case" >&2; exit 2; }
  R007_CASES="$cases"
fi

# ---- case helpers ------------------------------------------------------------------------------
R007_CASE_LABEL="preflight"   # the label under which r007_clear_log saves the lines of the running case
R007_PAIR=""                  # the pair of the last inspection
R007_CASE_PID=""              # the app process of the running case

# Prints REQUESTED, or MAX_REPEATS when that is smaller.
reps() { # requested
  if [ -n "$R007_MAX_REPEATS" ] && [ "$1" -gt "$R007_MAX_REPEATS" ]; then printf '%s' "$R007_MAX_REPEATS"
  else printf '%s' "$1"; fi
}

# True when CALLER is one of the selected callers.
caller_on() { r007_in_list "$1" "$R007_CALLERS"; }

# True when the run selects the case CASE: always without a case selection (-k).
case_on() { [ -z "$R007_CASES" ] || r007_in_list "$1" "$R007_CASES"; }

# Prints "yes" when the shell CONDITION is true, otherwise "no". The condition is one string, so that a compound
# condition (a && b) is evaluated as a whole. It sees the local variables of the caller.
yn() { # condition
  if eval "$1"; then printf yes; else printf no; fi
}

# Starts a case repeat: saves the lines of the case that ends under its label, clears logcat, and resets the
# submission counts.
case_start() { # label
  r007_clear_log "$R007_CASE_LABEL" || return 1
  R007_CASE_LABEL="$1"; R007_PAIR=""; R007_CASE_PID=""; R007_SCREEN_CYCLE=""; r007_v_reset
  step "$1"
}

# Prepares a case with no app process: the fixture (a count and a deadline, see r007_fixture), the fault script, and
# no release files. For L, the grant of the detector follows, and the system starts the app process to bind it.
case_prepare() { # caller count deadline [rule...]
  r007_stop_app || return 1
  r007_fixture "$2" "$3" || return 1
  r007_set_faults "${@:4}" || return 1
  r007_run_as rm -rf "$R007_CONTROL/release" || { fail "the release files could not be removed"; return 1; }
  if [ "$1" = L ]; then r007_grant_detector || return 1; fi
}

# Opens the gate of CALLER and sets R007_CASE_PID.
case_open() { # caller [accept]
  r007_open_gate "$@" || return 1
  R007_CASE_PID="$(r007_pid)" || { fail "$R007_CASE_LABEL: no single app process after the gate opened"; return 1; }
}

R007_KILL_WALL=""   # the device time just after the confirmed death of the last case_kill_inspect, or empty

# Kills the app (removing the grant first when the detector is bound) and inspects the store in a new process.
# Sets R007_PAIR, and R007_KILL_WALL from a device time read after the death is confirmed: an upper bound of the
# kill time, also when the removal of the grant took seconds.
case_kill_inspect() {
  local insp
  R007_KILL_WALL=""
  r007_kill_for_inspection || return 1
  R007_KILL_WALL="$(r007_device_wall)" || R007_KILL_WALL=""
  insp="$(r007_inspect_checked)" || { fail "$R007_CASE_LABEL: the inspection failed: $(r007_plain "$insp")"; return 1; }
  R007_PAIR="$(r007_pair "$insp")"
}

# Waits until write INDEX of the case process returns.
case_write_returned() { # index [timeout-s=15]
  r007_wait_phase WRITE "$1" RETURNED "${2:-15}" "$R007_CASE_PID" >/dev/null \
    || { fail "$R007_CASE_LABEL: write $1 of process $R007_CASE_PID did not return"; return 1; }
}

# Waits until write INDEX of the case process ends: RETURNED, or THREW for an injected throw. Use it for the cases
# whose write script can throw.
case_write_done() { # index [timeout-s=15]
  r007_wait_write_done "$R007_CASE_PID" "$1" "${2:-15}" >/dev/null \
    || { fail "$R007_CASE_LABEL: write $1 of process $R007_CASE_PID did not end"; return 1; }
}

# Waits until write INDEX of the case process holds.
case_write_held() { # index [timeout-s=15]
  r007_wait_phase WRITE "$1" HELD "${2:-15}" "$R007_CASE_PID" >/dev/null \
    || { fail "$R007_CASE_LABEL: write $1 of process $R007_CASE_PID did not hold"; return 1; }
}

R007_SCREEN_CYCLE=""   # "done", "skipped", or empty (no screen-off step in the case)

# Turns the screen off for 1.5 s and on again, when an operator is present (-o). The Moto G locks at once on the sleep
# key, so the function then waits for the operator to unlock. Sets R007_SCREEN_CYCLE.
screen_cycle() {
  if [ -z "$R007_OPERATOR" ]; then R007_SCREEN_CYCLE=skipped; return 0; fi
  sh_ input keyevent KEYCODE_SLEEP >/dev/null; sleep 1.5; sh_ input keyevent KEYCODE_WAKEUP >/dev/null; sleep 1
  r007_wait_unlock || { fail "the device stayed locked for $R007_UNLOCK_WAIT s after the screen cycle"; return 1; }
  r007_wake_screen || return 1
  R007_SCREEN_CYCLE=done
}

# Prints the gate state that a UI dump shows now.
gate_now() { r007_gate_state "$(ui_xml)"; }

# Prints the countdown of a blocked gate that a UI dump shows now, in seconds, or nothing.
countdown_now() { r007_countdown_s "$(ui_xml)"; }

# Prints the lockout window of write INDEX of the case process in ms (see r007_write_window). Fails when logcat cannot
# be read or the WRITE BEGIN line is missing.
case_write_window() { # index
  r007_capture_log && r007_write_window "$R007_CAPTURE" "$R007_CASE_PID" "$1"
}

# True when the absolute difference of A and B is at most LIMIT.
within() { # a b limit
  local d=$(( $1 - $2 )); [ "${d#-}" -le "$3" ]
}

# True when the gate of CALLER has let the user through: the app list for S, Clock in the foreground for L.
unlocked_now() { # caller
  if [ "$1" = S ]; then ui_has "$R007_UI_APP_LIST"; else foreground_is "$R007_CLOCK"; fi
}

# Reopens the gate of CALLER after HOME, and checks that the app process stayed.
reopen() { # caller
  r007_open_gate "$1" "open incorrect blocked" || return 1
  [ "$(r007_pid)" = "$R007_CASE_PID" ] || { fail "$R007_CASE_LABEL: the app process changed at the reopen"; return 1; }
}

# Opens the gate of CALLER in a new process after a kill and prints its state ("none" when no gate shows). Stops the
# app afterwards. For L, the detector is granted again first. It runs in a command substitution, so its messages go
# nowhere and the printed state carries the result.
gate_after_restart() { # caller
  local state
  if [ "$1" = L ]; then r007_grant_detector >/dev/null || { printf unknown; return 0; }; fi
  if r007_open_gate "$1" "open incorrect blocked" >/dev/null; then state="$R007_GATE"; else state=none; fi
  r007_stop_app >/dev/null
  printf '%s' "$state"
}

# ---- preflight and exit ------------------------------------------------------------------------
R007_P2_OWNED=""   # set when the preflight starts to change app data; the app-data cleanup runs only then

# The cleanup of this script, which r007_finish runs after a read-only preferences directory is writable again and
# before the device settings. It saves the lines of the last case and the summary. When this run changed app data, it
# also undoes those changes (r007_p2_cleanup). A run that the preflight refused changes nothing, so the copies and
# markers of an earlier run stay for scripts/r007/restore_settings.sh.
r007_finish_extra() {
  local status=0
  r007_clear_log "$R007_CASE_LABEL" || status=1
  R007_CASE_LABEL="cleanup"
  if [ -n "$R007_P2_OWNED" ]; then r007_p2_cleanup || status=1; fi
  r007_save_log "$R007_CASE_LABEL" || status=1
  if [ "${#R007_SUMMARY[@]}" -gt 0 ]; then r007_save_summary || status=1; fi
  return "$status"
}

preflight() {
  local pending
  step "preflight ($SEGMENT, callers: $R007_CALLERS, repeat cap: ${R007_MAX_REPEATS:-none}, cases: ${R007_CASES:-all})"
  r007_debuggable || { fail "run-as does not work for $APP_ID (not a debuggable build?)"; return 1; }
  r007_test_apk_installed || { fail "$TEST_APP_ID is not installed"; return 1; }
  r007_unlocked \
    || { fail "the device is locked, or its keyguard state is unknown (unlock it and run again)"; return 1; }
  pending="$(r007_pending_list)" || { fail "the pending markers could not be queried; nothing was changed"; return 1; }
  [ -z "$pending" ] \
    || { fail "an earlier run left app data changed ($pending); run scripts/r007/restore_settings.sh"; return 1; }
  r007_settings_apply || return 1
  r007_wake_screen || return 1
  # The device times of the cases (tap times, countdown checks, kill times) need `date +%s%3N` in ms.
  r007_device_wall >/dev/null || { fail "the device clock does not print 13-digit ms times (date +%s%3N)"; return 1; }
  r007_detector_init || return 1
  r007_stop_app || return 1
  # The first change of app data follows, so the app-data cleanup of the exit handler starts to apply here.
  R007_P2_OWNED=1
  r007_clear_faults || return 1
  r007_app_settings_save || return 1
  if [ "$SEGMENT" = biometric ]; then r007_app_settings_biometric true || return 1
  else r007_app_settings_biometric false || return 1; fi
  r007_fixture 0 0 || return 1
  r007_sweep_install || return 1
  if caller_on L; then
    r007_resolve_clock || return 1
    r007_grant_detector || return 1
    r007_protect_clock || return 1
    r007_stop_app || return 1
  fi
  local cases="${R007_CASES// /,}"
  printf '## preflight segment=%s callers=%s cases=%s repeat_cap=%s operator=%s clock=%s tap_gap=%s\n' "$SEGMENT" \
    "$R007_CALLERS" "${cases:-all}" "${R007_MAX_REPEATS:-none}" "${R007_OPERATOR:-no}" "${R007_CLOCK:-none}" \
    "$TAP_GAP" >> "$R007_LOG_OUT"
  pass "preflight"
}

require_device || exit 2
r007_evidence_init "p2_${SEGMENT//-/_}" || { summary "R-007 P2 $SEGMENT"; exit 1; }
r007_trap_finish "R-007 P2 $SEGMENT"
preflight || r007_stop_run
segment_run
# A selection of cases and callers that runs no case (for example -k R1.1a -c S) must not end without a failure.
[ "${#R007_SUMMARY[@]}" -gt 0 ] || fail "no case repeat recorded a verdict"
# r007_finish runs the cleanup, prints the summary, and sets the exit status.
exit 0
