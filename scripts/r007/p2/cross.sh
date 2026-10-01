#!/usr/bin/env bash
# R-007 phase P2, segment "cross": the cross-cutting device cases X09, X10, and X16. Sourced by p2_device.sh, which
# defines the case helpers. X10 needs both callers.

# Sets user_rotation to ROTATION and waits until the default display has it. r007_wait_rotation counts its own failure.
rotate() { # rotation
  sh_ settings put system user_rotation "$1" >/dev/null || { fail "user_rotation $1 could not be written"; return 1; }
  r007_wait_rotation "$1"
}

# X09: from Z with the first write held: (a) two taps on the last digit, (b) HOME and back during the hold, (c) a
# rotation during the hold (user_rotation 1, then 0; each must reach the default display). After the release: one
# write for each completed PIN entry. When the display cannot return to rotation 0, the run stops, because the later
# cases need the portrait PIN pad. In (a), `input tap` returns only after the app has handled the tap, and the
# handler checks and clears the PIN, so the second tap starts a new entry. The tap times record this order. The case
# cannot submit one entry twice, so the objective of (a) is "na", and the plan lists the double submit as a gap.
x09() { # caller repeat variant(a|b|c)
  local caller="$1" repeat="$2" variant="$3" writes rotated="" state objective
  case_start "X09$variant $caller #$repeat" \
    && case_prepare "$caller" 0 0 "WRITE 0 HoldBeforeCommit Normal" && case_open "$caller" || return 1
  case "$variant" in
    a) r007_submit_prefix "$WRONG_PIN" || return 1
       r007_tap $R007_LAST_KEY && r007_tap $R007_LAST_KEY || return 1
       r007_count_submission; sleep "$TAP_GAP"
       case_write_held 0 || return 1 ;;
    b) r007_submit "$WRONG_PIN"; case_write_held 0 || return 1
       reopen "$caller" || return 1 ;;
    c) r007_submit "$WRONG_PIN"; case_write_held 0 || return 1
       if ! rotate 1; then rotate 0 || r007_stop_run; return 1; fi
       sleep 2; state="$(gate_now)"
       rotate 0 || r007_stop_run
       rotated="1,0" ;;
  esac
  sleep 2
  r007_release "$R007_CASE_PID" WRITE 0 && case_write_returned 0 || return 1
  sleep 2
  r007_capture_log || { fail "X09: logcat could not be read"; return 1; }
  writes="$(r007_v_exact "$R007_CAPTURE" "$R007_CASE_PID")"
  case_kill_inspect || return 1
  objective="$(yn '[ "$writes" = 1 ] && [ "$R007_PAIR" = 1,0 ]')"; [ "$variant" != a ] || objective=na
  r007_mark "X09$variant" "$caller" "$repeat" "$(yn '[ "$writes" = 1 ] && [ "$R007_PAIR" = 1,0 ]')" "$objective" \
    fixture=Z \
    script="WRITE_0_HoldBeforeCommit_Normal" pid="$R007_CASE_PID" writes="$writes" tap_times="${R007_TAP_TIMES// /;}" \
    rotation_during_hold="${rotated:-none}" gate_after_rotation="${state:-none}" pair_after="$R007_PAIR"
}

# Opens the gate of CALLER in the running app process (the process that the other caller uses).
open_same_process() { # caller
  r007_open_gate "$1" "open incorrect blocked" || return 1
  [ "$(r007_pid)" = "$R007_CASE_PID" ] \
    || { fail "$R007_CASE_LABEL: the second gate runs in another process"; return 1; }
}

# X10: both callers in one app process. The first caller submits a wrong PIN with the write held; the second caller
# submits the correct PIN, which unlocks it and queues the reset. With the release, the writes run in order and the
# store ends at Z; with a kill before the release, both writes are lost. The stored Z after the kill is also the final
# state of the admitted writes, so the kill cannot show a lost change, and its objective is "na".
x10() { # first-caller(S|L) repeat ending(release|kill)
  local first="$1" repeat="$2" ending="$3" second=S unlocked p0="" p1=""
  [ "$first" = S ] && second=L
  case_start "X10 $first-then-$second $ending #$repeat" && case_prepare L 0 0 "WRITE 0 HoldBeforeCommit Normal" \
    && case_open "$first" || return 1
  r007_submit "$WRONG_PIN"; case_write_held 0 || return 1
  open_same_process "$second" || return 1
  r007_submit "$PIN"; sleep 1
  unlocked="$(yn 'unlocked_now "$second"')"
  if [ "$ending" = release ]; then
    r007_release "$R007_CASE_PID" WRITE 0 && case_write_returned 0 && case_write_returned 1 || return 1
    r007_capture_log || { fail "X10: logcat could not be read"; return 1; }
    p0="$(r007_begin_pair "$R007_CAPTURE" "$R007_CASE_PID" 0)"
    p1="$(r007_begin_pair "$R007_CAPTURE" "$R007_CASE_PID" 1)"
  fi
  case_kill_inspect || return 1
  if [ "$ending" = release ]; then
    r007_mark X10 "$first+$second" "$repeat" \
      "$(yn '[ "$unlocked" = yes ] && [ "$p0" = 1,0 ] && [ "$p1" = 0,0 ] && [ "$R007_PAIR" = 0,0 ]')" \
      "$(yn '[ "$R007_PAIR" = 0,0 ]')" fixture=Z script="WRITE_0_HoldBeforeCommit_Normal" ending="$ending" \
      pid="$R007_CASE_PID" unlocked_target="$second:$unlocked" write_pairs="$p0;$p1" pair_after="$R007_PAIR"
  else
    r007_mark X10 "$first+$second" "$repeat" "$(yn '[ "$unlocked" = yes ] && [ "$R007_PAIR" = 0,0 ]')" na fixture=Z \
      script="WRITE_0_HoldBeforeCommit_Normal" ending="$ending" pid="$R007_CASE_PID" \
      unlocked_target="$second:$unlocked" pair_after="$R007_PAIR"
  fi
}

# X16 (S): over (8, now + 10 min), XML that does not parse replaces the store. The inspection may rewrite the file
# (probe A of the 2026-09-25 Moto G report: the read wrote new keysets and no values). Then the gate, a wrong PIN, a
# kill, and an inspection; the saved store comes back at the end. A store copy that an earlier repeat left pending is
# written back first, so that its marker does not block the save and its restore is not lost.
x16() { # repeat
  local repeat="$1" read_result files state begin
  case_start "X16 S #$repeat" && r007_restore_pending && case_prepare S 8 +600000 || return 1
  R007_STORE_SAVED="$(r007_store_save)" || { R007_STORE_SAVED=""; fail "X16: the store could not be saved"; return 1; }
  r007_store_damage || return 1
  r007_inspect_files || return 1
  read_result="$(r007_pair "$R007_INSPECTION")"; files="$R007_FILES"
  case_open S "open incorrect blocked" || return 1
  state="$R007_GATE"
  if [ "$state" != blocked ]; then r007_submit "$WRONG_PIN"; case_write_returned 0 || return 1; fi
  r007_capture_log || { fail "X16: logcat could not be read"; return 1; }
  begin="$(r007_begin_pair "$R007_CAPTURE" "$R007_CASE_PID" 0)"
  case_kill_inspect || return 1
  r007_restore_pending || return 1
  r007_mark X16 S "$repeat" "$(yn '[ "$read_result" = 0,0 ] && [ "$state" = open ] && [ "$R007_PAIR" = 1,0 ]')" \
    "$(yn '[ "$state" = blocked ]')" fixture="(8,now+10min)+damaged_xml" script=none pid="$R007_CASE_PID" \
    inspected_damaged="$read_result" damaged_files="${files// /,}" gate="$state" write_pair="${begin:-none}" \
    pair_after="$R007_PAIR"
}

segment_run() {
  local caller repeat variant n3
  n3="$(reps 3)"
  for caller in $R007_CALLERS; do
    for variant in a b c; do for (( repeat=1; repeat<=n3; repeat++ )); do x09 "$caller" "$repeat" "$variant"; done; done
  done
  if caller_on S && caller_on L; then
    for (( repeat=1; repeat<=n3; repeat++ )); do
      x10 L "$repeat" release; x10 S "$repeat" release; x10 L "$repeat" kill; x10 S "$repeat" kill
    done
  else
    info "X10 needs both callers; it does not run with -c '$R007_CALLERS'"
  fi
  if caller_on S; then for (( repeat=1; repeat<=n3; repeat++ )); do x16 "$repeat"; done; fi
}
