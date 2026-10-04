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

# Taps the "Use PIN" button of the biometric prompt, at the middle of its text (_digit_xy of lib.sh). A dump can come
# back without the prompt, so it looks up to 3 times. When the button stays missing, the evidence file gets the texts
# of the last dump.
tap_use_pin() {
  local xml xy try
  for (( try=1; try<=3; try++ )); do
    xml="$(ui_xml)"; xy="$(_digit_xy "$xml" "$R007_UI_BIOMETRIC")"
    if [ -n "$xy" ]; then sh_ input tap $xy; return; fi
    sleep 1
  done
  printf '## use-pin-missing %s\n%s\n' "$R007_CASE_LABEL" \
    "$(grep -oE 'text="[^"]+"' <<< "$xml" | tr '\n' ' ')" >> "$R007_LOG_OUT"
  fail "$R007_CASE_LABEL: the Use PIN button was not found"; return 1
}

# The "Not recognized" message of the prompt shows for about 2 s, and one UI dump takes 2 to 5 s on the Moto G, so
# dumps can miss it. The accessibility event stream does not carry the message either. The biometric service logs
# each rejected attempt with the owner of the prompt, and the line stays in the log (Moto G, 2026-10-04):
# "Biometrics/AuthenticationClient: onAuthenticated(false), ID:0, Owner: com.applock, isBP: true, ...".
R007_BIO_REJECTED="Biometrics/AuthenticationClient: onAuthenticated\(false\).* Owner: $APP_ID[, ]"

# Prints the log lines of the rejected biometric attempts of the app, or nothing. Returns 1 when logcat cannot be read.
# The log can hold NUL bytes, which a command substitution would warn about.
bio_rejected_lines() {
  local out
  out="$(r007_logcat -d -v epoch | tr -d '\000')" || return 1
  grep -E -- "$R007_BIO_REJECTED" <<< "$out" || :
}

# Prints the number of WRITE BEGIN lines of the case process.
case_writes() {
  r007_capture_log || { printf unknown; return 0; }
  r007_v_exact "$R007_CAPTURE" "$R007_CASE_PID"
}

# X11a: a finger that is not enrolled, from C4. The prompt stays, and nothing is written. The log line of the rejected
# attempt shows the mismatch (case_start cleared the log). The evidence file keeps the lines.
x11a() { # repeat
  local repeat="$1" writes rejected
  case_start "X11a L #$repeat" && case_prepare L 4 0 && case_open L "biometric" || return 1
  operator_step "touch the fingerprint sensor once with a finger that is not enrolled" \
    '[ -n "$(bio_rejected_lines)" ]' || return 1
  sleep 2; writes="$(case_writes)"; rejected="$(bio_rejected_lines)"
  printf '## bio-rejected %s\n%s\n' "$R007_CASE_LABEL" "$(cut -c1-300 <<< "$rejected")" >> "$R007_LOG_OUT"
  tap_use_pin || return 1
  case_kill_inspect || return 1
  r007_mark X11a L "$repeat" "$(yn '[ "$writes" = 0 ] && [ "$R007_PAIR" = 4,0 ]')" "$(yn '[ "$R007_PAIR" = 4,0 ]')" \
    fixture=C4 script=none pid="$R007_CASE_PID" rejected_attempts="$(grep -c . <<< "$rejected")" writes="$writes" \
    pair_after="$R007_PAIR"
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

# The cases that a case selection (-k) can name.
SEGMENT_CASES="X11a X11b X11c X11d X11e H04-biometric"

segment_run() {
  local repeat n3
  caller_on L || { fail "the biometric segment needs the legacy caller (-c L or the default)"; return 1; }
  n3="$(reps 3)"
  if case_on X11a; then for (( repeat=1; repeat<=n3; repeat++ )); do x11a "$repeat"; done; fi
  if case_on X11b; then x11b; fi
  if case_on X11d; then for (( repeat=1; repeat<=n3; repeat++ )); do x11d "$repeat"; done; fi
  if case_on X11e; then x11e; fi
  if case_on H04-biometric; then for (( repeat=1; repeat<=n3; repeat++ )); do h04_biometric "$repeat"; done; fi
  if case_on X11c; then x11c; fi
}
