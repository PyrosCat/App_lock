#!/usr/bin/env bash
# R-007 phase P2, segment "healthy": the healthy controls H01 to H06 and the construction-read cost (R1.4d).
# Sourced by p2_device.sh, which defines the case helpers. The verdicts use yn with one condition string each.

# H01: an open gate on Z for 60 s reads the store once (the construction read) and writes nothing.
h01() { # caller repeat
  local caller="$1" repeat="$2" reads writes state
  case_start "H01 $caller #$repeat" && case_prepare "$caller" 0 0 && case_open "$caller" || return 1
  sleep 60
  state="$(gate_now)"
  r007_capture_log || { fail "H01: logcat could not be read"; return 1; }
  reads="$(r007_phase_count "$R007_CAPTURE" "$R007_CASE_PID" READ BEGIN)"
  writes="$(r007_v_exact "$R007_CAPTURE" "$R007_CASE_PID")"
  case_kill_inspect || return 1
  r007_mark H01 "$caller" "$repeat" \
    "$(yn '[ "$reads" = 1 ] && [ "$writes" = 0 ] && [ "$state" = open ] && [ "$R007_PAIR" = 0,0 ]')" \
    "$(yn '[ "$writes" = 0 ] && [ "$state" = open ]')" fixture=Z script=none pid="$R007_CASE_PID" reads="$reads" \
    writes="$writes" gate="$state" pair_after="$R007_PAIR" gate_retries="$R007_GATE_RETRIES"
}

# H02: four wrong PINs, each until its write returns, then a fifth that locks, then two attempts on the blocked gate.
h02() { # caller repeat
  local caller="$1" repeat="$2" index pairs="" state5 entries label last
  case_start "H02 $caller #$repeat" && case_prepare "$caller" 0 0 && case_open "$caller" || return 1
  for index in 0 1 2 3 4; do
    r007_submit "$WRONG_PIN"; case_write_returned "$index" || return 1
  done
  sleep 1; state5="$(gate_now)"
  r007_submit "$WRONG_PIN"; r007_submit "$WRONG_PIN"; sleep 2
  r007_capture_log || { fail "H02: logcat could not be read"; return 1; }
  for index in 0 1 2 3 4; do pairs+="$(r007_begin_pair "$R007_CAPTURE" "$R007_CASE_PID" "$index");"; done
  entries="$(r007_v_exact "$R007_CAPTURE" "$R007_CASE_PID")"; label="$(r007_v_label "$R007_CAPTURE" "$R007_CASE_PID")"
  last="$(r007_begin_pair "$R007_CAPTURE" "$R007_CASE_PID" 4)"
  case_kill_inspect || return 1
  r007_mark H02 "$caller" "$repeat" \
    "$(yn '[ "$entries" = 5 ] && [ "$state5" = blocked ] && [ "$R007_PAIR" = "$last" ] &&
      [[ "$pairs" == "1,0;2,0;3,0;4,0;5,"* ]]')" \
    "$(yn '[ "$entries" = 5 ] && [ "$state5" = blocked ] && [ "$R007_PAIR" = "$last" ]')" fixture=Z script=none \
    pid="$R007_CASE_PID" write_pairs="$pairs" gate_after_5="$state5" v="$entries" v_label="$label" \
    v_inferred="$R007_V_INFERRED" submissions="$R007_SUBMISSIONS" pair_after="$R007_PAIR"
}

# H03: the gate around the deadline of L5, then a sixth wrong PIN (60 s window); then (11, expired) and a wrong PIN
# (the 30 min cap). A UI dump takes seconds, so each sample records the device time before and after its dump. An open
# gate in a dump that ended before the deadline is an early permission. A blocked gate in a dump that started more
# than 750 ms after the deadline (the 250 ms poll of the gate and a margin) is a late one.
h03() { # caller repeat
  local caller="$1" repeat="$2" deadline xml state t0 t1 samples="" opened="" early=0 late=0 i writes window6 pair6
  local window12 pair12
  case_start "H03 $caller #$repeat" \
    && case_prepare "$caller" 5 +30000 && case_open "$caller" "blocked open incorrect" || return 1
  deadline="${R007_FIXTURE_PAIR#*,}"
  for (( i=0; i<60; i++ )); do
    t0="$(r007_device_wall)" && xml="$(ui_xml)" && t1="$(r007_device_wall)" \
      || { fail "H03: the device time could not be read"; return 1; }
    state="$(r007_gate_state "$xml")"
    case "$state" in
      blocked)
        samples+="$(r007_countdown_s "$xml")@$t0-$t1;"
        [ "$t0" -le $(( deadline + 750 )) ] || late=$((late + 1)) ;;
      open|incorrect)
        samples+="open@$t0-$t1;"; opened="$t1"
        [ "$t1" -ge "$deadline" ] || early=$((early + 1))
        break ;;
    esac
    # Sample densely near the deadline.
    [ $(( deadline - t1 )) -lt 3000 ] || sleep 1
  done
  [ -n "$opened" ] || { fail "H03: the gate did not open after the L5 deadline"; return 1; }
  r007_capture_log || { fail "H03: logcat could not be read"; return 1; }
  writes="$(r007_v_exact "$R007_CAPTURE" "$R007_CASE_PID")"
  r007_submit "$WRONG_PIN"; case_write_returned 0 || return 1
  r007_capture_log || { fail "H03: logcat could not be read"; return 1; }
  window6="$(r007_write_window "$R007_CAPTURE" "$R007_CASE_PID" 0)" \
    || { fail "H03: no WRITE BEGIN line for the sixth failure"; return 1; }
  pair6="$(r007_begin_pair "$R007_CAPTURE" "$R007_CASE_PID" 0)"
  case_kill_inspect || return 1
  [ "$R007_PAIR" = "$pair6" ] || { fail "H03: the stored pair $R007_PAIR is not the written pair $pair6"; return 1; }
  case_prepare "$caller" 11 -60000 && case_open "$caller" || return 1
  r007_submit "$WRONG_PIN"; case_write_returned 0 || return 1
  r007_capture_log || { fail "H03: logcat could not be read"; return 1; }
  window12="$(r007_write_window "$R007_CAPTURE" "$R007_CASE_PID" 0)" \
    || { fail "H03: no WRITE BEGIN line for the twelfth failure"; return 1; }
  pair12="$(r007_begin_pair "$R007_CAPTURE" "$R007_CASE_PID" 0)"
  case_kill_inspect || return 1
  r007_mark H03 "$caller" "$repeat" \
    "$(yn '[ "$writes" = 0 ] && [ "$early" = 0 ] && [ "$late" = 0 ] && [ "${pair6%%,*}" = 6 ] \
      && [ "${pair12%%,*}" = 12 ] && [ "$R007_PAIR" = "$pair12" ] && within "$window6" 60000 1000 \
      && within "$window12" 1800000 1000')" \
    "$(yn '[ "$writes" = 0 ] && [ "$early" = 0 ] && within "$window12" 1800000 1000')" \
    fixture="L5;11,expired" script=none deadline="$deadline" samples="$samples" early_open="$early" \
    late_block="$late" writes_in_countdown="$writes" pair6="$pair6" window6_ms="$window6" pair12="$pair12" \
    window12_ms="$window12" pair_after="$R007_PAIR"
}

# H04: a correct PIN from C4 with the reset write held for 5 s. The gate opens before the write returns.
h04() { # caller repeat
  local caller="$1" repeat="$2" opened returned begin
  case_start "H04 $caller #$repeat" \
    && case_prepare "$caller" 4 0 "WRITE 0 HoldBeforeCommit Normal" && case_open "$caller" || return 1
  r007_submit "$PIN"; case_write_held 0 || return 1
  sleep 1
  opened="$(yn 'unlocked_now "$caller"')"
  r007_capture_log || { fail "H04: logcat could not be read"; return 1; }
  returned="$(yn 'r007_has "$R007_CAPTURE" "R007Fault: pid=$R007_CASE_PID .*op=WRITE index=0 phase=RETURNED"')"
  begin="$(r007_begin_pair "$R007_CAPTURE" "$R007_CASE_PID" 0)"
  sleep 4
  r007_release "$R007_CASE_PID" WRITE 0 && case_write_returned 0 || return 1
  case_kill_inspect || return 1
  r007_mark H04 "$caller" "$repeat" \
    "$(yn '[ "$opened" = yes ] && [ "$returned" = no ] && [ "$begin" = 0,0 ] && [ "$R007_PAIR" = 0,0 ]')" \
    "$(yn '[ "$opened" = yes ] && [ "$R007_PAIR" = 0,0 ]')" fixture=C4 script="WRITE_0_HoldBeforeCommit_Normal" \
    pid="$R007_CASE_PID" opened_while_held="$opened" returned_before_release="$returned" write_pair="$begin" \
    pair_after="$R007_PAIR"
}

# H05: a stored fixture reloads after a kill: the count stays, an active deadline blocks with the remaining time, and
# an expired deadline stays expired. No write. One UI dump gives the state and the countdown, and the device time
# before and after the dump bounds the moment of the dump (see r007_reload_ok).
h05() { # caller count deadline name
  local caller="$1" name="$4" repeat n expected="" xml t0 t1 state ui_s writes ok_state
  n="$(reps 3)"
  for (( repeat=1; repeat<=n; repeat++ )); do
    case_start "H05 $caller $name #$repeat" || return 1
    if [ "$repeat" = 1 ]; then case_prepare "$caller" "$2" "$3" || return 1; expected="$R007_FIXTURE_PAIR"
    elif [ "$caller" = L ]; then r007_grant_detector || return 1; fi
    case_open "$caller" "open incorrect blocked" || return 1
    t0="$(r007_device_wall)" && xml="$(ui_xml)" && t1="$(r007_device_wall)" \
      || { fail "H05: the device time could not be read"; return 1; }
    state="$(r007_gate_state "$xml")"; ui_s="$(r007_countdown_s "$xml")"
    r007_capture_log || { fail "H05: logcat could not be read"; return 1; }
    writes="$(r007_v_exact "$R007_CAPTURE" "$R007_CASE_PID")"
    case_kill_inspect || return 1
    ok_state="$(yn 'r007_reload_ok "$state" "$ui_s" "${expected#*,}" "$t0" "$t1"')"
    r007_mark H05 "$caller" "$repeat" \
      "$(yn '[ "$ok_state" = yes ] && [ "$writes" = 0 ] && [ "$R007_PAIR" = "$expected" ]')" \
      "$(yn '[ "$ok_state" = yes ] && [ "$R007_PAIR" = "$expected" ]')" fixture="$name" script=none \
      pid="$R007_CASE_PID" gate="$state" countdown_s="${ui_s:-none}" dump_wall="$t0-$t1" writes="$writes" \
      pair_before="$expected" pair_after="$R007_PAIR"
  done
}

# H06: the first write held for HOLD seconds. During a hold of 5 s or more: HOME and back, a second wrong PIN, and a
# screen cycle (with -o); UI dumps back to back in all holds. The gate stays responsive, the queue keeps its order,
# and the last admitted pair is stored. Only a dump that shows a gate state is a sample of responsiveness: a failed
# dump, or a dump of another screen, gives "none". Without a sample the repeat is not as predicted, and the
# objective is "na" unless the stored pair alone shows a loss.
h06() { # caller repeat hold-s
  local caller="$1" repeat="$2" hold="$3" start anr=no dumps=0 samples=0 second=no state p0 p1="" expected=1,0
  local held released held_ms objective
  case_start "H06 $caller hold ${hold}s #$repeat" \
    && case_prepare "$caller" 0 0 "WRITE 0 HoldBeforeCommit Normal" && case_open "$caller" || return 1
  r007_submit "$WRONG_PIN"; case_write_held 0 || return 1
  start=$SECONDS
  if [ "$hold" -ge 5 ]; then
    reopen "$caller" || return 1
    r007_submit "$WRONG_PIN"; second=yes; expected=2,0
    screen_cycle || return 1
  fi
  # A UI dump takes seconds on the Moto G, so the dumps run back to back with 1 s between them, at least once.
  while :; do
    state="$(gate_now)"; dumps=$((dumps + 1))
    [ "$state" = none ] || samples=$((samples + 1))
    [ "$state" != anr ] || anr=yes
    [ $(( SECONDS - start )) -lt "$hold" ] || break
    sleep 1
  done
  r007_release "$R007_CASE_PID" WRITE 0 && case_write_returned 0 || return 1
  [ "$second" = no ] || case_write_returned 1 || return 1
  r007_capture_log || { fail "H06: logcat could not be read"; return 1; }
  p0="$(r007_begin_pair "$R007_CAPTURE" "$R007_CASE_PID" 0)"; p1="$(r007_begin_pair "$R007_CAPTURE" "$R007_CASE_PID" 1)"
  # The time from the HELD line to the RELEASED line of write 0 (elapsed clock).
  held="$(r007_phase_elapsed "$R007_CAPTURE" "$R007_CASE_PID" WRITE 0 HELD)"
  released="$(r007_phase_elapsed "$R007_CAPTURE" "$R007_CASE_PID" WRITE 0 RELEASED)"
  held_ms=""; [ -z "$held" ] || [ -z "$released" ] || held_ms=$(( released - held ))
  case_kill_inspect || return 1
  objective="$(yn '[ "$anr" = no ] && [ "$R007_PAIR" = "$expected" ]')"
  [ "$samples" -gt 0 ] || [ "$objective" = no ] || objective=na
  r007_mark H06 "$caller" "$repeat" \
    "$(yn '[ "$samples" -gt 0 ] && [ "$anr" = no ] && [ "$p0" = 1,0 ] && [ "$R007_PAIR" = "$expected" ] &&
      { [ "$second" = no ] || [ "$p1" = 2,0 ]; }')" \
    "$objective" fixture=Z script="WRITE_0_HoldBeforeCommit_Normal" hold_s="$hold" held_ms="${held_ms:-unknown}" \
    pid="$R007_CASE_PID" ui_dumps="$dumps" gate_samples="$samples" anr="$anr" second_pin="$second" \
    screen_cycle="${R007_SCREEN_CYCLE:-none}" write_pairs="$p0;${p1:-none}" pair_after="$R007_PAIR"
}

# H06, threshold and reset under a 5 s hold: from C4, a fifth wrong PIN (the threshold write) or a correct PIN (the
# reset) with the write held. The gate follows the memory state during the hold, and the admitted pair is stored.
h06_threshold_reset() { # caller repeat
  local caller="$1" repeat="$2" kind pin state expected_count begin
  for kind in threshold reset; do
    pin="$WRONG_PIN"; expected_count=5
    [ "$kind" = threshold ] || { pin="$PIN"; expected_count=0; }
    case_start "H06 $caller $kind under hold #$repeat" \
      && case_prepare "$caller" 4 0 "WRITE 0 HoldBeforeCommit Normal" && case_open "$caller" || return 1
    r007_submit "$pin"; case_write_held 0 || return 1
    sleep 1
    if [ "$kind" = threshold ]; then state="$(gate_now)"; else state="unlocked=$(yn 'unlocked_now "$caller"')"; fi
    sleep 4
    r007_release "$R007_CASE_PID" WRITE 0 && case_write_returned 0 || return 1
    r007_capture_log || { fail "H06: logcat could not be read"; return 1; }
    begin="$(r007_begin_pair "$R007_CAPTURE" "$R007_CASE_PID" 0)"
    case_kill_inspect || return 1
    r007_mark "H06-$kind" "$caller" "$repeat" \
      "$(yn '[ "${begin%%,*}" = "$expected_count" ] && [ "$R007_PAIR" = "$begin" ] &&
        r007_in_list "$state" "blocked unlocked=yes"')" \
      "$(yn '[ "$R007_PAIR" = "$begin" ] && r007_in_list "$state" "blocked unlocked=yes"')" fixture=C4 \
      script="WRITE_0_HoldBeforeCommit_Normal" pid="$R007_CASE_PID" gate_during_hold="$state" write_pair="$begin" \
      pair_after="$R007_PAIR"
  done
}

# R1.4d: the cost of the construction read on the main thread at 30 cold starts per caller path. For S the start is a
# launch of MainActivity. For L it is the detector bind after a grant. The first start of the run is marked, because
# it can include the first keystore use of the boot. This is a measurement, so the objective verdict is "na".
r14d() { # caller
  local caller="$1" n i pid begin end ms samples=""
  n="$(reps 30)"
  case_start "R1.4d $caller" && r007_stop_app && r007_set_faults || return 1
  for (( i=1; i<=n; i++ )); do
    r007_stop_app || return 1
    if [ "$caller" = S ]; then
      home; sleep 1; r007_launch_main || { fail "R1.4d: MainActivity did not start"; return 1; }
    else r007_grant_detector || return 1; fi
    r007_wait_phase READ 0 RETURNED 15 >/dev/null || { fail "R1.4d: no construction read at start $i"; return 1; }
    pid="$(r007_pid)" || { fail "R1.4d: no single app process at start $i"; return 1; }
    r007_capture_log || { fail "R1.4d: logcat could not be read"; return 1; }
    begin="$(r007_phase_elapsed "$R007_CAPTURE" "$pid" READ 0 BEGIN main)"
    end="$(r007_phase_elapsed "$R007_CAPTURE" "$pid" READ 0 RETURNED main)"
    if [ -n "$begin" ] && [ -n "$end" ]; then ms=$(( end - begin )); else ms=unknown; fi
    samples+="$ms;"
    printf '## R1.4d sample caller=%s start=%s pid=%s main_thread_read_ms=%s first_of_run=%s\n' "$caller" "$i" "$pid" \
      "$ms" "$([ "$i" = 1 ] && echo yes || echo no)" >> "$R007_LOG_OUT"
    r007_clear_log "R1.4d $caller start $i" || return 1
  done
  r007_stop_app || return 1
  r007_mark R1.4d "$caller" 1 "$(yn '[[ "$samples" != *unknown* ]]')" na fixture=current script=none starts="$n" \
    main_thread_read_ms="$samples"
}

segment_run() {
  local caller repeat n
  n="$(reps 3)"
  for caller in $R007_CALLERS; do
    for (( repeat=1; repeat<=n; repeat++ )); do h01 "$caller" "$repeat"; done
    for (( repeat=1; repeat<=n; repeat++ )); do h02 "$caller" "$repeat"; done
    for (( repeat=1; repeat<=n; repeat++ )); do h03 "$caller" "$repeat"; done
    for (( repeat=1; repeat<=n; repeat++ )); do h04 "$caller" "$repeat"; done
    h05 "$caller" 1 0 "(1,0)"
    h05 "$caller" 4 0 "(4,0)"
    h05 "$caller" 8 +600000 "(8,now+10min)"
    h05 "$caller" 8 -60000 "(8,expired)"
    for (( repeat=1; repeat<=n; repeat++ )); do
      h06 "$caller" "$repeat" 1; h06 "$caller" "$repeat" 5; h06 "$caller" "$repeat" 40
      h06_threshold_reset "$caller" "$repeat"
    done
    r14d "$caller"
  done
}
