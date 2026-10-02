#!/usr/bin/env bash
# R-007 phase P2, segment "cold-read": the cold-start read failure (R1.1 to R1.4; R1.4d runs in the healthy segment).
# Sourced by p2_device.sh, which defines the case helpers.

# Prints the pair that the first READ RETURNED line of process PID after read 0 gave ("count,until").
reseed_pair() { # text pid
  grep -E "R007Fault: pid=$2 .*op=READ index=[1-9][0-9]* phase=RETURNED( |$)" <<< "$1" | head -1 \
    | sed -n -E 's/.* count=([0-9]+) until=(-?[0-9]+).*/\1,\2/p'
}

# R1.1a (L): the construction read at the detector bind throws over a stored lock at count 5, and no lock screen polls
# for 10 s. Then Clock opens: the first poll answers from the empty memory and starts the re-seed read, which loads
# the lock. R1.1a and R1.1b use a 10 min window instead of the 30 s of L5: a gate retry takes at least 20 s, and the
# lock must still be active when the re-seed read loads it.
r11a() { # repeat
  local repeat="$1" states="" i threw reseed_at reseed unenforced pred
  case_start "R1.1a L #$repeat" && case_prepare L 5 +600000 "READ 0 Throw" || return 1
  R007_CASE_PID="$(r007_pid)" || { fail "R1.1a: no app process after the grant"; return 1; }
  r007_wait_phase READ 0 THREW 15 "$R007_CASE_PID" >/dev/null \
    || { fail "R1.1a: the construction read did not throw"; return 1; }
  sleep 10
  home; sleep 1; launch_pkg "$R007_CLOCK"
  for (( i=0; i<4; i++ )); do states+="$(gate_now);"; done
  r007_capture_log || { fail "R1.1a: logcat could not be read"; return 1; }
  threw="$(r007_phase_elapsed "$R007_CAPTURE" "$R007_CASE_PID" READ 0 THREW)"
  reseed_at="$(r007_phase_elapsed "$R007_CAPTURE" "$R007_CASE_PID" READ 1 RETURNED)"
  reseed="$(reseed_pair "$R007_CAPTURE" "$R007_CASE_PID")"
  unenforced=unknown; [ -z "$threw" ] || [ -z "$reseed_at" ] || unenforced=$(( reseed_at - threw ))
  case_kill_inspect || return 1
  pred="$(yn '[ "$reseed" = "$R007_FIXTURE_PAIR" ] && [[ "$states" == *blocked* ]] &&
    [ "$R007_PAIR" = "$R007_FIXTURE_PAIR" ]')"
  r007_mark R1.1a L "$repeat" "$pred" "$(r007_residual "$pred")" fixture="(5,now+10min)" script=READ_0_Throw \
    pid="$R007_CASE_PID" reads="$(r007_phase_count "$R007_CAPTURE" "$R007_CASE_PID" READ BEGIN)" \
    unenforced_ms="$unenforced" reseed_pair="${reseed:-none}" gate_states="$states" pair_after="$R007_PAIR"
}

# R1.1b: every read throws over a stored lock at count 5 (10 min window) while the gate polls; the host publishes an
# empty script 1 s after the gate opens. The next poll re-seeds the lock.
r11b() { # caller repeat
  local caller="$1" repeat="$2" opened_at blocked_at="" state failed reseed i pred
  case_start "R1.1b $caller #$repeat" && case_prepare "$caller" 5 +600000 "READ * Throw" \
    && case_open "$caller" "open incorrect blocked" || return 1
  opened_at=$SECONDS; state="$R007_GATE"
  sleep 1; r007_set_faults || return 1
  for (( i=0; i<10; i++ )); do
    [ "$(gate_now)" = blocked ] && { blocked_at=$(( SECONDS - opened_at )); break; }
  done
  r007_capture_log || { fail "R1.1b: logcat could not be read"; return 1; }
  failed="$(r007_phase_count "$R007_CAPTURE" "$R007_CASE_PID" READ THREW)"
  reseed="$(reseed_pair "$R007_CAPTURE" "$R007_CASE_PID")"
  case_kill_inspect || return 1
  pred="$(yn '[ "$state" = open ] && [ -n "$blocked_at" ] && [ "${reseed%%,*}" = 5 ]')"
  r007_mark R1.1b "$caller" "$repeat" "$pred" "$(r007_residual "$pred")" \
    fixture="(5,now+10min)" script="READ_*_Throw,cleared_at_1s" pid="$R007_CASE_PID" gate_at_open="$state" \
    failed_reads="$failed" reseed_pair="${reseed:-none}" blocked_after_s="${blocked_at:-never}" pair_after="$R007_PAIR"
}

# R1.2a to R1.2d: every read throws over FIXTURE (L8 or Z). (a) The read rate at the gate polls over 10 s. (b) A
# wrong PIN commits (1,0). (c) A correct PIN commits Z. (d) The fault ends after 10 s and the next poll loads the
# stored pair. Over Z, (b) to (d) give the correct result for an empty store, so these controls have the objective
# "na". The read rate of (a) is the objective of X05 on both fixtures.
r12() { # caller repeat variant fixture-name
  local caller="$1" repeat="$2" variant="$3" name="$4" count deadline reads state after expected pred objective
  if [ "$name" = L8 ]; then count=8; deadline=+240000; else count=0; deadline=0; fi
  case_start "R1.2$variant $caller $name #$repeat" && case_prepare "$caller" "$count" "$deadline" "READ * Throw" \
    && case_open "$caller" "open incorrect blocked" || return 1
  state="$R007_GATE"; after=""
  case "$variant" in
    a) r007_clear_log "$R007_CASE_LABEL (to the gate)" || return 1; sleep 10 ;;
    b) r007_submit "$WRONG_PIN"; case_write_returned 0 || return 1 ;;
    c) r007_submit "$PIN"; case_write_returned 0 || return 1 ;;
    d) sleep 10; r007_set_faults || return 1; sleep 2; after="$(gate_now)" ;;
  esac
  r007_capture_log || { fail "R1.2: logcat could not be read"; return 1; }
  reads="$(r007_phase_count "$R007_CAPTURE" "$R007_CASE_PID" READ THREW)"
  case_kill_inspect || return 1
  case "$variant" in
    a) pred="$(yn '[ "$state" = open ] && [ "$reads" -ge 30 ] && [ "$reads" -le 50 ] &&
         [ "$R007_PAIR" = "$R007_FIXTURE_PAIR" ]')" ;;
    b) pred="$(yn '[ "$state" = open ] && [ "$R007_PAIR" = 1,0 ]')" ;;
    c) pred="$(yn '[ "$state" = open ] && [ "$R007_PAIR" = 0,0 ]')" ;;
    d) expected=open; [ "$name" = Z ] || expected=blocked
       pred="$(yn '[ "$state" = open ] && [ "$after" = "$expected" ] && [ "$R007_PAIR" = "$R007_FIXTURE_PAIR" ]')" ;;
  esac
  objective="$(r007_residual "$pred")"
  [ "$name" = L8 ] || [ "$variant" = a ] || objective=na
  r007_mark "R1.2$variant" "$caller" "$repeat" "$pred" "$objective" fixture="$name" \
    script="READ_*_Throw" pid="$R007_CASE_PID" gate_at_open="$state" failed_reads="$reads" \
    gate_after_fault="${after:-none}" pair_before="$R007_FIXTURE_PAIR" pair_after="$R007_PAIR"
}

# R1.2e: the first process and 20 restart cycles, each with wrong PINs until the gate blocks (at most 7), under a
# persistent read fault, and a control run without the fault. The detector stays granted for L, so the system
# restarts the process after each kill; the store is inspected only at the end.
r12e() { # caller script(fault|control)
  local caller="$1" mode="$2" cycle pid entries per="" n submitted killed rules=()
  [ "$mode" = control ] || rules=("READ * Throw")
  n="$(reps 21)"
  case_start "R1.2e $caller $mode" && case_prepare "$caller" 0 0 "${rules[@]}" || return 1
  for (( cycle=1; cycle<=n; cycle++ )); do
    r007_open_gate "$caller" "open incorrect blocked" || return 1
    pid="$(r007_pid)" || { fail "R1.2e: no single app process in cycle $cycle"; return 1; }
    # A submission to a blocked gate ends the process; r007_submit does not count it as a verifier entry.
    for (( submitted=0; submitted<7; submitted++ )); do
      r007_submit "$WRONG_PIN"; [ "$R007_SUBMIT_STATE" != blocked ] || break
    done
    r007_capture_log || { fail "R1.2e: logcat could not be read"; return 1; }
    entries="$(r007_v_exact "$R007_CAPTURE" "$pid")"; per+="$entries;"
    printf '## R1.2e cycle caller=%s mode=%s process=%s pid=%s v=%s\n' "$caller" "$mode" "$cycle" "$pid" "$entries" \
      >> "$R007_LOG_OUT"
    killed="$(r007_kill)" || { fail "R1.2e: the kill failed in cycle $cycle: $(r007_plain "$killed")"; return 1; }
    [ "$caller" = S ] || r007_wait_new_process "$pid" || return 1
  done
  r007_stop_app || return 1
  local insp
  insp="$(r007_inspect_checked)" || { fail "R1.2e: the inspection failed: $(r007_plain "$insp")"; return 1; }
  R007_PAIR="$(r007_pair "$insp")"
  if [ "$mode" = fault ]; then
    local every5='^(5;)+$' pred
    pred="$(yn '[[ "$per" =~ $every5 ]]')"
    r007_mark R1.2e "$caller" 1 "$pred" "$(r007_residual "$pred")" fixture=Z script="READ_*_Throw" processes="$n" \
      v_per_process="$per" v_label=exact pair_after="$R007_PAIR"
  else
    # With real time, the 30 s lock of the first process can end during later cycles, so one entry per restarted
    # process is possible.
    local control='^5;([01];)*$'
    r007_mark R1.2e-control "$caller" 1 "$(yn '[[ "$per" =~ $control ]]')" "$(yn '[[ "$per" =~ $control ]]')" \
      fixture=Z script=none processes="$n" v_per_process="$per" v_label=exact pair_after="$R007_PAIR"
  fi
}

# R1.3: over a stored lock at count 5, the construction read throws and the re-seed read holds after it has read the
# old value. While it holds: (a) a wrong PIN, (b) a correct PIN, (f) a wrong PIN whose write fails. The writes wait
# behind the held read. After the release, the old value must not come back into memory. The stored lock has a 10 min
# window instead of the 30 s of L5, so that an old lock that came back still blocks when the harness looks. One UI
# dump gives the live state: (a) an open gate (count 1), (b) an open gate after a reopen in the same process (count
# 0), (f) the degraded lock of the failed write (at most 30 s left), not the old lock.
r13() { # caller repeat variant
  local caller="$1" repeat="$2" variant="$3" pin="$WRONG_PIN" rules=("READ 0 Throw" "READ 1 ReadThenHold")
  local expected xml state
  local left live=no pred
  [ "$variant" != b ] || pin="$PIN"
  [ "$variant" != f ] || rules+=("WRITE 0 ReturnFalseBeforeCommit")
  case_start "R1.3$variant $caller #$repeat" && case_prepare "$caller" 5 +600000 "${rules[@]}" \
    && case_open "$caller" "open incorrect blocked" || return 1
  r007_wait_phase READ 1 HELD 15 "$R007_CASE_PID" >/dev/null \
    || { fail "R1.3: the re-seed read did not hold"; return 1; }
  r007_submit "$pin"; sleep 2
  r007_release "$R007_CASE_PID" READ 1 && case_write_returned 0 || return 1
  sleep 1
  [ "$variant" != b ] || reopen "$caller" || return 1
  xml="$(ui_xml)"; state="$(r007_gate_state "$xml")"; left="$(r007_countdown_s "$xml")"
  case "$variant" in
    a|b) r007_in_list "$state" "open incorrect" && live=yes ;;
    f) [ "$state" = blocked ] && [ -n "$left" ] && [ "$left" -le 30 ] && live=yes ;;
  esac
  case_kill_inspect || return 1
  case "$variant" in a) expected=1,0 ;; b) expected=0,0 ;; f) expected="$R007_FIXTURE_PAIR" ;; esac
  pred="$(yn '[ "$live" = yes ] && [ "$R007_PAIR" = "$expected" ]')"
  r007_mark "R1.3$variant" "$caller" "$repeat" "$pred" "$(r007_residual "$pred")" \
    fixture="(5,now+10min)" script="$(IFS=,; echo "${rules[*]// /_}")" pid="$R007_CASE_PID" \
    gate_after_release="$state" countdown_after_release_s="${left:-none}" old_value_kept_out="$live" \
    pair_before="$R007_FIXTURE_PAIR" pair_after="$R007_PAIR"
}

# Prints the marker fields am_anr and am_kill for process PID (r007_proc_events). Each field is the first event of its
# kind as "<n>s:<reason>" (n seconds after HELD-EPOCH), with "_" for each space of the reason, or "none". Both fields
# are "unknown" when logcat cannot be read. Without HELD-EPOCH, the time is "unknown".
r14_events() { # pid held-epoch
  local events kind field out=""
  events="$(r007_proc_events "$1")" || { printf 'am_anr=unknown am_kill=unknown'; return; }
  for kind in am_anr am_kill; do
    field="$(grep -m1 "^$kind " <<< "$events" | awk -v held="$2" '{
      # Rounds to the nearest second; int() alone would round an event 1 s before the hold to 0 s.
      offset = $2 - held
      at = (held == "") ? "unknown" : sprintf("%ds", offset < 0 ? -int(-offset + 0.5) : int(offset + 0.5))
      $1 = ""; $2 = ""; sub(/^ +/, ""); gsub(/ /, "_"); printf "%s:%s", at, $0 }')"
    out+=" $kind=${field:-none}"
  done
  printf '%s' "${out# }"
}

# R1.4a (S) and R1.4b (L): the construction read holds on the main thread for 40 s at a cold start, then reads. The
# harness taps the screen after 2 s (an input event for the ANR timer) and looks for an ANR dialog. `input tap` returns
# only after the app has handled the tap, and the held main thread cannot handle it. So the tap runs as a background
# job. The system can kill a process with a held main thread without a dialog (an ANR of a process in the background).
# So the hold loop also checks the process, and a death goes into the marker with its time. A dump of the window of a
# held app can fail, while an ANR dialog is a system window. So the marker counts the dumps that returned a screen.
# Without one, the ANR record is "unknown", not "none". The marker also records the am_anr and am_kill events of the
# case process (r14_events), which come from the events buffer and so do not depend on a dump.
r14ab() { # caller repeat
  local caller="$1" repeat="$2" start anr_at="" died_at="" held_ms state tap_job="" insp xml dumps=0 usable=0 anr
  local held_at events pred
  case_start "R1.4$([ "$caller" = S ] && echo a || echo b) $caller #$repeat" || return 1
  r007_stop_app && r007_fixture 5 +30000 && r007_set_faults "READ 0 HoldThenRead" || return 1
  # No wait for the launch or the bind: the main thread holds in the construction read.
  if [ "$caller" = S ]; then home; sleep 1; sh_ am start -n "$MAIN_ACTIVITY" >/dev/null
  else r007_grant_write || return 1; fi
  r007_wait_phase READ 0 HELD 20 >/dev/null || { fail "R1.4: the construction read did not hold"; return 1; }
  R007_CASE_PID="$(r007_pid)" || { fail "R1.4: no single app process"; return 1; }
  # The epoch time of the HELD line, so that the event times are relative to the start of the hold.
  held_at="$(grep -E "R007Fault: pid=$R007_CASE_PID .*op=READ index=0 phase=HELD( |$)" <<< "$R007_CAPTURE" \
    | awk 'NR == 1 { print $1 }')"
  start=$SECONDS
  # A tap near the top, away from the PIN keys, gives the input event that starts the ANR timer.
  sleep 2; if _screen_wh; then tap_frac 0.5 0.1 >/dev/null & tap_job=$!; fi
  while [ $(( SECONDS - start )) -lt 40 ]; do
    if [ -z "$anr_at" ]; then
      xml="$(ui_xml)"; dumps=$((dumps + 1)); [ -z "$xml" ] || usable=$((usable + 1))
      if grep -qF -- "$R007_UI_ANR" <<< "$xml"; then anr_at=$(( SECONDS - start )); fi
    fi
    if [ "$(r007_proc_state "$R007_CASE_PID")" = absent ]; then died_at=$(( SECONDS - start )); break; fi
    sleep 2
  done
  if [ -n "$anr_at" ]; then anr="$anr_at"; elif [ "$usable" -gt 0 ]; then anr=none; else anr=unknown; fi
  if [ -n "$died_at" ]; then
    # The plan predicts a read that returns at 40 s. For L, it also predicts a death by an ANR: the system can kill a
    # service process whose main thread holds past the service timeout. That death is as predicted only with an
    # am_anr event of the process at the start of the hold or later. For S, the app is in the foreground, where an
    # ANR shows a dialog, so a death is not as predicted. A main thread held for 5 s or more before the death shows
    # the unmet objective. The fault script would also hold the read of a process that the grant restarts, so the
    # grant goes first, then a stop of that process.
    [ -z "$tap_job" ] || wait "$tap_job"
    events="$(r14_events "$R007_CASE_PID" "$held_at")"
    pred=no; if [ "$caller" = L ] && [[ "$events" =~ ^am_anr=[0-9]+s: ]]; then pred=yes; fi
    if [ "$caller" = L ]; then r007_revoke_detector || return 1; fi
    r007_stop_app || return 1
    insp="$(r007_inspect_checked)" || { fail "R1.4: the inspection failed: $(r007_plain "$insp")"; return 1; }
    R007_PAIR="$(r007_pair "$insp")"
    r007_mark "R1.4$([ "$caller" = S ] && echo a || echo b)" "$caller" "$repeat" "$pred" \
      "$([ "$died_at" -ge 5 ] && echo no || echo na)" fixture=L5 script=READ_0_HoldThenRead pid="$R007_CASE_PID" \
      process_died_after_s="$died_at" main_thread_read_ms=unknown anr_dialog_after_s="$anr" ui_dumps="$dumps" \
      usable_dumps="$usable" "$events" pair_after="$R007_PAIR"
    return
  fi
  r007_release "$R007_CASE_PID" READ 0 || return 1
  [ -z "$tap_job" ] || wait "$tap_job"
  r007_wait_phase READ 0 RETURNED 15 "$R007_CASE_PID" >/dev/null || { fail "R1.4: the read did not return"; return 1; }
  sleep 2; state="$(gate_now)"; dismiss_anr
  r007_capture_log || { fail "R1.4: logcat could not be read"; return 1; }
  local begin end
  begin="$(r007_phase_elapsed "$R007_CAPTURE" "$R007_CASE_PID" READ 0 BEGIN)"
  end="$(r007_phase_elapsed "$R007_CAPTURE" "$R007_CASE_PID" READ 0 RETURNED)"
  [ -n "$begin" ] && [ -n "$end" ] || { fail "R1.4: no BEGIN and RETURNED lines for the construction read"; return 1; }
  held_ms=$(( end - begin ))
  events="$(r14_events "$R007_CASE_PID" "$held_at")"
  case_kill_inspect || return 1
  r007_mark "R1.4$([ "$caller" = S ] && echo a || echo b)" "$caller" "$repeat" \
    "$(yn '[ "$held_ms" -ge 39000 ] && [ "$R007_PAIR" = "$R007_FIXTURE_PAIR" ]')" \
    "$(yn '[ -z "$anr_at" ] && [ "$held_ms" -lt 5000 ]')" \
    fixture=L5 script=READ_0_HoldThenRead pid="$R007_CASE_PID" process_died_after_s=none \
    main_thread_read_ms="$held_ms" anr_dialog_after_s="$anr" ui_dumps="$dumps" usable_dumps="$usable" "$events" \
    gate_after="$state" pair_after="$R007_PAIR"
}

# R1.4c: the construction read throws, and the re-seed read holds before it reads, on the writer thread. Five wrong
# PINs while it holds: the gate enforces from memory, and no write begins until the release at 40 s.
r14c() { # caller repeat
  local caller="$1" repeat="$2" start begun_before entries last pred attempt
  case_start "R1.4c $caller #$repeat" && case_prepare "$caller" 5 +30000 "READ 0 Throw" "READ 1 HoldThenRead" \
    && case_open "$caller" "open incorrect blocked" || return 1
  r007_wait_phase READ 1 HELD 15 "$R007_CASE_PID" >/dev/null \
    || { fail "R1.4c: the re-seed read did not hold"; return 1; }
  start=$SECONDS
  for attempt in 1 2 3 4 5; do r007_submit "$WRONG_PIN"; done
  r007_capture_log || { fail "R1.4c: logcat could not be read"; return 1; }
  begun_before="$(r007_v_exact "$R007_CAPTURE" "$R007_CASE_PID")"
  while [ $(( SECONDS - start )) -lt 40 ]; do sleep 1; done
  r007_release "$R007_CASE_PID" READ 1 || return 1
  case_write_returned 4 20 || return 1
  r007_capture_log || { fail "R1.4c: logcat could not be read"; return 1; }
  entries="$(r007_v_exact "$R007_CAPTURE" "$R007_CASE_PID")"
  last="$(r007_begin_pair "$R007_CAPTURE" "$R007_CASE_PID" 4)"
  case_kill_inspect || return 1
  pred="$(yn '[ "$begun_before" = 0 ] && [ "$entries" = 5 ] && [ "$R007_PAIR" = "$last" ]')"
  r007_mark R1.4c "$caller" "$repeat" "$pred" "$(r007_residual "$pred")" fixture=L5 \
    script="READ_0_Throw,READ_1_HoldThenRead" pid="$R007_CASE_PID" writes_begun_during_hold="$begun_before" \
    v="$entries" v_label=exact v_inferred="$R007_V_INFERRED" last_write_pair="$last" pair_after="$R007_PAIR"
}

segment_run() {
  local caller repeat n variant
  n="$(reps 3)"
  if caller_on L; then for (( repeat=1; repeat<=n; repeat++ )); do r11a "$repeat"; done; fi
  for caller in $R007_CALLERS; do
    for (( repeat=1; repeat<=n; repeat++ )); do r11b "$caller" "$repeat"; done
    for variant in a b c d; do
      for (( repeat=1; repeat<=n; repeat++ )); do
        r12 "$caller" "$repeat" "$variant" L8; r12 "$caller" "$repeat" "$variant" Z
      done
    done
    r12e "$caller" fault
    r12e "$caller" control
    for variant in a b f; do for (( repeat=1; repeat<=n; repeat++ )); do r13 "$caller" "$repeat" "$variant"; done; done
    for (( repeat=1; repeat<=n; repeat++ )); do r14ab "$caller" "$repeat"; r14c "$caller" "$repeat"; done
  done
}
