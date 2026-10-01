#!/usr/bin/env bash
# R-007 phase P2, segment "biometric": X11 and the biometric part of H04, on the legacy caller of the Moto G with the
# operator present (-o). The preflight turns biometric unlock on, so the lock screen shows the biometric prompt at each
# start. The harness asks the operator for each finger action and detects its result in the UI. Case (c), the system
# biometric lockout, runs last, because the sensor then stays locked for 30 s.

: "${R007_FINGER_WAIT:=120}"   # seconds to wait for the result of one operator finger action

# Prints the request, then waits until the shell CONDITION holds (R007_FINGER_WAIT seconds at most).
operator_step() { # request condition
  local start=$SECONDS
  info "ACTION: $1 (waiting up to $R007_FINGER_WAIT s)"
  until eval "$2"; do
    [ $(( SECONDS - start )) -lt "$R007_FINGER_WAIT" ] \
      || { fail "$R007_CASE_LABEL: no result within $R007_FINGER_WAIT s after: $1"; return 1; }
    sleep 1
  done
}

# Taps the "Use PIN" button of the biometric prompt, at the middle of its text (_digit_xy of lib.sh).
tap_use_pin() {
  local xy
  xy="$(_digit_xy "$(ui_xml)" "$R007_UI_BIOMETRIC")"
  [ -n "$xy" ] || { fail "$R007_CASE_LABEL: the Use PIN button was not found"; return 1; }
  sh_ input tap $xy
}

# Prints the number of WRITE BEGIN lines of the case process.
case_writes() {
  r007_capture_log || { printf unknown; return 0; }
  r007_v_exact "$R007_CAPTURE" "$R007_CASE_PID"
}

# X11a: a finger that is not enrolled, from C4. The prompt stays, and nothing is written.
x11a() { # repeat
  local repeat="$1" writes
  case_start "X11a L #$repeat" && case_prepare L 4 0 && case_open L "biometric" || return 1
  operator_step "touch the fingerprint sensor once with a finger that is not enrolled" \
    'grep -qi "not recognized" <<< "$(ui_xml)"' || return 1
  sleep 2; writes="$(case_writes)"
  tap_use_pin || return 1
  case_kill_inspect || return 1
  r007_mark X11a L "$repeat" "$(yn '[ "$writes" = 0 ] && [ "$R007_PAIR" = 4,0 ]')" "$(yn '[ "$R007_PAIR" = 4,0 ]')" \
    fixture=C4 script=none pid="$R007_CASE_PID" writes="$writes" pair_after="$R007_PAIR"
}

# X11b: the harness cancels the prompt with "Use PIN". The PIN pad shows, and nothing is written.
x11b() {
  local writes state
  case_start "X11b L" && case_prepare L 4 0 && case_open L "biometric" || return 1
  tap_use_pin || return 1
  sleep 2; state="$(gate_now)"; writes="$(case_writes)"
  case_kill_inspect || return 1
  r007_mark X11b L 1 "$(yn '[ "$state" = open ] && [ "$writes" = 0 ] && [ "$R007_PAIR" = 4,0 ]')" \
    "$(yn '[ "$R007_PAIR" = 4,0 ]')" fixture=C4 script=none pid="$R007_CASE_PID" gate_after_cancel="$state" \
    writes="$writes" pair_after="$R007_PAIR"
}

# X11c: repeated mismatches until the system biometric lockout closes the prompt. The PIN pad shows, and nothing is
# written.
x11c() {
  local writes state
  case_start "X11c L" && case_prepare L 4 0 && case_open L "biometric" || return 1
  operator_step "touch the sensor with a finger that is not enrolled, again and again, until the prompt closes" \
    'r007_in_list "$(gate_now)" "open incorrect"' || return 1
  sleep 2; state="$(gate_now)"; writes="$(case_writes)"
  case_kill_inspect || return 1
  r007_mark X11c L 1 "$(yn '[ "$writes" = 0 ] && [ "$R007_PAIR" = 4,0 ]')" "$(yn '[ "$R007_PAIR" = 4,0 ]')" \
    fixture=C4 script=none pid="$R007_CASE_PID" gate_after_lockout="$state" writes="$writes" pair_after="$R007_PAIR"
}

# X11d: an enrolled finger from C4 unlocks Clock, and the reset writes Z.
x11d() { # repeat
  local repeat="$1"
  case_start "X11d L #$repeat" && case_prepare L 4 0 && case_open L "biometric" || return 1
  operator_step "touch the fingerprint sensor with an enrolled finger" 'foreground_is "$R007_CLOCK"' || return 1
  case_write_returned 0 || return 1
  case_kill_inspect || return 1
  r007_mark X11d L "$repeat" "$(yn '[ "$R007_PAIR" = 0,0 ]')" "$(yn '[ "$R007_PAIR" = 0,0 ]')" fixture=C4 script=none \
    pid="$R007_CASE_PID" pair_after="$R007_PAIR"
}

# X11e: over (8, now + 10 min) with every read failing, the gate answers from empty memory, so the prompt shows. An
# enrolled finger unlocks Clock, and the reset erases the stored lock.
x11e() {
  local state
  case_start "X11e L" && case_prepare L 8 +600000 "READ * Throw" && case_open L "biometric open incorrect blocked" \
    || return 1
  state="$R007_GATE"
  if [ "$state" = biometric ]; then
    operator_step "touch the fingerprint sensor with an enrolled finger" 'foreground_is "$R007_CLOCK"' || return 1
    case_write_returned 0 || return 1
  fi
  r007_set_faults || return 1
  case_kill_inspect || return 1
  r007_mark X11e L 1 "$(yn '[ "$state" = biometric ] && [ "$R007_PAIR" = 0,0 ]')" \
    "$(yn '[ "$R007_PAIR" = "$R007_FIXTURE_PAIR" ]')" fixture="(8,now+10min)" script="READ_*_Throw" \
    pid="$R007_CASE_PID" gate_at_open="$state" pair_before="$R007_FIXTURE_PAIR" pair_after="$R007_PAIR"
}

# H04, biometric part: from C4, an enrolled finger with the reset write held for 5 s. Clock opens before the write
# returns.
h04_biometric() { # repeat
  local repeat="$1" opened returned
  case_start "H04-biometric L #$repeat" \
    && case_prepare L 4 0 "WRITE 0 HoldBeforeCommit Normal" && case_open L "biometric" \
    || return 1
  operator_step "touch the fingerprint sensor with an enrolled finger" 'foreground_is "$R007_CLOCK"' || return 1
  case_write_held 0 || return 1
  opened="$(yn 'foreground_is "$R007_CLOCK"')"
  r007_capture_log || { fail "H04-biometric: logcat could not be read"; return 1; }
  returned="$(yn 'r007_has "$R007_CAPTURE" "R007Fault: pid=$R007_CASE_PID .*op=WRITE index=0 phase=RETURNED"')"
  sleep 5
  r007_release "$R007_CASE_PID" WRITE 0 && case_write_returned 0 || return 1
  case_kill_inspect || return 1
  r007_mark H04-biometric L "$repeat" "$(yn '[ "$opened" = yes ] && [ "$returned" = no ] && [ "$R007_PAIR" = 0,0 ]')" \
    "$(yn '[ "$opened" = yes ] && [ "$R007_PAIR" = 0,0 ]')" fixture=C4 script="WRITE_0_HoldBeforeCommit_Normal" \
    pid="$R007_CASE_PID" opened_while_held="$opened" returned_before_release="$returned" pair_after="$R007_PAIR"
}

segment_run() {
  local repeat n3
  caller_on L || { fail "the biometric segment needs the legacy caller (-c L or the default)"; return 1; }
  n3="$(reps 3)"
  for (( repeat=1; repeat<=n3; repeat++ )); do x11a "$repeat"; done
  x11b
  for (( repeat=1; repeat<=n3; repeat++ )); do x11d "$repeat"; done
  x11e
  for (( repeat=1; repeat<=n3; repeat++ )); do h04_biometric "$repeat"; done
  x11c
}
