#!/usr/bin/env bash
# R-007 phase P2, segment "death": death before admitted changes are durable (R3.1a to R3.1d, R3.2, R3.3, R3.4).
# Sourced by p2_device.sh, which defines the case helpers. The critical cuts use the fixtures Z and C4 in turn.

# Prints the count and the deadline of the fixture for repeat R of a critical cut: Z for odd repeats, C4 for even.
cut_fixture() { if (( $1 % 2 )); then printf '0 0'; else printf '4 0'; fi; }
cut_fixture_name() { if (( $1 % 2 )); then printf Z; else printf C4; fi; }

R31A_WINDOW=""   # "low high": the kill window of the caller from its R3.1a control, in ms after the last tap starts
# The main-thread CPU ticks between the start of the last tap and the kill that show the PIN check has started. On the
# Moto G (probe of 2026-09-30), a digit tap used at most 3 ticks, and the Argon2 hash of the check 61 ticks in 640 ms.
: "${R31A_ENTRY_TICKS:=8}"

# True when LOW and HIGH are a kill window of at least 200 ms, so that a kill in its middle has a margin of 100 ms on
# each side.
r31a_window_ok() { # low high
  [[ "${1:-}" =~ ^[0-9]+$ ]] && [[ "${2:-}" =~ ^-?[0-9]+$ ]] && [ $(( $2 - $1 )) -ge 200 ]
}

# Prints why an R3.1a trial does not count, or nothing when it counts:
#   write       the wrapper logged a WRITE line for the process;
#   no-times    the device gave no kill times;
#   tap-failed  the last tap exited with an error;
#   no-entry    the main thread used fewer than R31A_ENTRY_TICKS CPU ticks between the start of the last tap and the
#               kill, so the PIN check had not started, or its start is unknown.
r31a_reason() { # write-lines kill-sent-ms tap-status main-ticks
  if [ "$1" != 0 ]; then printf write
  elif [ -z "$2" ]; then printf no-times
  elif [ "$3" != 0 ]; then printf tap-failed
  elif ! [[ "${4:-}" =~ ^[0-9]+$ ]] || [ "$4" -lt "$R31A_ENTRY_TICKS" ]; then printf no-entry; fi
}

# R3.1a control: one wrong PIN from Z with timed taps. It measures the kill window of r007_kill_window, and the trials
# kill at the middle of this window (see r31a_window_ok). This is a measurement, so the objective verdict is "na".
r31a_control() { # caller
  local caller="$1" begin low="" high=""
  R31A_WINDOW=""
  case_start "R3.1a $caller control" && case_prepare "$caller" 0 0 && case_open "$caller" || return 1
  # As in the trials, the grant is removed before the PIN.
  if [ "$caller" = L ]; then r007_revoke_detector || return 1; fi
  r007_submit "$WRONG_PIN"; case_write_returned 0 || return 1
  r007_capture_log || { fail "R3.1a control: logcat could not be read"; return 1; }
  begin="$(r007_begin_wall "$R007_CAPTURE" "$R007_CASE_PID" 0)"
  read -r low high <<< "$(r007_kill_window "$R007_TAP_TIMES" "$begin")"
  r007_stop_app || return 1
  r007_mark R3.1a-control "$caller" 1 "$(yn 'r31a_window_ok "$low" "$high"')" na fixture=Z \
    script=none pid="$R007_CASE_PID" tap_times="${R007_TAP_TIMES// /;}" write_begin_wall="${begin:-none}" \
    window_ms="${low:-none}-${high:-none}" || return 1
  R31A_WINDOW="$low $high"
}

# R3.1a: a kill during the PIN check, before the write begins. The last tap runs in the background, and the kill
# follows at the middle of the window of the control. A trial counts only when the tap succeeded, the main thread
# shows the check running at the kill (R31A_ENTRY_TICKS), and the wrapper logged no WRITE line for the process. At
# most 30 trials per caller. The check gave no result before the kill, so the objective verdict is "na". The JVM cut
# (a death after the check and before the admission) is not reachable by a host kill, and the plan lists it as a gap
# of the device lanes.
r31a() { # caller
  local caller="$1" valid=0 trial=0 n count deadline name lines after low high delay sent ended reason insp
  local main_state main_ticks
  n="$(reps 10)"
  r31a_control "$caller" || { fail "R3.1a $caller: the control gave no kill window, so no trial runs"; return 1; }
  read -r low high <<< "$R31A_WINDOW"; delay=$(( (low + high) / 2 ))
  while [ "$valid" -lt "$n" ] && [ "$trial" -lt 30 ]; do
    trial=$((trial + 1)); name="$(cut_fixture_name $((valid + 1)))"
    read -r count deadline <<< "$(cut_fixture $((valid + 1)))"
    case_start "R3.1a $caller trial $trial" \
      && case_prepare "$caller" "$count" "$deadline" && case_open "$caller" || return 1
    # For L, the grant is removed before the PIN, so that the kill does not wait for the unbind.
    if [ "$caller" = L ]; then r007_revoke_detector || return 1; fi
    r007_submit_kill "$WRONG_PIN" "$R007_CASE_PID" "$delay" || return 1
    read -r sent ended <<< "$R007_KILL_TIMES"
    read -r main_state main_ticks <<< "$R007_KILL_MAIN"
    r007_capture_log || { fail "R3.1a: logcat could not be read"; return 1; }
    lines="$(r007_phase_count "$R007_CAPTURE" "$R007_CASE_PID" WRITE "[A-Z_]+")"   # WRITE lines of any phase
    reason="$(r31a_reason "$lines" "$sent" "$R007_KILL_TAP_RC" "$main_ticks")"
    if [ -n "$reason" ]; then
      printf '## R3.1a trial=%s caller=%s counted=no reason=%s write_lines=%s kill_ms=%s-%s tap_rc=%s' \
        "$trial" "$caller" "$reason" "$lines" "${sent:-none}" "${ended:-none}" "${R007_KILL_TAP_RC:-none}" \
        >> "$R007_LOG_OUT"
      printf ' main_thread=%s main_ticks=%s window_ms=%s-%s\n' "${main_state:-none}" "${main_ticks:-none}" "$low" \
        "$high" >> "$R007_LOG_OUT"
      continue
    fi
    valid=$((valid + 1))
    insp="$(r007_inspect_checked)" || { fail "R3.1a: the inspection failed: $(r007_plain "$insp")"; return 1; }
    R007_PAIR="$(r007_pair "$insp")"; after="$(gate_after_restart "$caller")"
    r007_mark R3.1a "$caller" "$valid" "$(yn '[ "$R007_PAIR" = "$R007_FIXTURE_PAIR" ] && [ "$after" = open ]')" na \
      fixture="$name" script=none trial="$trial" pid="$R007_CASE_PID" write_lines=0 kill_delay_ms="$delay" \
      kill_ms="$sent-$ended" tap_rc="$R007_KILL_TAP_RC" main_thread="$main_state" main_ticks="$main_ticks" \
      window_ms="$low-$high" pair_before="$R007_FIXTURE_PAIR" pair_after="$R007_PAIR" gate_after_restart="$after"
  done
  [ "$valid" -ge "$n" ] || fail "R3.1a $caller: only $valid of $n trials counted after $trial trials"
}

# R3.1b: the first wrong PIN holds before the commit, and a second one waits in the queue (from Z; from C4 the first
# failure locks the gate, so the second attempt is refused). Kill. Both failures are lost; from C4 the threshold lock
# is lost.
r31b() { # caller repeat
  local caller="$1" repeat="$2" count deadline name state2 after pred
  name="$(cut_fixture_name "$repeat")"; read -r count deadline <<< "$(cut_fixture "$repeat")"
  case_start "R3.1b $caller #$repeat" && case_prepare "$caller" "$count" "$deadline" "WRITE 0 HoldBeforeCommit Normal" \
    && case_open "$caller" || return 1
  r007_submit "$WRONG_PIN"; case_write_held 0 || return 1
  sleep 1; state2="$(gate_now)"
  r007_submit "$WRONG_PIN"; sleep 2
  case_kill_inspect || return 1
  after="$(gate_after_restart "$caller")"
  # The held first failure is in memory: from Z the gate admits the second PIN, from C4 it is the threshold lock.
  local gate2_ok='r007_in_list "$state2" "open incorrect"'
  [ "$name" = Z ] || gate2_ok='[ "$state2" = blocked ]'
  pred="$(yn '[ "$R007_PAIR" = "$R007_FIXTURE_PAIR" ] && [ "$after" = open ] && '"$gate2_ok")"
  r007_mark R3.1b "$caller" "$repeat" "$pred" "$(r007_residual "$pred")" fixture="$name" \
    script="WRITE_0_HoldBeforeCommit_Normal" pid="$R007_CASE_PID" gate_before_second="$state2" \
    v_inferred="$R007_V_INFERRED" v_label=inferred pair_before="$R007_FIXTURE_PAIR" pair_after="$R007_PAIR" \
    gate_after_restart="$after"
}

# R3.1 held: one wrong PIN with the write held before the commit, then a kill. The failure is lost. The write is held
# at the kill, so V has the label of r007_v_label.
r31held() { # caller repeat
  local caller="$1" repeat="$2" count deadline name after entries label pred
  name="$(cut_fixture_name "$repeat")"; read -r count deadline <<< "$(cut_fixture "$repeat")"
  case_start "R3.1held $caller #$repeat" \
    && case_prepare "$caller" "$count" "$deadline" "WRITE 0 HoldBeforeCommit Normal" \
    && case_open "$caller" || return 1
  r007_submit "$WRONG_PIN"; case_write_held 0 || return 1
  r007_capture_log || { fail "R3.1held: logcat could not be read"; return 1; }
  entries="$(r007_v_exact "$R007_CAPTURE" "$R007_CASE_PID")"; label="$(r007_v_label "$R007_CAPTURE" "$R007_CASE_PID")"
  case_kill_inspect || return 1
  after="$(gate_after_restart "$caller")"
  pred="$(yn '[ "$R007_PAIR" = "$R007_FIXTURE_PAIR" ] && [ "$after" = open ]')"
  r007_mark R3.1held "$caller" "$repeat" "$pred" "$(r007_residual "$pred")" fixture="$name" \
    script="WRITE_0_HoldBeforeCommit_Normal" pid="$R007_CASE_PID" v="$entries" v_label="$label" \
    v_inferred="$R007_V_INFERRED" pair_before="$R007_FIXTURE_PAIR" pair_after="$R007_PAIR" gate_after_restart="$after"
}

# R3.1d: the write commits and then holds before it returns; kill. The new pair is durable.
r31d() { # caller repeat
  local caller="$1" repeat="$2" count deadline name begin
  name="$(cut_fixture_name "$repeat")"; read -r count deadline <<< "$(cut_fixture "$repeat")"
  case_start "R3.1d $caller #$repeat" && case_prepare "$caller" "$count" "$deadline" "WRITE 0 CommitThenHold" \
    && case_open "$caller" || return 1
  r007_submit "$WRONG_PIN"; case_write_held 0 || return 1
  r007_capture_log || { fail "R3.1d: logcat could not be read"; return 1; }
  begin="$(r007_begin_pair "$R007_CAPTURE" "$R007_CASE_PID" 0)"
  case_kill_inspect || return 1
  r007_mark R3.1d "$caller" "$repeat" "$(yn '[ -n "$begin" ] && [ "$R007_PAIR" = "$begin" ]')" \
    "$(yn '[ -n "$begin" ] && [ "$R007_PAIR" = "$begin" ]')" fixture="$name" script=WRITE_0_CommitThenHold \
    pid="$R007_CASE_PID" write_pair="$begin" pair_before="$R007_FIXTURE_PAIR" pair_after="$R007_PAIR"
}

# R3.1c: the mid-commit sweep of the P2 device plan. No fault script: the kill targets the platform
# commit. The loop kills when the backup file appears, after a delay of 0, 2, 5, or 10 ms in turn. At least 60 trials
# and 10 trials inside the platform write; at most 150 trials.
r31c() { # caller
  local caller="$1" trials inside=0 trial n delay count deadline name old new bak class
  local delays=(0 2 5 10)
  n="$(reps 60)"
  for (( trial=1; trial<=150; trial++ )); do
    [ "$trial" -gt "$n" ] && [ "$inside" -ge "$(reps 10)" ] && break
    delay="${delays[$(( (trial - 1) % 4 ))]}"; name="$(cut_fixture_name "$trial")"
    read -r count deadline <<< "$(cut_fixture "$trial")"
    case_start "R3.1c $caller trial $trial" \
      && case_prepare "$caller" "$count" "$deadline" && case_open "$caller" || return 1
    old="$R007_FIXTURE_PAIR"
    if [ "$caller" = L ]; then r007_revoke_detector || return 1; fi
    r007_sweep_start "$R007_CASE_PID" "$delay" || return 1
    sleep 0.5
    r007_submit "$WRONG_PIN"
    r007_sweep_wait || return 1
    [ "$R007_SWEEP_ANSWER" = killed ] || { fail "R3.1c: the loop did not kill in trial $trial"; continue; }
    r007_wait_dead "$R007_CASE_PID" || { fail "R3.1c: no confirmed death in trial $trial"; return 1; }
    r007_capture_log || { fail "R3.1c: logcat could not be read"; return 1; }
    new="$(r007_begin_pair "$R007_CAPTURE" "$R007_CASE_PID" 0)"
    r007_inspect_files || return 1
    bak="$(sed -E 's/.*bak=([^ ]+).*/\1/' <<< "$R007_FILES")"
    class="$(r007_sweep_class "$R007_CAPTURE" "$R007_CASE_PID" "$bak" "$(r007_pair "$R007_INSPECTION")" "$old" "$new")"
    [ "$class" = platform-write ] && inside=$((inside + 1))
    r007_mark R3.1c "$caller" "$trial" "$(yn '[ "$class" != anomaly ]')" "$(yn '[ "$class" != anomaly ]')" \
      fixture="$name" script=none delay_ms="$delay" pid="$R007_CASE_PID" class="$class" $R007_FILES pair_before="$old" \
      write_pair="${new:-none}" pair_after="$(r007_pair "$R007_INSPECTION")"
  done
  trials=$((trial - 1))
  printf '## R3.1c caller=%s trials=%s inside_platform_write=%s\n' "$caller" "$trials" "$inside" >> "$R007_LOG_OUT"
  [ "$inside" -ge "$(reps 10)" ] \
    || fail "R3.1c $caller: only $inside trials inside the platform write after $trials trials"
}

# R3.2: from (2,0), four held writes: F3, F4, a correct PIN (the reset), and F1 of the new streak from the same gate
# after the unlock. RELEASED writes are released in order, then a kill. The stored pair is the prefix of RELEASED
# writes. The objective is met only when all 4 writes are released and the stored pair is the last one.
r32() { # caller released
  local caller="$1" released="$2" i expected=("2,0" "3,0" "4,0" "0,0" "1,0") pred objective
  case_start "R3.2 $caller k=$released" && case_prepare "$caller" 2 0 "WRITE 0 HoldBeforeCommit Normal" \
    "WRITE 1 HoldBeforeCommit Normal" "WRITE 2 HoldBeforeCommit Normal" "WRITE 3 HoldBeforeCommit Normal" \
    && case_open "$caller" || return 1
  r007_submit "$WRONG_PIN"; case_write_held 0 || return 1
  r007_submit "$WRONG_PIN"
  r007_submit "$PIN"; sleep 1
  r007_open_gate "$caller" "open incorrect" || return 1
  [ "$(r007_pid)" = "$R007_CASE_PID" ] || { fail "R3.2: the app process changed after the unlock"; return 1; }
  r007_submit "$WRONG_PIN"; sleep 1
  for (( i=0; i<released; i++ )); do
    case_write_held "$i" && r007_release "$R007_CASE_PID" WRITE "$i" && case_write_returned "$i" || return 1
  done
  [ "$released" -ge 4 ] || case_write_held "$released" || return 1
  case_kill_inspect || return 1
  pred="$(yn '[ "$R007_PAIR" = "${expected[$released]}" ]')"
  if [ "$released" = 4 ]; then objective="$pred"; else objective="$(r007_residual "$pred")"; fi
  r007_mark R3.2 "$caller" "k=$released" "$pred" "$objective" fixture="(2,0)" \
    script="WRITE_0-3_HoldBeforeCommit_Normal" pid="$R007_CASE_PID" released="$released" \
    expected="${expected[$released]}" v_inferred="$R007_V_INFERRED" pair_after="$R007_PAIR"
}

# R3.3: from Z, the first write holds. It is released 40 s after it began, or never (the kill). The steps:
#   1. wrong PINs at the UI rate until the gate blocks (the threshold lock from memory);
#   2. HOME and back, and a screen cycle (with -o);
#   3. a wait until the gate opens again, when the threshold window ends during the stall;
#   4. wrong PINs until the gate blocks again.
# Each of the three gate transitions has its own time limit. A transition that is not seen is recorded as "never",
# and the repeat is then not as predicted. V is inferred, because the held write keeps the others in the queue. A
# release that fails is tried again at each later step, without a failure count. The case fails once when the write
# is still not released R33_RELEASE_WAIT seconds after the due time or after the PIN loops, whichever is later.
: "${R33_RELEASE_WAIT:=60}"
r33() { # caller repeat ending
  local caller="$1" repeat="$2" ending="$3" start released=no retries=0 block1=never open2=never block2=never
  local last="" expected release_end
  case_start "R3.3 $caller $ending #$repeat" && case_prepare "$caller" 0 0 "WRITE 0 HoldBeforeCommit Normal" \
    && case_open "$caller" || return 1
  r007_submit "$WRONG_PIN"; case_write_held 0 || return 1
  start=$SECONDS
  # The release runs in a subshell, so a failed try does not count; the case counts the final failure once.
  release_due() {
    if [ "$ending" = 40s ] && [ "$released" = no ] && [ $(( SECONDS - start )) -ge 40 ]; then
      if ( r007_release "$R007_CASE_PID" WRITE 0 ) >/dev/null; then released=yes; else retries=$((retries + 1)); fi
    fi
  }
  # Submits wrong PINs until the dump before a submission shows a blocked gate, or until LIMIT seconds of the case.
  submit_until_blocked() { # limit-s
    while [ $(( SECONDS - start )) -le "$1" ]; do
      r007_submit "$WRONG_PIN"; release_due
      [ "$R007_SUBMIT_STATE" != blocked ] || return 0
    done
    return 1
  }
  if submit_until_blocked 120; then
    block1=$(( SECONDS - start ))
    reopen "$caller" && screen_cycle || return 1
    while [ $(( SECONDS - start )) -le 240 ]; do
      if r007_in_list "$(gate_now)" "open incorrect"; then open2=$(( SECONDS - start )); break; fi
      release_due; sleep 1
    done
  fi
  if [ "$open2" != never ] && submit_until_blocked 300; then block2=$(( SECONDS - start )); fi
  release_end=$(( (SECONDS > start + 40 ? SECONDS : start + 40) + R33_RELEASE_WAIT ))
  while [ "$ending" = 40s ] && [ "$released" = no ]; do
    [ "$SECONDS" -lt "$release_end" ] \
      || { fail "R3.3: write 0 of process $R007_CASE_PID could not be released ($retries tries)"; return 1; }
    release_due; sleep 1
  done
  sleep 3
  r007_capture_log || { fail "R3.3: logcat could not be read"; return 1; }
  last="$(grep -E "R007Fault: pid=$R007_CASE_PID .*op=WRITE index=[0-9]+ phase=BEGIN" <<< "$R007_CAPTURE" | tail -1 \
    | sed -n -E 's/.* count=([0-9]+) until=(-?[0-9]+).*/\1,\2/p')"
  case_kill_inspect || return 1
  expected="0,0"; [ "$ending" = never ] || expected="$last"
  r007_mark R3.3 "$caller" "$repeat" \
    "$(yn '[ "$block1" != never ] && [ "$open2" != never ] && [ "$block2" != never ] &&
      [ "$R007_PAIR" = "$expected" ]')" \
    "$(yn '[ "$ending" = 40s ] && [ "$block2" != never ] && [ "$R007_PAIR" = "$expected" ]')" \
    fixture=Z script="WRITE_0_HoldBeforeCommit_Normal" ending="$ending" pid="$R007_CASE_PID" \
    first_block_s="$block1" open_again_s="$open2" second_block_s="$block2" v_inferred="$R007_V_INFERRED" \
    v_label=inferred submissions="$R007_SUBMISSIONS" screen_cycle="${R007_SCREEN_CYCLE:-none}" \
    release_retries="$retries" last_write_pair="${last:-none}" pair_after="$R007_PAIR"
}

# R3.4: the write commits and then reports false: from Z, from C4, and for a reset from C4. The gate of the process is
# degraded (blocked after a failure, unlocked after the reset), and after the kill the store holds the new pair; from
# C4 the recorded lock blocks after the restart.
r34() { # caller repeat kind
  local caller="$1" repeat="$2" kind="$3" count=0 pin="$WRONG_PIN" begin gate after gate_ok after_ok
  [ "$kind" = Z ] || count=4
  [ "$kind" != reset ] || pin="$PIN"
  case_start "R3.4 $caller $kind #$repeat" && case_prepare "$caller" "$count" 0 "WRITE 0 CommitThenReportFalse" \
    && case_open "$caller" || return 1
  r007_submit "$pin"; case_write_returned 0 || return 1
  sleep 1
  if [ "$kind" = reset ]; then gate="unlocked=$(yn 'unlocked_now "$caller"')"; else gate="$(gate_now)"; fi
  r007_capture_log || { fail "R3.4: logcat could not be read"; return 1; }
  begin="$(r007_begin_pair "$R007_CAPTURE" "$R007_CASE_PID" 0)"
  case_kill_inspect || return 1
  after="$(gate_after_restart "$caller")"
  gate_ok='[ "$gate" = blocked ]'; after_ok='[ "$after" = open ]'
  [ "$kind" != reset ] || gate_ok='[ "$gate" = unlocked=yes ]'
  [ "$kind" != C4 ] || after_ok='[ "$after" = blocked ]'
  r007_mark R3.4 "$caller" "$repeat" \
    "$(yn '[ -n "$begin" ] && [ "$R007_PAIR" = "$begin" ] && '"$gate_ok"' && '"$after_ok")" \
    "$(yn '[ "$R007_PAIR" = "$begin" ] && '"$gate_ok"' && '"$after_ok")" \
    fixture="$([ "$kind" = Z ] && echo Z || echo C4)" \
    script=WRITE_0_CommitThenReportFalse kind="$kind" pid="$R007_CASE_PID" gate_in_process="$gate" \
    write_pair="$begin" pair_after="$R007_PAIR" gate_after_restart="$after"
}

# The cases that a case selection (-k) can name. R3.1a includes its control. On Android 11 (API 30), SELinux denies
# the run-as kill of R3.1a and of the R3.1c loop, so that a run there can leave out these two cases.
SEGMENT_CASES="R3.1a R3.1b R3.1held R3.1d R3.1c R3.2 R3.3 R3.4"

segment_run() {
  local caller repeat released n10 n3
  n10="$(reps 10)"; n3="$(reps 3)"
  for caller in $R007_CALLERS; do
    if case_on R3.1a; then r31a "$caller"; fi
    if case_on R3.1b; then for (( repeat=1; repeat<=n10; repeat++ )); do r31b "$caller" "$repeat"; done; fi
    if case_on R3.1held; then for (( repeat=1; repeat<=n10; repeat++ )); do r31held "$caller" "$repeat"; done; fi
    if case_on R3.1d; then for (( repeat=1; repeat<=n10; repeat++ )); do r31d "$caller" "$repeat"; done; fi
    if case_on R3.1c; then r31c "$caller"; fi
    if case_on R3.2; then for released in 0 1 2 3 4; do r32 "$caller" "$released"; done; fi
    if case_on R3.3; then
      for (( repeat=1; repeat<=n3; repeat++ )); do r33 "$caller" "$repeat" 40s; r33 "$caller" "$repeat" never; done
    fi
    if case_on R3.4; then
      for (( repeat=1; repeat<=n3; repeat++ )); do
        r34 "$caller" "$repeat" Z; r34 "$caller" "$repeat" C4; r34 "$caller" "$repeat" reset
      done
    fi
  done
}
