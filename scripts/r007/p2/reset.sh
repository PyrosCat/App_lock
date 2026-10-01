#!/usr/bin/env bash
# R-007 phase P2, segment "reset": a failed reset brings stale enforcement back (R4.1 to R4.4; the reboot variant of
# R4.1a runs in the reboot segment). Sourced by p2_device.sh, which defines the case helpers.

# R4.1a: from (4,0), a correct PIN whose clear fails (SCRIPT: ReturnFalseBeforeCommit, Throw, or HoldBeforeCommit
# never released). Kill 5 s later: the stale (4,0) stays, and after the restart the first wrong PIN locks for 30 s.
r41a() { # caller repeat script
  local caller="$1" repeat="$2" script="$3" unlocked window state pred
  case_start "R4.1a $caller $script #$repeat" \
    && case_prepare "$caller" 4 0 "WRITE 0 $script" && case_open "$caller" || return 1
  r007_submit "$PIN"
  if [ "$script" = HoldBeforeCommit ]; then case_write_held 0 || return 1; else case_write_done 0 || return 1; fi
  unlocked="$(yn 'unlocked_now "$caller"')"
  sleep 5
  case_kill_inspect || return 1
  r007_set_faults || return 1
  [ "$caller" = S ] || r007_grant_detector || return 1
  case_open "$caller" || return 1
  r007_submit "$WRONG_PIN"; case_write_returned 0 || return 1
  sleep 1; state="$(gate_now)"
  window="$(case_write_window 0)" || window=unknown
  pred="$(yn '[ "$unlocked" = yes ] && [ "$R007_PAIR" = 4,0 ] && [ "$state" = blocked ] && [ "$window" != unknown ] &&
    within "$window" 30000 1000')"
  r007_mark R4.1a "$caller" "$repeat" "$pred" "$(r007_residual "$pred")" fixture="(4,0)" script="WRITE_0_$script" \
    pid="$R007_CASE_PID" unlocked="$unlocked" stale_pair="$R007_PAIR" \
    gate_after_first_wrong_pin="$state" window_ms="$window"
  r007_stop_app
}

# R4.1b: over (8, now + 10 min) with every read failing, the gate is open; a correct PIN clears memory, and the clear
# fails. The host clears the fault script, kills, and relaunches: the old lock blocks after an accepted success.
r41b() { # caller repeat
  local caller="$1" repeat="$2" gate_before unlocked after countdown pred
  case_start "R4.1b $caller #$repeat" \
    && case_prepare "$caller" 8 +600000 "READ * Throw" "WRITE 0 ReturnFalseBeforeCommit" \
    && case_open "$caller" "open incorrect blocked" || return 1
  gate_before="$R007_GATE"
  r007_submit "$PIN"; case_write_returned 0 || return 1
  unlocked="$(yn 'unlocked_now "$caller"')"
  r007_set_faults || return 1
  case_kill_inspect || return 1
  [ "$caller" = S ] || r007_grant_detector || return 1
  case_open "$caller" "open incorrect blocked" || return 1
  after="$R007_GATE"; countdown="$(countdown_now)"
  r007_stop_app
  pred="$(yn '[ "$gate_before" = open ] && [ "$unlocked" = yes ] && [ "$R007_PAIR" = "$R007_FIXTURE_PAIR" ] &&
    [ "$after" = blocked ]')"
  r007_mark R4.1b "$caller" "$repeat" "$pred" "$(r007_residual "$pred")" fixture="(8,now+10min)" \
    script="READ_*_Throw,WRITE_0_ReturnFalseBeforeCommit" pid="$R007_CASE_PID" \
    gate_before="$gate_before" unlocked="$unlocked" pair_after="$R007_PAIR" gate_after_restart="$after" \
    countdown_after_restart_s="${countdown:-none}"
}

# R4.1c: from (4,0), a correct PIN whose clear fails on a read-only preferences directory (a real platform fault).
r41c() { # caller repeat
  local caller="$1" repeat="$2" result pred
  case_start "R4.1c $caller #$repeat" && case_prepare "$caller" 4 0 && case_open "$caller" || return 1
  r007_prefs_readonly || return 1
  r007_submit "$PIN"; case_write_returned 0 || { r007_prefs_writable; return 1; }
  r007_prefs_writable || return 1
  r007_capture_log || { fail "R4.1c: logcat could not be read"; return 1; }
  result="$(grep -E "R007Fault: pid=$R007_CASE_PID .*op=WRITE index=0 phase=REAL_RESULT" <<< "$R007_CAPTURE" \
    | sed -n -E 's/.* result=([a-z]+).*/\1/p' | tail -1)"
  case_kill_inspect || return 1
  pred="$(yn '[ "$result" = false ] && [ "$R007_PAIR" = 4,0 ]')"
  r007_mark R4.1c "$caller" "$repeat" "$pred" "$(r007_residual "$pred")" fixture="(4,0)" \
    script=platform_readonly_dir pid="$R007_CASE_PID" commit_result="${result:-none}" pair_after="$R007_PAIR"
}

# R4.2: from (4,0), a failed clear and 60 s of observation: exactly 1 write in the process (no retry).
r42() { # caller repeat
  local caller="$1" repeat="$2" writes pred
  case_start "R4.2 $caller #$repeat" \
    && case_prepare "$caller" 4 0 "WRITE 0 ReturnFalseBeforeCommit" && case_open "$caller" || return 1
  r007_submit "$PIN"; case_write_returned 0 || return 1
  sleep 60
  r007_capture_log || { fail "R4.2: logcat could not be read"; return 1; }
  writes="$(r007_v_exact "$R007_CAPTURE" "$R007_CASE_PID")"
  case_kill_inspect || return 1
  pred="$(yn '[ "$writes" = 1 ] && [ "$R007_PAIR" = 4,0 ]')"
  r007_mark R4.2 "$caller" "$repeat" "$pred" "$(r007_residual "$pred")" fixture="(4,0)" \
    script=WRITE_0_ReturnFalseBeforeCommit pid="$R007_CASE_PID" writes_in_60s="$writes" pair_after="$R007_PAIR"
}

# R4.3: from (4,0), a failed clear, then a wrong PIN of the new streak commits (1,0). Variant "held": the clear holds
# and then fails, and the wrong PIN waits behind it.
r43() { # caller repeat variant(plain|held)
  local caller="$1" repeat="$2" variant="$3" rule="WRITE 0 ReturnFalseBeforeCommit" p0 p1
  [ "$variant" = plain ] || rule="WRITE 0 HoldBeforeCommit ReturnFalseBeforeCommit"
  case_start "R4.3 $caller $variant #$repeat" && case_prepare "$caller" 4 0 "$rule" && case_open "$caller" || return 1
  r007_submit "$PIN"
  if [ "$variant" = plain ]; then case_write_returned 0 || return 1; else case_write_held 0 || return 1; fi
  reopen "$caller" || return 1
  r007_submit "$WRONG_PIN"; sleep 1
  [ "$variant" = plain ] || r007_release "$R007_CASE_PID" WRITE 0 || return 1
  case_write_returned 1 || return 1
  r007_capture_log || { fail "R4.3: logcat could not be read"; return 1; }
  p0="$(r007_begin_pair "$R007_CAPTURE" "$R007_CASE_PID" 0)"; p1="$(r007_begin_pair "$R007_CAPTURE" "$R007_CASE_PID" 1)"
  case_kill_inspect || return 1
  r007_mark R4.3 "$caller" "$repeat" "$(yn '[ "$p0" = 0,0 ] && [ "$p1" = 1,0 ] && [ "$R007_PAIR" = 1,0 ]')" na \
    fixture="(4,0)" script="${rule// /_}" variant="$variant" pid="$R007_CASE_PID" write_pairs="$p0;$p1" \
    pair_after="$R007_PAIR"
}

# R4.4: from L5 with every write failing (SCRIPT). The first process waits for the lock and enters the correct PIN;
# 20 restart cycles each enter the correct PIN; a last restart enters a wrong PIN, which locks for 60 s (degraded, from
# the stale count 5). The detector stays granted for L; the store is inspected only at the end.
r44() { # caller script
  local caller="$1" script="$2" cycle pid n waits="" start state window killed pred
  n="$(reps 21)"
  case_start "R4.4 $caller $script" && case_prepare "$caller" 5 +30000 "WRITE * $script" || return 1
  for (( cycle=1; cycle<=n; cycle++ )); do
    r007_open_gate "$caller" "open incorrect blocked" || return 1
    pid="$(r007_pid)" || { fail "R4.4: no single app process in cycle $cycle"; return 1; }
    start=$SECONDS
    until r007_in_list "$(gate_now)" "open incorrect" || [ $(( SECONDS - start )) -gt 60 ]; do sleep 1; done
    waits+="$(( SECONDS - start ));"
    r007_submit "$PIN"
    r007_wait_write_done "$pid" 0 >/dev/null \
      || { fail "R4.4: the clear did not end in cycle $cycle"; return 1; }
    printf '## R4.4 cycle caller=%s script=%s process=%s pid=%s wait_s=%s\n' "$caller" "$script" "$cycle" "$pid" \
      "$(( SECONDS - start ))" >> "$R007_LOG_OUT"
    killed="$(r007_kill)" || { fail "R4.4: the kill failed in cycle $cycle: $(r007_plain "$killed")"; return 1; }
    [ "$caller" = S ] || r007_wait_new_process "$pid" || return 1
  done
  r007_open_gate "$caller" "open incorrect blocked" || return 1
  R007_CASE_PID="$(r007_pid)" || { fail "R4.4: no single app process at the last restart"; return 1; }
  r007_submit "$WRONG_PIN"; case_write_done 0 || return 1
  sleep 1; state="$(gate_now)"
  window="$(case_write_window 0)" || window=unknown
  r007_stop_app || return 1
  local insp; insp="$(r007_inspect_checked)" || { fail "R4.4: the inspection failed: $(r007_plain "$insp")"; return 1; }
  R007_PAIR="$(r007_pair "$insp")"
  pred="$(yn '[ "$state" = blocked ] && [ "$window" != unknown ] && within "$window" 60000 1000 &&
    [ "${R007_PAIR%%,*}" = 5 ]')"
  r007_mark R4.4 "$caller" 1 "$pred" "$(r007_residual "$pred")" fixture=L5 script="WRITE_*_$script" processes="$n" \
    waits_s="$waits" last_gate="$state" \
    last_window_ms="$window" pair_after="$R007_PAIR"
}

segment_run() {
  local caller repeat n10 n3 scripts=(ReturnFalseBeforeCommit Throw HoldBeforeCommit)
  n10="$(reps 10)"; n3="$(reps 3)"
  for caller in $R007_CALLERS; do
    for (( repeat=1; repeat<=n10; repeat++ )); do r41a "$caller" "$repeat" "${scripts[$(( (repeat - 1) % 3 ))]}"; done
    for (( repeat=1; repeat<=n3; repeat++ )); do r41b "$caller" "$repeat"; done
    for (( repeat=1; repeat<=n3; repeat++ )); do r41c "$caller" "$repeat"; done
    for (( repeat=1; repeat<=n3; repeat++ )); do r42 "$caller" "$repeat"; done
    for (( repeat=1; repeat<=n3; repeat++ )); do r43 "$caller" "$repeat" plain; r43 "$caller" "$repeat" held; done
    r44 "$caller" ReturnFalseBeforeCommit
    r44 "$caller" Throw
  done
}
