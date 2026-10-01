#!/usr/bin/env bash
# R-007 phase P2, segment "degraded": degraded enforcement lost on restart (R2.1 to R2.4; the reboot variant of R2.1
# runs in the reboot segment). Sourced by p2_device.sh, which defines the case helpers.

# Prints the wall time at which write INDEX of the case process ended (RETURNED, or THREW for an injected throw).
# Fails when the line is missing.
done_wall() { # index
  local wall
  r007_capture_log || return 1
  wall="$(r007_write_done_wall "$R007_CAPTURE" "$R007_CASE_PID" "$1")"
  [ -n "$wall" ] && printf '%s' "$wall"
}

# Waits until the device wall clock reaches WALL (ms). Fails when the device time cannot be read.
wait_until_wall() { # wall
  local now left
  now="$(r007_device_wall)" || { fail "$R007_CASE_LABEL: the device time could not be read"; return 1; }
  left=$(( $1 - now ))
  [ "$left" -le 0 ] || sleep "$(awk -v ms="$left" 'BEGIN { printf "%.3f", ms / 1000 }')"
}

# R2.1: from Z, a wrong PIN whose write fails arms the 30 s degraded lock. The kill starts KILL_AT seconds after the
# write ended (device clock). The gate opens at once after the restart. SCRIPT is ReturnFalseBeforeCommit, Throw, or
# platform (a read-only preferences directory). The kill time is the device time after the confirmed death
# (R007_KILL_WALL), so the lost enforcement up to the end of the degraded lock is a lower bound; for L the kill
# follows the removal of the grant (about 1 s, at most 10 s). A kill after the end of the lock shows no loss: the
# repeat then fails with the objective "na".
r21() { # caller repeat script kill-at-s
  local caller="$1" repeat="$2" script="$3" at="$4" rules=() gate_in after returned pred objective lost
  [ "$script" = platform ] || rules=("WRITE 0 $script")
  case_start "R2.1 $caller $script kill ${at}s #$repeat" \
    && case_prepare "$caller" 0 0 "${rules[@]}" && case_open "$caller" || return 1
  [ "$script" != platform ] || r007_prefs_readonly || return 1
  r007_submit "$WRONG_PIN"; case_write_done 0 || { [ "$script" != platform ] || r007_prefs_writable; return 1; }
  [ "$script" != platform ] || r007_prefs_writable || return 1
  returned="$(done_wall 0)" || { fail "R2.1: no line for the end of the write"; return 1; }
  gate_in="$(gate_now)"
  wait_until_wall $(( returned + at * 1000 )) || return 1
  case_kill_inspect || return 1
  [ -n "$R007_KILL_WALL" ] || { fail "R2.1: the device time after the kill could not be read"; return 1; }
  lost=$(( returned + 30000 - R007_KILL_WALL ))
  after="$(gate_after_restart "$caller")"
  pred="$(yn '[ "$gate_in" = blocked ] && [ "$after" = open ] && [ "$R007_PAIR" = 0,0 ]')"
  objective="$(r007_residual "$pred")"
  if [ "$lost" -le 0 ]; then
    objective=na
    fail "R2.1 caller $caller repeat $repeat: the kill came $(( -lost )) ms after the end of the degraded lock"
  fi
  r007_mark R2.1 "$caller" "$repeat" "$pred" "$objective" fixture=Z script="$script" \
    kill_after_return_ms=$(( R007_KILL_WALL - returned )) pid="$R007_CASE_PID" \
    gate_in_process="$gate_in" gate_after_restart="$after" lost_enforcement_ms="$lost" pair_after="$R007_PAIR"
}

# R2.2: from Z, F1 holds and then fails, and F2 waits behind it and commits (2,0). The state is degraded until 30 s
# after F1 completes. A kill 10 s later gives count 2 and an open gate.
r22() { # caller repeat
  local caller="$1" repeat="$2" gate_in after pred
  case_start "R2.2 $caller #$repeat" && case_prepare "$caller" 0 0 "WRITE 0 HoldBeforeCommit ReturnFalseBeforeCommit" \
    && case_open "$caller" || return 1
  r007_submit "$WRONG_PIN"; case_write_held 0 || return 1
  r007_submit "$WRONG_PIN"; sleep 1
  r007_release "$R007_CASE_PID" WRITE 0 && case_write_returned 0 && case_write_returned 1 || return 1
  sleep 1; gate_in="$(gate_now)"
  sleep 9
  case_kill_inspect || return 1
  after="$(gate_after_restart "$caller")"
  pred="$(yn '[ "$gate_in" = blocked ] && [ "$R007_PAIR" = 2,0 ] && [ "$after" = open ]')"
  r007_mark R2.2 "$caller" "$repeat" "$pred" "$(r007_residual "$pred")" fixture=Z \
    script="WRITE_0_HoldBeforeCommit_ReturnFalseBeforeCommit" pid="$R007_CASE_PID" \
    gate_in_process="$gate_in" gate_after_restart="$after" pair_after="$R007_PAIR"
}

# R2.3: from (3,0), F4 holds and then fails, and F5 locks and waits behind it. After 40 s the release: F4 fails and
# arms a degraded lock from its completion, and F5 commits a deadline that has passed. After a kill, count 5 and an
# open gate.
r23() { # caller repeat
  local caller="$1" repeat="$2" gate_f5 gate_in after begin5 returned5 pred
  case_start "R2.3 $caller #$repeat" && case_prepare "$caller" 3 0 "WRITE 0 HoldBeforeCommit ReturnFalseBeforeCommit" \
    && case_open "$caller" || return 1
  r007_submit "$WRONG_PIN"; case_write_held 0 || return 1
  r007_submit "$WRONG_PIN"; sleep 1; gate_f5="$(gate_now)"
  sleep 38
  r007_release "$R007_CASE_PID" WRITE 0 && case_write_returned 0 && case_write_returned 1 || return 1
  sleep 1; gate_in="$(gate_now)"
  r007_capture_log || { fail "R2.3: logcat could not be read"; return 1; }
  begin5="$(r007_begin_pair "$R007_CAPTURE" "$R007_CASE_PID" 1)"
  returned5="$(r007_write_done_wall "$R007_CAPTURE" "$R007_CASE_PID" 1)"
  case_kill_inspect || return 1
  after="$(gate_after_restart "$caller")"
  # F5 committed a deadline that had passed when its write ended.
  pred="$(yn '[ "$gate_f5" = blocked ] && [ "$gate_in" = blocked ] && [ "${R007_PAIR%%,*}" = 5 ] &&
    [ -n "$begin5" ] && [ -n "$returned5" ] && [ "${begin5#*,}" -lt "$returned5" ] && [ "$after" = open ]')"
  r007_mark R2.3 "$caller" "$repeat" "$pred" "$(r007_residual "$pred")" fixture="(3,0)" \
    script="WRITE_0_HoldBeforeCommit_ReturnFalseBeforeCommit" pid="$R007_CASE_PID" \
    gate_after_f5="$gate_f5" gate_after_release="$gate_in" f5_pair="$begin5" f5_returned_wall="${returned5:-none}" \
    gate_after_restart="$after" pair_after="$R007_PAIR"
}

# R2.4: every write fails (SCRIPT). The first process and 20 restart cycles, each with wrong PINs until the gate
# blocks. Each process admits 1 verifier entry, the stored pair stays Z, and the count never passes 1. The detector
# stays granted for L, so the system restarts the process after each kill; the store is inspected only at the end.
r24() { # caller script
  local caller="$1" script="$2" cycle pid entries per="" n submitted killed
  n="$(reps 21)"
  case_start "R2.4 $caller $script" && case_prepare "$caller" 0 0 "WRITE * $script" || return 1
  for (( cycle=1; cycle<=n; cycle++ )); do
    r007_open_gate "$caller" "open incorrect blocked" || return 1
    pid="$(r007_pid)" || { fail "R2.4: no single app process in cycle $cycle"; return 1; }
    for (( submitted=0; submitted<3; submitted++ )); do
      r007_submit "$WRONG_PIN"; [ "$R007_SUBMIT_STATE" != blocked ] || break
    done
    r007_capture_log || { fail "R2.4: logcat could not be read"; return 1; }
    entries="$(r007_v_exact "$R007_CAPTURE" "$pid")"; per+="$entries;"
    printf '## R2.4 cycle caller=%s script=%s process=%s pid=%s v=%s\n' "$caller" "$script" "$cycle" "$pid" "$entries" \
      >> "$R007_LOG_OUT"
    killed="$(r007_kill)" || { fail "R2.4: the kill failed in cycle $cycle: $(r007_plain "$killed")"; return 1; }
    [ "$caller" = S ] || r007_wait_new_process "$pid" || return 1
  done
  r007_stop_app || return 1
  local insp every1='^(1;)+$' pred
  insp="$(r007_inspect_checked)" || { fail "R2.4: the inspection failed: $(r007_plain "$insp")"; return 1; }
  R007_PAIR="$(r007_pair "$insp")"
  pred="$(yn '[[ "$per" =~ $every1 ]] && [ "$R007_PAIR" = 0,0 ]')"
  r007_mark R2.4 "$caller" 1 "$pred" "$(r007_residual "$pred")" fixture=Z \
    script="WRITE_*_$script" processes="$n" v_per_process="$per" v_label=exact pair_after="$R007_PAIR"
}

# R2.4, deadline kills: from Z with every write failing, a wrong PIN arms the 30 s degraded lock; the kill comes 25 s
# (before the deadline) or 35 s (after it) after the write returned. The gate opens after the restart in both. After
# the deadline an open gate is correct, so the objective of that kill is met when the gate opens. The kill time is the
# device time after the confirmed death (R007_KILL_WALL), as in R2.1: a 25 s kill that came after the end of the lock
# shows no loss, so the repeat fails with the objective "na".
r24_deadline() { # caller repeat kill-at-s
  local caller="$1" repeat="$2" at="$3" after returned pred objective
  case_start "R2.4 $caller deadline kill ${at}s #$repeat" \
    && case_prepare "$caller" 0 0 "WRITE * ReturnFalseBeforeCommit" \
    && case_open "$caller" || return 1
  r007_submit "$WRONG_PIN"; case_write_done 0 || return 1
  returned="$(done_wall 0)" || { fail "R2.4: no line for the end of the write"; return 1; }
  wait_until_wall $(( returned + at * 1000 )) || return 1
  case_kill_inspect || return 1
  [ -n "$R007_KILL_WALL" ] || { fail "R2.4: the device time after the kill could not be read"; return 1; }
  after="$(gate_after_restart "$caller")"
  pred="$(yn '[ "$after" = open ] && [ "$R007_PAIR" = 0,0 ]')"
  if [ "$at" -ge 30 ]; then objective="$(yn '[ "$after" = open ]')"
  elif [ "$R007_KILL_WALL" -ge $(( returned + 30000 )) ]; then
    objective=na
    fail "R2.4 caller $caller repeat $repeat: the kill came $(( R007_KILL_WALL - returned - 30000 )) ms after the" \
      "end of the lock"
  else objective="$(r007_residual "$pred")"; fi
  r007_mark R2.4-deadline "$caller" "$repeat" "$pred" "$objective" fixture=Z script="WRITE_*_ReturnFalseBeforeCommit" \
    kill_after_return_ms=$(( R007_KILL_WALL - returned )) pid="$R007_CASE_PID" gate_after_restart="$after" \
    pair_after="$R007_PAIR"
}

segment_run() {
  local caller repeat n10 n3 scripts=(ReturnFalseBeforeCommit Throw)
  n10="$(reps 10)"; n3="$(reps 3)"
  for caller in $R007_CALLERS; do
    for (( repeat=1; repeat<=n10; repeat++ )); do r21 "$caller" "$repeat" "${scripts[$(( (repeat - 1) % 2 ))]}" 5; done
    for (( repeat=1; repeat<=n3; repeat++ )); do
      r21 "$caller" "$repeat" ReturnFalseBeforeCommit 25; r21 "$caller" "$repeat" platform 5
    done
    for (( repeat=1; repeat<=n3; repeat++ )); do r22 "$caller" "$repeat"; done
    for (( repeat=1; repeat<=n3; repeat++ )); do r23 "$caller" "$repeat"; done
    r24 "$caller" ReturnFalseBeforeCommit
    r24 "$caller" Throw
    for (( repeat=1; repeat<=n3; repeat++ )); do
      r24_deadline "$caller" "$repeat" 25; r24_deadline "$caller" "$repeat" 35
    done
  done
}
