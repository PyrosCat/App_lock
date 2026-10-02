#!/usr/bin/env bash
# R-007 device harness, test plan phase P2: the functions of the device lanes, on top of lib_r007.sh.
#
# It adds these parts. The plan is docs/testing/M7_WP2_F2H_P2_DEVICE_PLAN.md.
# - the fixture writer (LockoutStoreFixture);
# - the legacy caller: the detector AppDetectionService, the protected Clock app, and LockScreenActivity;
# - the gate state from a UI dump, and the count of verifier entries (V);
# - the kill with the detector on, the kill during the PIN check, and the mid-commit kill;
# - reboots, the damaged store file, and the app setting for biometric unlock;
# - the case markers and the summary of the evidence.
#
# A function that changes a harness variable (a count, a pid, a pair) sets it directly and prints nothing, so that
# the caller runs it in the current shell. A command substitution would lose the variable and the failure count.

R007_P2_HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$R007_P2_HERE/lib_r007.sh"

R007_FIXTURE_TOOL="com.applock.r007.LockoutStoreFixture"
R007_DETECTOR="$APP_ID/com.applock.applocker.service.AppDetectionService"
R007_DETECTOR_LABEL="App Lock protection"   # the label of the bound detector in `dumpsys accessibility`
R007_STORE_BAK="$R007_STORE.bak"            # the backup file that SharedPreferences keeps during a commit
R007_APP_SETTINGS="shared_prefs/applock_settings.xml"
R007_APP_SETTINGS_COPY="$R007_CONTROL/app_settings.orig"
R007_SWEEP_SCRIPT="$R007_CONTROL/sweep.sh"
R007_DAMAGED_XML='<map><int'                # XML that does not parse (probe A of the 2026-09-25 Moto G report)
: "${R007_BIND_POLLS:=40}"    # the detector bind and unbind waits poll this many times, 0.25 s apart
: "${R007_GATE_WAIT:=20}"     # seconds to wait for a gate (a UI dump takes 2 to 5 s on the Moto G)
: "${R007_GATE_TRIES:=3}"     # launches that r007_open_gate makes before it fails
: "${R007_BOOT_WAIT:=240}"    # seconds to wait for a reboot to complete
: "${R007_REBOOT_SETTLE:=5}"  # seconds between `adb reboot` and the first state query
: "${R007_UNLOCK_WAIT:=300}"  # seconds to wait for the operator to unlock the device after a reboot
: "${R007_SWEEP_LIMIT:=30}"   # seconds after which the mid-commit loop stops without a kill

# UI text of both gates (res/values/strings.xml).
R007_UI_BLOCKED="Try again in"
R007_UI_INCORRECT="Incorrect PIN"
R007_UI_OPEN="Enter your PIN"
R007_UI_BIOMETRIC="Use PIN"                 # the negative button of the biometric prompt
R007_UI_ANR="App Lock isn't responding"     # the ANR dialog of the app (app_name), not of another app
R007_UI_APP_LIST='content-desc="Open vault"'

# Strips the color codes of lib.sh from TEXT and joins its lines, for a failure message.
r007_plain() { sed 's/\x1b\[[0-9;]*m//g' <<< "$1" | tr '\n' ' ' | sed 's/  */ /g; s/ $//'; }

# Prints the device wall clock in ms (13 digits). Fails, and prints nothing, for any other answer: an empty answer in
# shell arithmetic would stop the whole run.
r007_device_wall() {
  local now
  now="$(sh_ date +%s%3N)" && [[ "$now" =~ ^[0-9]{13}$ ]] || return 1
  printf '%s' "$now"
}

# Prints the stored pair of an inspection as "count,until", or "error:<exception class>" for a read error.
r007_pair() { # inspection
  local err
  err="$(r007_field "$1" r007_read_error)"
  if [ -n "$err" ]; then printf 'error:%s' "${err##*.}"
  else printf '%s,%s' "$(r007_field "$1" r007_count)" "$(r007_field "$1" r007_lockout_until)"; fi
}

# ---- fixture writer ----------------------------------------------------------------------------
# True when COUNT and DEADLINE are valid fixture arguments: COUNT is an integer from 0 to 999999999, and DEADLINE is
# an absolute wall time in ms (0 for no deadline) or an offset in ms from the device wall clock at the write (+N or
# -N).
r007_fixture_args_ok() { # count deadline
  [[ "${1:-}" =~ ^[0-9]{1,9}$ ]] && [[ "${2:-}" =~ ^([0-9]{1,15}|[+-][0-9]{1,12})$ ]]
}

R007_FIXTURE_PAIR=""   # "count,until" of the last confirmed fixture

# Writes the stored pair (COUNT, DEADLINE) with the fixture writer in a new process, then checks it with the
# inspector in another new process. `am instrument` stops the app first, so no manager writes at the same time. It
# fails when an argument is not valid, when commit() or the writer's markers do not confirm the write, when the graph
# touched the store in the writer process, or when the inspection reads another pair. Sets R007_FIXTURE_PAIR.
r007_fixture() { # count deadline
  local args raw out pid count written insp
  R007_FIXTURE_PAIR=""
  r007_fixture_args_ok "${1:-}" "${2:-}" || { fail "fixture: the arguments '${1:-}' '${2:-}' are not valid"; return 1; }
  args=(-e r007_count "$1")
  case "$2" in [+-]*) args+=(-e r007_until_offset "${2#+}") ;; *) args+=(-e r007_until "$2") ;; esac
  raw="$(sh_ am instrument -w -r -e r007 fixture "${args[@]}" -e class "$R007_FIXTURE_TOOL" \
    "$TEST_APP_ID/$R007_RUNNER")" || { fail "fixture: am instrument failed"; return 1; }
  out="$(r007_instrument_fields "$raw")" || { fail "fixture: the writer did not complete"; return 1; }
  [ -z "$(r007_field "$out" r007_error)" ] \
    || { fail "fixture: the writer refused the argument '$(r007_field "$out" r007_error)'"; return 1; }
  [ "$(r007_field "$out" r007_commit)" = true ] || { fail "fixture: commit() did not return true"; return 1; }
  pid="$(r007_field "$out" r007_pid)"; count="$(r007_field "$out" r007_count)"
  written="$(r007_field "$out" r007_lockout_until)"
  [[ "$pid" =~ ^[0-9]+$ ]] || { fail "fixture: the writer reported no process id"; return 1; }
  [ "$count" = "$1" ] || { fail "fixture: the writer wrote count $count, not $1"; return 1; }
  if [[ "$2" != [+-]* ]] && [ "$written" != "$2" ]; then
    fail "fixture: the writer wrote deadline $written"; return 1
  fi
  r007_capture_log || { fail "fixture: logcat could not be read, so the write is unverified"; return 1; }
  r007_has "$R007_CAPTURE" "R007Fixture: pid=$pid phase=FIXTURE_BEGIN( |$)" \
    && r007_has "$R007_CAPTURE" "R007Fixture: pid=$pid phase=FIXTURE_END .*r007_commit=true( |$)" \
    || { fail "fixture: no FIXTURE_BEGIN and FIXTURE_END markers for the writer process $pid"; return 1; }
  ! r007_has "$R007_CAPTURE" "R007Fault: pid=$pid " \
    || { fail "fixture: the graph touched the store in the writer process $pid"; return 1; }
  r007_wait_absent || { fail "fixture: the writer process did not end"; return 1; }
  insp="$(r007_inspect_checked)" || { fail "fixture: the inspection failed: $(r007_plain "$insp")"; return 1; }
  [ "$(r007_pair "$insp")" = "$count,$written" ] \
    || { fail "fixture: the inspection read $(r007_pair "$insp"), not $count,$written"; return 1; }
  R007_FIXTURE_PAIR="$count,$written"
}

# ---- detector (legacy caller) ------------------------------------------------------------------
# In the baseline app, only the accessibility detector (AppDetectionService) starts LockScreenActivity, and the
# activity is not exported. So the legacy-caller cases grant the detector with adb. The grant is a device setting of
# the run: the settings record holds the original values, and the run restores them. The app does not change.
R007_A11Y_OFF_SERVICES=""   # the list of enabled services without the detector ("null" when empty)
R007_A11Y_OFF_ENABLED=""    # the recorded accessibility_enabled value

# Prints the colon-separated service LIST without the detector, in its full or its short component form (case does
# not matter). Prints "null" when no service stays.
r007_services_without() { # list
  local s out="" short="${R007_DETECTOR/\/$APP_ID./\/.}" parts=()
  [ "$1" = null ] || IFS=: read -ra parts <<< "$1"
  for s in "${parts[@]}"; do
    [ -n "$s" ] || continue
    [ "${s,,}" = "${R007_DETECTOR,,}" ] || [ "${s,,}" = "${short,,}" ] && continue
    out+="${out:+:}$s"
  done
  printf '%s' "${out:-null}"
}

# Sets the values that r007_revoke_detector writes, from the settings record on the device: the recorded service list
# without the detector, and the recorded accessibility_enabled. Call it after r007_settings_apply.
r007_detector_init() {
  local record services
  record="$(sh_ cat "$R007_SETTINGS_RECORD")" && r007_settings_record_ok "$record" \
    || { fail "the settings record could not be read, so the detector values are unknown"; return 1; }
  services="$(sed -n 's/^secure:enabled_accessibility_services=//p' <<< "$record")"
  R007_A11Y_OFF_SERVICES="$(r007_services_without "$services")"
  R007_A11Y_OFF_ENABLED="$(sed -n 's/^secure:accessibility_enabled=//p' <<< "$record")"
}

# Prints "bound" or "unbound", from the first "Bound services" line of `dumpsys accessibility`. Fails when the query
# fails or has no such line, so an unknown state is never taken for either answer.
r007_detector_state() {
  local out line
  out="$(sh_ dumpsys accessibility)" || return 1
  line="$(grep -m1 -E '^ *Bound services:' <<< "$out")" || return 1
  if grep -qF "label=$R007_DETECTOR_LABEL" <<< "$line"; then printf bound; else printf unbound; fi
}

# Waits until the detector state is STATE (bound or unbound).
r007_wait_detector() { # state
  local i state
  for (( i=0; i<R007_BIND_POLLS; i++ )); do
    state="$(r007_detector_state)" || state="unknown"
    [ "$state" = "$1" ] && return 0
    sleep 0.25
  done
  return 1
}

# Writes the grant of the detector: the recorded services without the detector, then the detector, and
# accessibility_enabled 1. An identical `settings put` does not bind the service again after a force-stop, so the list
# is deleted first.
r007_grant_write() {
  local value="$R007_DETECTOR"
  [ -n "$R007_A11Y_OFF_SERVICES" ] || { fail "the detector grant needs r007_detector_init"; return 1; }
  [ "$R007_A11Y_OFF_SERVICES" = null ] || value="$R007_A11Y_OFF_SERVICES:$R007_DETECTOR"
  sh_ settings delete secure enabled_accessibility_services >/dev/null \
    && r007_setting_write secure:enabled_accessibility_services "$value" \
    && r007_setting_write secure:accessibility_enabled 1 \
    || { fail "the detector grant could not be written"; return 1; }
}

# Grants the detector and waits until it is bound.
r007_grant_detector() {
  r007_grant_write || return 1
  r007_wait_detector bound || { fail "the detector did not bind after the grant"; return 1; }
}

# Writes the recorded values without the detector back and waits until the detector is not bound.
r007_revoke_detector() {
  [ -n "$R007_A11Y_OFF_SERVICES" ] || { fail "the detector revoke needs r007_detector_init"; return 1; }
  r007_setting_write secure:enabled_accessibility_services "$R007_A11Y_OFF_SERVICES" \
    && r007_setting_write secure:accessibility_enabled "$R007_A11Y_OFF_ENABLED" \
    || { fail "the detector grant could not be removed"; return 1; }
  r007_wait_detector unbound || { fail "the detector stayed bound after the grant was removed"; return 1; }
}

# Kills the app for an inspection. When the detector is bound, it removes the grant first and waits until the
# detector is not bound, so that the system does not start a new process that loads the store before the inspection.
# The app process must stay while the grant is removed, because its activity is in the foreground: a changed pid
# fails the kill.
r007_kill_for_inspection() {
  local before after state killed
  before="$(r007_pid)" || { fail "the kill: $APP_ID does not run as exactly one process"; return 1; }
  state="$(r007_detector_state)" || { fail "the kill: the detector state could not be read"; return 1; }
  if [ "$state" = bound ]; then
    r007_revoke_detector || return 1
    after="$(r007_pid)" || after="none"
    [ "$after" = "$before" ] \
      || { fail "the kill: the app process changed when the grant was removed ($before, then $after)"; return 1; }
  fi
  killed="$(r007_kill)" || { fail "the kill failed: $(r007_plain "$killed")"; return 1; }
}

# Waits until an app process with a pid other than OLD runs (the system restarts the app while the detector is
# bound).
r007_wait_new_process() { # old-pid [timeout-s=15]
  local i pid
  for (( i=0; i<${2:-15}*4; i++ )); do
    if pid="$(r007_pid)" && [ "$pid" != "$1" ]; then return 0; fi
    sleep 0.25
  done
  fail "no new app process started after the kill of $1"; return 1
}

# ---- gates -------------------------------------------------------------------------------------
R007_CLOCK=""   # the protected app of the legacy caller

# Resolves the Clock package: com.google.android.deskclock on the Moto G, com.android.deskclock on the AOSP images.
r007_resolve_clock() { resolve_clock && R007_CLOCK="$PROTECTED_PKG"; }

# Prints the gate state that the UI dump XML shows: "anr" (a dialog that says the app is not responding), "blocked"
# (a lockout countdown), "biometric" (the biometric prompt), "incorrect" (the PIN pad after a wrong PIN), "open" (the
# PIN pad), or "none". Only "open" and "incorrect" admit a PIN to the verifier.
r007_gate_state() { # ui-xml
  if grep -qF "$R007_UI_ANR" <<< "$1"; then printf anr
  elif grep -qF "$R007_UI_BLOCKED" <<< "$1"; then printf blocked
  elif grep -qF "text=\"$R007_UI_BIOMETRIC\"" <<< "$1"; then printf biometric
  elif grep -qF "$R007_UI_INCORRECT" <<< "$1"; then printf incorrect
  elif grep -qF "$R007_UI_OPEN" <<< "$1"; then printf open
  else printf none; fi
}

# True when the component TOP (package/activity) is the gate activity of CALLER: S is the self-gate (MainActivity),
# L is the legacy lock screen (LockScreenActivity).
r007_top_is() { # caller top
  case "$1" in
    S) [[ "$2" =~ ^${APP_ID//./\\.}/.*\.MainActivity$ ]] ;;
    L) [[ "$2" =~ ^${APP_ID//./\\.}/.*\.LockScreenActivity$ ]] ;;
    *) return 1 ;;
  esac
}

# Prints the countdown of a blocked gate in the UI dump XML in whole seconds ("Try again in 1:05" gives 65), or
# nothing when the dump shows no countdown.
r007_countdown_s() { # ui-xml
  local re="$R007_UI_BLOCKED ([0-9]+):([0-9]{2})"
  [[ "$1" =~ $re ]] || return 0
  printf '%s' $(( 10#${BASH_REMATCH[1]} * 60 + 10#${BASH_REMATCH[2]} ))
}

# True when the countdown SHOWN (whole seconds) fits the wall DEADLINE for a UI dump that ran between the device times
# T0 and T1 (ms). The gate shows the remaining time rounded up to whole seconds. That time comes from a poll that is up
# to 250 ms old. So the value lies between ceil((DEADLINE - T1) / 1000) and ceil((DEADLINE - T0 + 250) / 1000).
r007_countdown_ok() { # shown deadline t0 t1
  local low high arg
  for arg in "${1:-}" "${2:-}" "${3:-}" "${4:-}"; do [[ "$arg" =~ ^[0-9]+$ ]] || return 1; done
  low=$(( ($2 - $4 + 999) / 1000 )); high=$(( ($2 - $3 + 250 + 999) / 1000 ))
  [ "$low" -ge 0 ] || low=0
  [ "$1" -ge "$low" ] && [ "$1" -le "$high" ]
}

# True when a reloaded gate fits the stored wall DEADLINE (0 for none): the gate STATE and its COUNTDOWN (whole
# seconds, or empty) come from one UI dump that ran between the device times T0 and T1 (ms). The deadline ahead of the
# dump needs a blocked gate whose countdown fits (r007_countdown_ok). A deadline before the dump needs an open gate.
# A deadline inside the dump allows either.
r007_reload_ok() { # state countdown deadline t0 t1
  local arg blocked=no open=no
  for arg in "${3:-}" "${4:-}" "${5:-}"; do [[ "$arg" =~ ^[0-9]+$ ]] || return 1; done
  [ "$1" = blocked ] && r007_countdown_ok "$2" "$3" "$4" "$5" && blocked=yes
  r007_in_list "$1" "open incorrect" && open=yes
  if [ "$3" -gt "$5" ]; then [ "$blocked" = yes ]
  elif [ "$3" -le "$4" ]; then [ "$open" = yes ]
  else [ "$blocked" = yes ] || [ "$open" = yes ]; fi
}

R007_GATE=""   # the gate state that the last wait saw

# Waits until the gate activity of CALLER is on top and its state is one of ACCEPT. Sets R007_GATE. Fails quietly, so
# that a probe can use it. The limit is in seconds, because a UI dump takes seconds; it checks at least once.
r007_wait_gate() { # caller [accept="open incorrect blocked"] [timeout-s=R007_GATE_WAIT]
  local accept="${2:-open incorrect blocked}" end=$(( SECONDS + ${3:-$R007_GATE_WAIT} )) top
  R007_GATE="none"
  while :; do
    top="$(top_component)"
    if r007_top_is "$1" "$top"; then
      R007_GATE="$(r007_gate_state "$(ui_xml)")"
      r007_in_list "$R007_GATE" "$accept" && return 0
    fi
    [ "$SECONDS" -lt "$end" ] || return 1
    sleep 0.5
  done
}

R007_GATE_RETRIES=0   # the launches of the last r007_open_gate that did not show the gate

# Opens the gate of CALLER from the home screen: S starts MainActivity, and L starts Clock, which the detector covers
# with the lock screen. Waits for a state in ACCEPT and sets R007_GATE. It launches again, up to R007_GATE_TRIES times
# in all, when the gate does not show: on the Moto G, the lock screen closed once after a second start request of the
# detector during one launch of Clock. A launch reads the lockout state and writes nothing. The retries go to the
# evidence file.
r007_open_gate() { # caller [accept]
  local try
  R007_GATE_RETRIES=0
  for (( try=1; try<=R007_GATE_TRIES; try++ )); do
    home; sleep 1
    case "$1" in
      S) r007_launch_main || { fail "MainActivity could not be started"; return 1; } ;;
      L) launch_pkg "$R007_CLOCK" ;;
      *) fail "unknown caller '$1'"; return 1 ;;
    esac
    if r007_wait_gate "$1" "${2:-}"; then
      _screen_wh || { fail "the screen size could not be read"; return 1; }
      return 0
    fi
    R007_GATE_RETRIES=$try
    info "the gate of caller $1 did not show on launch $try (top: $(top_component), state: $R007_GATE)"
    printf '## gate-retry caller=%s launch=%s state=%s\n' "$1" "$try" "$R007_GATE" >> "$R007_LOG_OUT"
  done
  fail "the gate of caller $1 did not show after $R007_GATE_TRIES launches"; return 1
}

# ---- submissions and V -------------------------------------------------------------------------
R007_SUBMISSIONS=0   # PIN submissions of the case
R007_V_INFERRED=0    # submissions made while the gate admitted a PIN (the inferred V)
R007_SUBMIT_STATE="" # the gate state before the last submission
R007_LAST_KEY=""     # the tap position of the last digit of the last submission
R007_TAP_TIMES=""    # "start-end" device times (ms) of each tap of the last submission, separated by spaces

# Resets the submission counts at the start of a case.
r007_v_reset() { R007_SUBMISSIONS=0; R007_V_INFERRED=0; R007_SUBMIT_STATE=""; }

# Prints the tap position "x y" of the PIN key DIGIT: the middle of its text in the UI dump XML, otherwise the screen
# geometry of lib.sh (_pin_digit_xy). Needs SCREEN_W and SCREEN_H.
r007_key_xy() { # xml digit
  local xy
  xy="$(_digit_xy "$1" "$2")"
  [ -n "$xy" ] || xy="$(_pin_digit_xy "$2")" || return 1
  printf '%s' "$xy"
}

# Taps X Y and appends the device times before and after the tap to R007_TAP_TIMES. `input tap` returns only after
# the app has handled the tap. The PIN check runs on the main thread in the handler of the last digit, so the time of
# that tap covers the check (probe of 2026-09-30). The device command reports the exit status of `input tap`. A
# failed command, a failed tap, or an answer without times appends "?-?" and fails.
r007_tap() { # x y
  local out
  if out="$(sh_ "s=\$(date +%s%3N); input tap $1 $2; r=\$?; e=\$(date +%s%3N); echo \$s \$e \$r")" \
    && [[ "$out" =~ ^([0-9]+)\ ([0-9]+)\ 0$ ]]; then
    R007_TAP_TIMES+="${R007_TAP_TIMES:+ }${BASH_REMATCH[1]}-${BASH_REMATCH[2]}"; return 0
  fi
  R007_TAP_TIMES+="${R007_TAP_TIMES:+ }?-?"
  fail "the tap at $1 $2 failed or gave no device times (answer: '${out:-none}')"; return 1
}

# Counts a submission, and a verifier entry for the inferred V when the dump before it showed "open" or "incorrect"
# (R007_SUBMIT_STATE).
r007_count_submission() {
  R007_SUBMISSIONS=$((R007_SUBMISSIONS+1))
  case "$R007_SUBMIT_STATE" in open|incorrect) R007_V_INFERRED=$((R007_V_INFERRED+1)) ;; esac
}

# Taps all digits of PIN but the last, TAP_GAP apart, after one UI dump. Sets R007_SUBMIT_STATE, R007_TAP_TIMES, and
# R007_LAST_KEY (the position of the last digit). It counts nothing: the caller counts the submission after the last
# digit (r007_count_submission). A failed tap stops it.
r007_submit_prefix() { # pin
  local xml i
  R007_TAP_TIMES=""
  xml="$(ui_xml)"; R007_SUBMIT_STATE="$(r007_gate_state "$xml")"
  _screen_wh || { fail "the screen size could not be read"; return 1; }
  for (( i=0; i<${#1}-1; i++ )); do
    r007_tap $(r007_key_xy "$xml" "${1:$i:1}") || return 1
    sleep "$TAP_GAP"
  done
  R007_LAST_KEY="$(r007_key_xy "$xml" "${1: -1}")"
}

# Submits PIN once (see r007_submit_prefix) and counts it. A failed tap stops it before the count. Sets
# R007_SUBMIT_STATE and R007_TAP_TIMES.
r007_submit() { # pin
  r007_submit_prefix "$1" || return 1
  r007_tap $R007_LAST_KEY || return 1
  r007_count_submission; sleep "$TAP_GAP"
}

# Prints the kill window "low high" of a submission with the tap times TIMES (R007_TAP_TIMES) and the wall time WALL
# of its WRITE BEGIN line, in ms after the start of its last tap. LOW is the longest prefix tap: by then a tap has
# reached the app. HIGH is the time from the start of the last tap to WALL. Fails when a time is missing.
r007_kill_window() { # times wall
  local times=() t low=0 last
  read -ra times <<< "$1"
  [ "${#times[@]}" -ge 2 ] && [[ "${2:-}" =~ ^[0-9]+$ ]] || return 1
  for t in "${times[@]}"; do [[ "$t" =~ ^[0-9]+-[0-9]+$ ]] || return 1; done
  for t in "${times[@]:0:${#times[@]}-1}"; do
    (( ${t#*-} - ${t%-*} > low )) && low=$(( ${t#*-} - ${t%-*} ))
  done
  last="${times[${#times[@]}-1]}"
  printf '%s %s' "$low" $(( $2 - ${last%-*} ))
}

# Prints "state ticks" of the /proc stat line STAT of a thread: its state letter and its CPU time (utime plus stime,
# fields 14 and 15) in clock ticks. Fails for any other line.
r007_stat_ticks() { # stat-line
  local f=()
  [[ "$1" == *") "* ]] || return 1
  read -ra f <<< "${1##*) }"   # the fields after the command name, from field 3 (the state)
  [[ "${f[0]:-}" =~ ^[A-Za-z]$ ]] && [[ "${f[11]:-}" =~ ^[0-9]+$ ]] && [[ "${f[12]:-}" =~ ^[0-9]+$ ]] || return 1
  printf '%s %s' "${f[0]}" $(( f[11] + f[12] ))
}

R007_KILL_TIMES=""    # "sent ended": when the kill of r007_submit_kill was sent and ended, in ms after the tap start
R007_KILL_RC=""       # the exit status of the kill of r007_submit_kill
R007_KILL_TAP_RC=""   # the exit status of the last tap of r007_submit_kill
R007_KILL_MAIN=""     # "state ticks": the main thread at the kill, and its CPU ticks since the start of the last tap

# Submits PIN and sends SIGKILL to process PID DELAY ms (0 to 999) after the start of the last tap, while the app
# handles the tap. `input tap` returns only after the app has handled the tap, and the PIN check runs in that handler,
# so the tap runs in the background of the device command that kills. The command reads the stat line of the main
# thread (tid = pid) before the tap and just before the kill. The PIN check hashes on the main thread, so the CPU
# ticks between the two reads show whether the handler has started (probe of 2026-09-30: the check used 61 ticks, a
# digit tap at most 3). The command waits for the tap and reports the exit status of the kill and of the tap. It
# counts the submission after the command. Sets R007_KILL_TIMES, R007_KILL_RC, R007_KILL_TAP_RC, and R007_KILL_MAIN.
# Fails when the kill reports an error: the process then died by another cause, or not at all. Fails also unless the
# death of the process is confirmed.
r007_submit_kill() { # pin pid delay-ms
  local cmd out lines=() before at
  R007_KILL_TIMES=""; R007_KILL_RC=""; R007_KILL_TAP_RC=""; R007_KILL_MAIN=""
  [[ "${3:-}" =~ ^[0-9]{1,3}$ ]] || { fail "the kill delay '${3:-}' is not 0 to 999 ms"; return 1; }
  r007_submit_prefix "$1" || return 1
  cmd="s=\$(date +%s%3N); a=\$(cat /proc/$2/task/$2/stat); input tap $R007_LAST_KEY >/dev/null 2>&1 & t=\$!"
  cmd+="; sleep $(printf '0.%03d' "$((10#$3))"); b=\$(cat /proc/$2/task/$2/stat); k=\$(date +%s%3N)"
  cmd+="; run-as $APP_ID kill -9 $2; kr=\$?; e=\$(date +%s%3N); wait \$t; r=\$?"
  cmd+="; echo \$s \$k \$e \$kr \$r; echo \"\$a\"; echo \"\$b\""
  out="$(sh_ "$cmd")" || { fail "the tap and the kill of process $2 failed"; return 1; }
  r007_count_submission
  mapfile -t lines <<< "$out"
  if [[ "${lines[0]:-}" =~ ^([0-9]+)\ ([0-9]+)\ ([0-9]+)\ ([0-9]+)\ ([0-9]+)$ ]]; then
    R007_KILL_TIMES="$(( BASH_REMATCH[2] - BASH_REMATCH[1] )) $(( BASH_REMATCH[3] - BASH_REMATCH[1] ))"
    R007_KILL_RC="${BASH_REMATCH[4]}"; R007_KILL_TAP_RC="${BASH_REMATCH[5]}"
  fi
  if before="$(r007_stat_ticks "${lines[1]:-}")" && at="$(r007_stat_ticks "${lines[2]:-}")"; then
    R007_KILL_MAIN="${at% *} $(( ${at#* } - ${before#* } ))"
  fi
  [ -z "$R007_KILL_RC" ] || [ "$R007_KILL_RC" = 0 ] \
    || { fail "the kill of process $2 exited with status $R007_KILL_RC, so the harness did not kill it"; return 1; }
  r007_wait_dead "$2" || { fail "no confirmed death of process $2"; return 1; }
}

# Prints the number of OP lines of process PID in the log TEXT whose phase matches the extended regex PHASE (for
# example BEGIN, or [A-Z_]+ for any phase).
r007_phase_count() { # text pid op phase
  grep -cE "R007Fault: pid=$2 .*op=$3 index=[0-9]+ phase=$4( |$)" <<< "$1" || :
}

# Prints the elapsed time (ms) of the last OP line of process PID for INDEX with PHASE in the log TEXT, or nothing.
# With THREAD, only the lines of that thread count.
r007_phase_elapsed() { # text pid op index phase [thread]
  grep -E "R007Fault: pid=$2 ${6:+thread=$6 }.*op=$3 index=$4 phase=$5( |$)" <<< "$1" | tail -1 \
    | sed -n -E 's/.* elapsed=([0-9]+).*/\1/p'
}

# Prints the am_anr and am_kill events of process PID from the events buffer, one per line: "<event> <epoch s>
# <reason>". The reason of am_anr is its last field (the ANR subject, which can hold commas); the reason of am_kill
# is its fifth field. Returns 1 when logcat cannot be read. `logcat -c` does not clear the events buffer, so the
# buffer also holds older processes, and the pid selects the lines.
r007_proc_events() { # pid
  local out
  out="$(adbx logcat -b events -d -v epoch -s am_anr:I am_kill:I)" || return 1
  tr -d '\r' <<< "$out" | sed -n -E \
    -e "s/^ *([0-9]+\.[0-9]+) +[0-9]+ +[0-9]+ I am_anr *: \[[0-9]+,$1,[^,]*,[^,]*,(.*)\]$/am_anr \1 \2/p" \
    -e "s/^ *([0-9]+\.[0-9]+) +[0-9]+ +[0-9]+ I am_kill *: \[[0-9]+,$1,[^,]*,[^,]*,([^,]*),.*$/am_kill \1 \2/p"
}

# Prints the number of writes that process PID began in the log TEXT (its WRITE BEGIN lines). This is the exact V when
# no write was held or waiting in the queue at the end of the process (see r007_v_label).
r007_v_exact() { # text pid
  r007_phase_count "$1" "$2" WRITE BEGIN
}

# Prints "exact" when every held write of process PID in the log TEXT was released, otherwise "inferred": a write
# that stayed held can have more writes waiting behind it, and those never log a BEGIN line.
r007_v_label() { # text pid
  if [ "$(r007_phase_count "$1" "$2" WRITE HELD)" = "$(r007_phase_count "$1" "$2" WRITE RELEASED)" ]; then
    printf exact
  else printf inferred; fi
}

# Waits until write INDEX of process PID ends: its RETURNED line, or its THREW line for an injected throw (the
# Throw script ends a write without a RETURNED line). Prints the pid.
r007_wait_write_done() { # pid index [timeout-s=15]
  r007_wait_phase WRITE "$2" "(RETURNED|THREW)" "${3:-15}" "$1"
}

# Prints the wall time of the line that ended write INDEX of process PID in TEXT (RETURNED or THREW).
r007_write_done_wall() { # text pid index
  grep -E "R007Fault: pid=$2 .*op=WRITE index=$3 phase=(RETURNED|THREW)( |$)" <<< "$1" | tail -1 \
    | sed -n -E 's/.* wall=([0-9]+).*/\1/p'
}

# Prints the pair that the WRITE BEGIN line of process PID for write INDEX logged, as "count,until".
r007_begin_pair() { # text pid index
  grep -E "R007Fault: pid=$2 .*op=WRITE index=$3 phase=BEGIN( |$)" <<< "$1" | tail -1 \
    | sed -n -E 's/.* count=([0-9]+) until=(-?[0-9]+).*/\1,\2/p'
}

# Prints the wall time of the WRITE BEGIN line of process PID for write INDEX.
r007_begin_wall() { # text pid index
  grep -E "R007Fault: pid=$2 .*op=WRITE index=$3 phase=BEGIN( |$)" <<< "$1" | tail -1 \
    | sed -n -E 's/.* wall=([0-9]+).*/\1/p'
}

# Prints the lockout window of write INDEX of process PID in ms: the deadline of its WRITE BEGIN line minus the wall
# time of that line. Fails when the line is missing.
r007_write_window() { # text pid index
  local line
  line="$(grep -E "R007Fault: pid=$2 .*op=WRITE index=$3 phase=BEGIN( |$)" <<< "$1" | tail -1)"
  [[ "$line" =~ until=(-?[0-9]+).*wall=([0-9]+) ]] || return 1
  printf '%s' $(( BASH_REMATCH[1] - BASH_REMATCH[2] ))
}

# ---- files -------------------------------------------------------------------------------------
R007_INSPECTION=""   # the last inspection of r007_inspect_files
R007_FILES=""        # the file states around it: "store=<state> bak=<state> after=<state>"

# Runs the inspector on a store that the inspection may change: a load restores a backup file that a death inside
# the commit left, and a read of a damaged file writes new keysets. It records the states of the store and the backup
# file before the inspection and of the store after it, and does not treat a change as a failure. The other evidence
# checks of the inspection apply. Sets R007_INSPECTION and R007_FILES.
r007_inspect_files() {
  local store bak after fields
  R007_INSPECTION=""; R007_FILES=""
  r007_quiesce || return 1
  store="$(r007_file_state "$R007_STORE")" && bak="$(r007_file_state "$R007_STORE_BAK")" \
    || { fail "the store files could not be queried before the inspection"; return 1; }
  fields="$(r007_inspect)" || { fail "the inspector did not complete"; return 1; }
  after="$(r007_file_state "$R007_STORE")" || { fail "the store could not be queried after the inspection"; return 1; }
  R007_FILES="store=$store bak=$bak after=$after"
  R007_INSPECTION="$(r007_inspect_evidence "$fields")" \
    || { fail "the inspection evidence failed: $(r007_plain "$R007_INSPECTION")"; R007_INSPECTION=""; return 1; }
}

# Writes XML that does not parse over the store (case X16). Use it only while no app process runs, after
# r007_store_save.
r007_store_damage() {
  local expected actual
  r007_app_absent || { fail "r007_store_damage: $APP_ID runs"; return 1; }
  printf '%s' "$R007_DAMAGED_XML" | adbx exec-in "run-as $APP_ID sh -c 'cat > $R007_STORE'" \
    || { fail "r007_store_damage: the damaged store could not be written"; return 1; }
  expected="$(printf '%s' "$R007_DAMAGED_XML" | sha256sum)"; expected="${expected%% *}"
  actual="$(r007_store_hash)" || { fail "r007_store_damage: the damaged store could not be hashed"; return 1; }
  [ "$actual" = "$expected" ] || { fail "r007_store_damage: the store differs from the damaged text"; return 1; }
}

# ---- mid-commit kill (R3.1c) -------------------------------------------------------------------
# The loop runs in the app uid. It waits for the backup file of the store, waits DELAY ms, and sends SIGKILL to PID.
# It checks the clock only every 2000 polls, because a `date` process per poll would slow the loop more than the
# window of a commit. It prints "killed" or "timeout".
R007_SWEEP_BODY='pid=$1; delay=$2; limit=$3; bak=shared_prefs/applock_lockout.xml.bak; n=0
end=$(( $(date +%s) + limit ))
while [ ! -e $bak ]; do
  n=$((n+1))
  if [ $n -ge 2000 ]; then n=0; [ $(date +%s) -lt $end ] || { echo timeout; exit 0; }; fi
done
[ "$delay" -eq 0 ] || sleep "$(printf "0.%03d" "$delay")"
kill -9 "$pid" && echo killed'

# Writes the loop script into the control directory and checks it.
r007_sweep_install() {
  local actual
  printf '%s\n' "$R007_SWEEP_BODY" \
    | adbx exec-in "run-as $APP_ID sh -c 'mkdir -p $R007_CONTROL && cat > $R007_SWEEP_SCRIPT'" \
    || { fail "the mid-commit loop could not be written"; return 1; }
  actual="$(r007_run_as cat "$R007_SWEEP_SCRIPT")" && [ "$actual" = "$R007_SWEEP_BODY" ] \
    || { fail "the mid-commit loop on the device differs from the script"; return 1; }
}

R007_SWEEP_JOB=""; R007_SWEEP_OUT=""

# Starts the loop in the background for process PID with DELAY ms (0 to 999). It refuses when a backup file exists,
# because the loop would kill at once.
r007_sweep_start() { # pid delay-ms
  local bak
  [[ "$2" =~ ^[0-9]{1,3}$ ]] || { fail "the mid-commit delay '$2' is not 0 to 999 ms"; return 1; }
  bak="$(r007_file_state "$R007_STORE_BAK")" || { fail "the backup file could not be queried"; return 1; }
  [ "$bak" = absent ] || { fail "a backup file of the store exists before the mid-commit trial"; return 1; }
  R007_SWEEP_OUT="$(mktemp)"
  sh_ run-as "$APP_ID" sh "$R007_SWEEP_SCRIPT" "$1" "$2" "$R007_SWEEP_LIMIT" < /dev/null > "$R007_SWEEP_OUT" &
  R007_SWEEP_JOB=$!
}

R007_SWEEP_ANSWER=""

# Waits for the loop and sets R007_SWEEP_ANSWER to "killed" or "timeout".
r007_sweep_wait() {
  [ -n "$R007_SWEEP_JOB" ] || { fail "no mid-commit loop runs"; return 1; }
  wait "$R007_SWEEP_JOB"
  R007_SWEEP_ANSWER="$(tr -d '\r\n' < "$R007_SWEEP_OUT")"
  rm -f "$R007_SWEEP_OUT"; R007_SWEEP_JOB=""; R007_SWEEP_OUT=""
  r007_in_list "$R007_SWEEP_ANSWER" "killed timeout" \
    || { fail "the mid-commit loop answered '$R007_SWEEP_ANSWER'"; return 1; }
}

# Prints the class of a mid-commit trial, as in the table of the P2 device plan. The inputs are the wrapper lines of
# the dead process PID in TEXT and the backup-file state BAK after the death (a hash or "absent"). The inspected PAIR
# is compared with the OLD pair and with the NEW pair from the BEGIN line of the write. Any other combination is an
# "anomaly".
r007_sweep_class() { # text pid bak pair old new
  local begin=no result=no bak=present held=old
  r007_has "$1" "R007Fault: pid=$2 .*op=WRITE index=[0-9]+ phase=BEGIN( |$)" && begin=yes
  r007_has "$1" "R007Fault: pid=$2 .*op=WRITE index=[0-9]+ phase=REAL_RESULT( |$)" && result=yes
  [ "$3" = absent ] && bak=absent
  if [ "$4" = "$5" ]; then held=old; elif [ -n "$6" ] && [ "$4" = "$6" ]; then held=new; else held=other; fi
  case "$begin $result $bak $held" in
    "no no absent old") printf before-write ;;
    "yes no absent old") printf in-adapter ;;
    "yes no present old") printf platform-write ;;
    "yes no absent new") printf after-file-write ;;
    "yes yes absent new") printf after-return ;;
    *) printf anomaly ;;
  esac
}

# ---- reboots -----------------------------------------------------------------------------------
R007_BOOT_ID=""   # the boot id after the last reboot

# Reboots the device and waits until it has booted with a new boot id and is unlocked, with the screen on and the
# stay-awake setting in effect. The wait ends only on a new boot id: a phone that still shuts down after
# R007_REBOOT_SETTLE answers as a booted device with the old boot id. A phone needs its credential for the first
# unlock after a boot: the function prints a request and waits up to R007_UNLOCK_WAIT seconds for the operator. It
# never enters a device credential. Sets R007_BOOT_ID.
r007_reboot() {
  local old state end id="" booted=no
  R007_BOOT_ID=""
  old="$(r007_boot_id)" && [ -n "$old" ] || { fail "the boot id could not be read before the reboot"; return 1; }
  adbx reboot || { fail "adb reboot failed"; return 1; }
  sleep "$R007_REBOOT_SETTLE"
  end=$(( SECONDS + R007_BOOT_WAIT ))
  while :; do
    state="$(adbx get-state 2>/dev/null | tr -d '\r')" || state=""
    if [ "$state" = device ] && [ "$(sh_ getprop sys.boot_completed)" = 1 ]; then
      booted=yes; id="$(r007_boot_id)"
      [ -n "$id" ] && [ "$id" != "$old" ] && break
    fi
    if [ "$SECONDS" -ge "$end" ]; then
      if [ "$booted" = no ]; then fail "the device did not boot in $R007_BOOT_WAIT s"
      else fail "the boot id did not change in $R007_BOOT_WAIT s ($old, then ${id:-none})"; fi
      return 1
    fi
    sleep 2
  done
  R007_BOOT_ID="$id"
  sh_ input keyevent KEYCODE_WAKEUP >/dev/null
  r007_wait_unlock || { fail "the device stayed locked for $R007_UNLOCK_WAIT s after the reboot"; return 1; }
  r007_wake_screen
}

# Waits up to R007_UNLOCK_WAIT seconds for the operator to unlock the device, when it is locked. It prints the request
# and never enters a device credential. Fails when the device stays locked.
r007_wait_unlock() {
  local end=$(( SECONDS + R007_UNLOCK_WAIT ))
  r007_unlocked && return 0
  info "ACTION: unlock the device now (waiting up to $R007_UNLOCK_WAIT s). The harness never enters a credential."
  while [ "$SECONDS" -lt "$end" ]; do
    sleep 2
    r007_unlocked && return 0
  done
  return 1
}

# ---- app setting -------------------------------------------------------------------------------
R007_APP_SETTINGS_SAVED=""   # "absent", or the hash of the saved original, while a restore is pending

# Saves the original app settings file (or its absence) before the first change, and writes the "app_settings" marker
# with that state. It refuses while a saved state of an earlier run is pending, because the file then holds the
# settings of that run. Use it while no app process runs.
r007_app_settings_save() {
  local state copy pending
  r007_app_absent || { fail "the app settings: $APP_ID runs"; return 1; }
  pending="$(r007_pending_get app_settings)" || { fail "the app_settings marker could not be queried"; return 1; }
  [ "$pending" = absent ] \
    || { fail "the app settings of an earlier run are pending (run scripts/r007/restore_settings.sh)"; return 1; }
  state="$(r007_file_state "$R007_APP_SETTINGS")" || { fail "the app settings file could not be queried"; return 1; }
  if [ "$state" != absent ]; then
    r007_run_as sh -c "'mkdir -p $R007_CONTROL && cat $R007_APP_SETTINGS > $R007_APP_SETTINGS_COPY'" \
      || { fail "the app settings file could not be saved"; return 1; }
    copy="$(r007_file_state "$R007_APP_SETTINGS_COPY")" && [ "$copy" = "$state" ] \
      || { fail "the saved app settings differ from the original"; return 1; }
  fi
  # The marker says "no-file" for a missing original, because r007_pending_get prints "absent" for a missing marker.
  r007_pending_set app_settings "${state/#absent/no-file}" \
    || { fail "the app_settings marker could not be written"; return 1; }
  R007_APP_SETTINGS_SAVED="$state"
  printf '## app-settings-before\n%s=%s\n' "$R007_APP_SETTINGS" "$state" >> "$R007_LOG_OUT" \
    || { fail "the app settings state could not be saved in the evidence file"; return 1; }
}

# Writes the app settings file with biometric unlock set to VALUE (true or false). The file holds only this key, so the
# other app settings take their defaults (relock immediately). Use it while no app process runs, after
# r007_app_settings_save.
r007_app_settings_biometric() { # true|false
  local xml expected actual
  case "$1" in true|false) ;; *) fail "the biometric setting '$1' is not true or false"; return 1 ;; esac
  [ -n "$R007_APP_SETTINGS_SAVED" ] || { fail "the app settings were not saved before the change"; return 1; }
  r007_app_absent || { fail "the app settings: $APP_ID runs"; return 1; }
  xml="<?xml version='1.0' encoding='utf-8' standalone='yes' ?>"$'\n'"<map>"$'\n'
  xml+="    <boolean name=\"biometric_unlock\" value=\"$1\" />"$'\n'"</map>"$'\n'
  printf '%s' "$xml" | adbx exec-in "run-as $APP_ID sh -c 'cat > $R007_APP_SETTINGS'" \
    || { fail "the app settings file could not be written"; return 1; }
  expected="$(printf '%s' "$xml" | sha256sum)"; expected="${expected%% *}"
  actual="$(r007_file_state "$R007_APP_SETTINGS")" && [ "$actual" = "$expected" ] \
    || { fail "the app settings file differs from the written text"; return 1; }
}

# Stops the app and writes the saved original app settings back (or removes the file when there was none), once,
# when a restore is pending, and removes the marker. A failed restore keeps the copy, the marker, and the pending
# state.
r007_app_settings_restore() {
  local state
  [ -n "$R007_APP_SETTINGS_SAVED" ] || return 0
  r007_stop_app || return 1
  if [ "$R007_APP_SETTINGS_SAVED" = absent ]; then
    r007_run_as rm -f "$R007_APP_SETTINGS" || { fail "the app settings file could not be removed"; return 1; }
  else
    r007_run_as sh -c "'[ -f $R007_APP_SETTINGS_COPY ] && cat $R007_APP_SETTINGS_COPY > $R007_APP_SETTINGS'" \
      || { fail "the saved app settings could not be written back"; return 1; }
  fi
  state="$(r007_file_state "$R007_APP_SETTINGS")" && [ "$state" = "$R007_APP_SETTINGS_SAVED" ] \
    || { fail "the app settings file is not restored (state ${state:-unknown})"; return 1; }
  r007_pending_clear app_settings || { fail "the app_settings marker could not be removed"; return 1; }
  R007_APP_SETTINGS_SAVED=""; pass "the app settings file is restored"
}

# ---- recovery of an unfinished run -------------------------------------------------------------
# Undoes the app-data changes that an unfinished run left. With nothing to undo, it does not stop the app: a
# force-stop sets accessibility_enabled to 0, and no settings record may exist to restore it. Otherwise it stops the
# app first. It removes a fault script and the release files, because a script that an unfinished run left (a held
# read, for example) would act on the next start of the app. Then it undoes the changes that the markers name, in this
# order: the read-only preferences directory, the saved store copy, and the saved app settings file. The copies that
# the undo needs stay until their marker is gone. scripts/r007/restore_settings.sh calls it. A marker with a value that
# is not valid stays, and the recovery fails.
r007_recover_pending() {
  local faults pending value status=0 valid='^[0-9a-f]{64}$'
  faults="$(r007_file_state "$R007_CONTROL/faults")" || { fail "the fault script could not be queried"; return 1; }
  pending="$(r007_pending_list)" || { fail "the pending markers could not be queried"; return 1; }
  if [ "$faults" = absent ] && [ -z "$pending" ]; then
    info "no fault script and no pending app-data change"; return 0
  fi
  r007_stop_app || return 1
  if [ "$faults" != absent ]; then
    if r007_faults_remove; then pass "the fault script of an earlier run is removed"; else status=1; fi
  fi
  [ -n "$pending" ] || return "$status"
  info "pending app-data changes: $pending"
  if r007_in_list prefs_mode "$pending"; then
    R007_PREFS_OWNED=1
    if r007_prefs_writable; then pass "the preferences directory is writable again"; else status=1; fi
  fi
  if r007_in_list store "$pending"; then
    value="$(r007_pending_get store)"
    if [[ "$value" =~ $valid ]]; then R007_STORE_SAVED="$value"; r007_restore_pending || status=1
    else fail "the store marker holds no hash: '$value'"; status=1; fi
  fi
  if r007_in_list app_settings "$pending"; then
    value="$(r007_pending_get app_settings)"
    if [ "$value" = no-file ] || [[ "$value" =~ $valid ]]; then
      R007_APP_SETTINGS_SAVED="${value/#no-file/absent}"; r007_app_settings_restore || status=1
    else fail "the app_settings marker holds no state: '$value'"; status=1; fi
  fi
  return "$status"
}

# Undoes the app-data changes of a P2 run at its end, in this order: it writes a pending store copy (X16) back, writes
# the fixture Z when no store copy stays pending, restores the app settings file, and removes the control directory
# when nothing stays pending. The copy comes first, so that Z is the final store and not the (8, now + 10 min) fixture
# that X16 saved. Each step runs also when an earlier one fails.
r007_p2_cleanup() {
  local status=0
  r007_restore_pending || status=1
  if [ -z "$R007_STORE_SAVED" ]; then
    if r007_fixture 0 0; then pass "the store is back at Z"; else status=1; fi
  fi
  r007_app_settings_restore || status=1
  if [ -z "$R007_STORE_SAVED" ] && [ -z "$R007_APP_SETTINGS_SAVED" ]; then r007_remove_control || status=1; fi
  return "$status"
}

# ---- protected app -----------------------------------------------------------------------------
# Prints one word for each lock decision about Clock that the lock engine logged at or after the device time T0 (ms):
# "unprotected" for the reason "not protected", otherwise "protected". The engine logs each decision at debug level
# under the tag AppLockEngine. Fails when logcat cannot be read.
r007_clock_decisions() { # t0-ms
  local out
  out="$(adbx logcat -d -v epoch -s AppLockEngine:D)" || return 1
  tr -d '\r' <<< "$out" | awk -v t0="$1" -v prefix="AppLockEngine: $R007_CLOCK -> LockDecision(" '
    $1 * 1000 >= t0 && index($0, prefix) {
      if (index($0, "requiresAuthentication=false, reason=not protected)")) print "unprotected"; else print "protected"
    }'
}

R007_CLOCK_STATE=""   # the result of the last r007_clock_probe: protected, unprotected, or unknown

# Launches Clock once from the home screen and sets R007_CLOCK_STATE. "protected": the lock screen showed, or the lock
# engine decided that Clock is protected (on the Moto G, the lock screen closed once after a second start request).
# "unprotected": no lock screen, and every logged decision for Clock was "not protected". "unknown": no decision was
# logged, or the device time or the log could not be read. Each probe goes to the evidence file.
r007_clock_probe() { # label
  local t0 decisions=""
  R007_CLOCK_STATE=unknown; R007_GATE=none
  home; sleep 1
  if t0="$(r007_device_wall)"; then
    launch_pkg "$R007_CLOCK"
    if r007_wait_gate L "open incorrect blocked biometric"; then
      R007_CLOCK_STATE=protected
    elif decisions="$(r007_clock_decisions "$t0")"; then
      decisions="$(sort -u <<< "$decisions" | tr '\n' ' ')"; decisions="${decisions% }"
      if r007_in_list protected "$decisions"; then R007_CLOCK_STATE=protected
      elif [ "$decisions" = unprotected ]; then R007_CLOCK_STATE=unprotected; fi
    fi
  fi
  printf '## clock-probe %s gate=%s decisions=%s state=%s\n' "$1" "$R007_GATE" "${decisions:-none}" \
    "$R007_CLOCK_STATE" >> "$R007_LOG_OUT"
  home
}

# Makes sure that Clock is protected, with the detector bound. The function taps the switch of the Clock row in the
# app list only when R007_GATE_TRIES launches in a row were "unprotected" (see r007_clock_probe). A tap on a protected
# row removes the protection, so a "protected" or an "unknown" launch never leads to a tap. After the tap it checks
# by behaviour again, because the switch state in a UI dump did not follow the tap on the Moto G. The store must admit
# a PIN (write the fixture Z first).
r007_protect_clock() {
  local xml bounds y sw i try
  for (( try=1; try<=R007_GATE_TRIES; try++ )); do
    r007_clock_probe "launch=$try"
    case "$R007_CLOCK_STATE" in
      protected) info "Clock is protected (launch $try, lock screen: $R007_GATE)"; return 0 ;;
      unknown) fail "the protection of Clock is unknown after launch $try; check the app list by hand"; return 1 ;;
    esac
  done
  info "Clock is not protected in $R007_GATE_TRIES launches: the app list opens to protect it"
  r007_open_gate S "open incorrect" || return 1
  enter_pin "$PIN"; sleep 2
  ui_has "$R007_UI_APP_LIST" || { fail "the app list did not open after the self-gate"; return 1; }
  for (( i=0; i<6; i++ )); do
    sh_ input swipe $((SCREEN_W/2)) $((SCREEN_H/4)) $((SCREEN_W/2)) $((SCREEN_H*3/4)) 200
  done
  sleep 1
  for (( i=0; i<20; i++ )); do
    xml="$(ui_xml)"
    bounds="$(grep -oE "text=\"${R007_CLOCK//./\\.}\"[^>]*bounds=\"\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]\"" <<< "$xml" \
      | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]' | head -1)"
    [ -n "$bounds" ] && break
    sh_ input swipe $((SCREEN_W/2)) $((SCREEN_H*55/100)) $((SCREEN_W/2)) $((SCREEN_H*40/100)) 250; sleep 0.7
  done
  [ -n "$bounds" ] || { fail "the Clock row ($R007_CLOCK) was not found in the app list"; return 1; }
  y="$(sed -E 's/\[[0-9]+,([0-9]+)\]\[[0-9]+,([0-9]+)\]/\1 \2/' <<< "$bounds" | awk '{printf "%d", ($1+$2)/2}')"
  # The switch is the checkable node whose vertical range holds the middle of the package-name text.
  sw="$(grep -oE '<node [^>]*checkable="true"[^>]*>' <<< "$xml" \
    | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' \
    | sed -E 's/bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]"/\1 \2 \3 \4/' \
    | awk -v y="$y" '$2 <= y && y <= $4 { printf "%d %d", ($1+$3)/2, ($2+$4)/2; exit }')"
  [ -n "$sw" ] || { fail "the switch of the Clock row was not found"; return 1; }
  sh_ input tap $sw; sleep 2
  r007_clock_probe after-tap
  [ "$R007_CLOCK_STATE" = protected ] \
    || { fail "Clock is $R007_CLOCK_STATE after the switch was tapped (check the app list by hand)"; return 1; }
  pass "Clock is protected after the switch was tapped (lock screen: $R007_GATE)"
}

# ---- case markers and summary ------------------------------------------------------------------
R007_SUMMARY=()   # one "case caller predicted objective" line per case repeat

# Appends the marker line of one case repeat to the evidence file and adds its verdicts to the summary. FIELDS are
# key=value words, for example fixture, script, pids, pair_before, pair_after, v, and v_label. PREDICTED and OBJECTIVE
# are yes or no: "as predicted" (the baseline matches the prediction of the test plan) and "objective met" (the
# security objective of the case holds). OBJECTIVE is "na" when the repeat cannot show the objective: a measurement,
# a control without a stored lock, or a repeat whose evidence does not establish an unmet objective. A repeat that
# is not as predicted counts as a failure of the run. An objective that is not met is the expected result of a
# residual at baseline, so it is recorded and not counted. A boot id that cannot be read is an evidence failure: the
# marker then holds boot_id=unreadable, and the repeat counts as a failure.
r007_mark() { # case caller repeat predicted objective [field...]
  local line boot status=0
  r007_in_list "$4" "yes no" && r007_in_list "$5" "yes no na" \
    || { fail "$1: the verdicts '$4' '$5' are not valid"; return 1; }
  boot="$(r007_boot_id)" || boot=""
  line="## CASE $1 caller=$2 repeat=$3 boot_id=${boot:-unreadable} ${*:6} predicted=$4 objective=$5"
  printf '%s\n' "$line" >> "$R007_LOG_OUT" || { fail "$1: the case marker was not saved"; return 1; }
  R007_SUMMARY+=("$1 $2 $4 $5")
  [ -n "$boot" ] || { fail "$1 caller $2 repeat $3: the boot id could not be read for the case marker"; status=1; }
  [ "$4" = yes ] \
    || { fail "$1 caller $2 repeat $3: the baseline differs from the prediction (see its marker)"; status=1; }
  return "$status"
}

# Prints the objective verdict of a residual case from its PREDICTED verdict. The prediction of a residual case is the
# loss at baseline, so a repeat as predicted shows an unmet objective ("no"). Any other repeat does not establish an
# unmet objective, so it gives "na", and r007_mark counts it as a failure.
r007_residual() { # predicted
  if [ "$1" = yes ]; then printf no; else printf na; fi
}

# Prints the summary table: for each case and caller, in the order of their first marker, the number of repeats and
# the number of repeats with each verdict "yes". The objective counts only the repeats with a yes or no objective, and
# names the "na" repeats apart; an objective that is "na" in every repeat shows as "n/a".
r007_summary_table() {
  printf '## SUMMARY\n| Case | Caller | Repeats | As predicted | Objective met |\n|---|---|---|---|---|\n'
  printf '%s\n' "${R007_SUMMARY[@]}" | awk 'NF == 4 {
    key = $1 " " $2
    if (!(key in n)) order[++k] = key
    n[key]++; if ($3 == "yes") p[key]++; if ($4 == "yes") o[key]++; if ($4 == "na") na[key]++
  } END {
    for (i = 1; i <= k; i++) {
      split(order[i], part, " "); key = order[i]
      if (na[key] == n[key]) objective = "n/a"
      else objective = sprintf("%d of %d", o[key], n[key] - na[key]) (na[key] ? sprintf(", %d n/a", na[key]) : "")
      printf "| %s | %s | %d | %d of %d | %s |\n", part[1], part[2], n[key], p[key], n[key], objective
    }
  }'
}

# Appends the summary table to the evidence file.
r007_save_summary() {
  r007_summary_table >> "$R007_LOG_OUT" || { fail "the summary was not saved"; return 1; }
}
