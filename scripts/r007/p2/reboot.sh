#!/usr/bin/env bash
# R-007 phase P2, segment "reboot": the reboot variants of H05, R2.1, R3.1 (held write), and R4.1a. Sourced by
# p2_device.sh, which defines the case helpers. The segment needs -o: on the Moto G the operator unlocks the phone
# after each reboot (11 reboots in a full run). The harness never enters a device credential.
#
# Before each reboot the detector grant is removed, so the boot does not start the detector. After the boot the
# harness checks that the wrapper logged no WRITE line, stops the app, inspects the store, and then opens the gate of
# the case caller (for L, after a new grant). The fault script in files/r007/ survives the reboot, so the script for
# the process after the boot is published before the reboot.

# Reboots after the action of the case and checks the store after the boot. Sets R007_PAIR and R007_BOOT_ID.
reboot_and_inspect() {
  local state writes insp
  state="$(r007_detector_state)" || { fail "$R007_CASE_LABEL: the detector state could not be read"; return 1; }
  [ "$state" != bound ] || r007_revoke_detector || return 1
  r007_save_log "$R007_CASE_LABEL (before the reboot)" || return 1
  r007_reboot || return 1
  r007_capture_log || { fail "$R007_CASE_LABEL: logcat could not be read after the boot"; return 1; }
  writes="$(grep -cE "R007Fault: .*op=WRITE " <<< "$R007_CAPTURE" || :)"
  [ "$writes" = 0 ] || fail "$R007_CASE_LABEL: the wrapper logged $writes WRITE lines after the boot"
  r007_stop_app || return 1
  insp="$(r007_inspect_checked)" || { fail "$R007_CASE_LABEL: the inspection failed: $(r007_plain "$insp")"; return 1; }
  R007_PAIR="$(r007_pair "$insp")"
}

# Prints the caller for reboot repeat R: S and L in turn when both are selected.
reboot_caller() { # repeat
  if caller_on S && caller_on L; then (( $1 % 2 )) && printf S || printf L; else printf '%s' "${R007_CALLERS// /}"; fi
}

# True when an H05 reboot result matches: the stored PAIR is the fixture pair FIXTURE, and each caller in CALLERS
# showed a gate that fits the stored deadline (OK_S for S, OK_L for L: yes or no, see reboot_gate). A caller that is
# not in CALLERS has no result.
h05_reboot_ok() { # pair fixture ok-s ok-l callers
  [ "$1" = "$2" ] && { ! r007_in_list S "$5" || [ "$3" = yes ]; } && { ! r007_in_list L "$5" || [ "$4" = yes ]; }
}

REBOOT_GATE=""; REBOOT_COUNTDOWN=""; REBOOT_DUMP=""; REBOOT_OK=""   # the results of the last reboot_gate

# Opens the gate of CALLER after the boot (for L, after a new grant) and checks it against the stored wall DEADLINE:
# one UI dump gives the state and the countdown, between two device times (r007_reload_ok). Sets REBOOT_GATE,
# REBOOT_COUNTDOWN, REBOOT_DUMP ("t0-t1"), and REBOOT_OK (yes or no). Stops the app afterwards.
reboot_gate() { # caller deadline
  local t0 t1 xml
  REBOOT_GATE=none; REBOOT_COUNTDOWN=""; REBOOT_DUMP=""; REBOOT_OK=no
  if [ "$1" = L ] && ! r007_grant_detector; then REBOOT_GATE=unknown; return 0; fi
  if r007_open_gate "$1" "open incorrect blocked"; then
    if t0="$(r007_device_wall)" && xml="$(ui_xml)" && t1="$(r007_device_wall)"; then
      REBOOT_GATE="$(r007_gate_state "$xml")"; REBOOT_COUNTDOWN="$(r007_countdown_s "$xml")"; REBOOT_DUMP="$t0-$t1"
      if r007_reload_ok "$REBOOT_GATE" "$REBOOT_COUNTDOWN" "$2" "$t0" "$t1"; then REBOOT_OK=yes; fi
    else
      fail "$R007_CASE_LABEL: the device time could not be read around the UI dump of caller $1"
    fi
  fi
  r007_stop_app
}

# H05 reboot: an active (8, now + 10 min) or an expired (8, expired) fixture survives a reboot. After the boot, the
# active lock blocks with the remaining time against the stored wall deadline, and the expired one stays expired.
# Each selected caller is checked with its own UI dump (reboot_gate). The objective is "no" only when the stored pair
# changed or a gate showed and did not fit; a gate that did not show gives "na".
h05_reboot() { # kind(active|expired)
  local kind="$1" deadline=+600000 now matches objective misfit=no ok_s="" ok_l="" fields=() caller
  [ "$kind" = active ] || deadline=-60000
  case_start "H05 reboot $kind" && case_prepare S 8 "$deadline" || return 1
  reboot_and_inspect || return 1
  now="$(r007_device_wall)" || { fail "H05 reboot: the device time could not be read"; return 1; }
  for caller in S L; do
    caller_on "$caller" || continue
    reboot_gate "$caller" "${R007_FIXTURE_PAIR#*,}"
    if [ "$caller" = S ]; then ok_s="$REBOOT_OK"; else ok_l="$REBOOT_OK"; fi
    [ "$REBOOT_OK" = yes ] || r007_in_list "$REBOOT_GATE" "none unknown" || misfit=yes
    fields+=("gate_${caller,,}=$REBOOT_GATE" "countdown_${caller,,}=${REBOOT_COUNTDOWN:-none}")
    fields+=("dump_wall_${caller,,}=${REBOOT_DUMP:-none}")
  done
  matches="$(yn 'h05_reboot_ok "$R007_PAIR" "$R007_FIXTURE_PAIR" "$ok_s" "$ok_l" "$R007_CALLERS"')"
  if [ "$matches" = yes ]; then objective=yes
  elif [ "$R007_PAIR" != "$R007_FIXTURE_PAIR" ] || [ "$misfit" = yes ]; then objective=no
  else objective=na; fi
  r007_mark H05-reboot "${R007_CALLERS// /}" 1 "$matches" "$objective" fixture="(8,$kind)" script=none \
    boot_id_after="$R007_BOOT_ID" pair_before="$R007_FIXTURE_PAIR" pair_after="$R007_PAIR" "${fields[@]}" \
    remaining_ms=$(( ${R007_FIXTURE_PAIR#*,} - now ))
}

# R2.1 reboot: from (8, expired), a wrong PIN whose write fails arms a 480 s degraded lock (count 9); then a reboot.
# After the boot the stored pair is (8, expired), and the gate opens at once.
r21_reboot() { # repeat
  local repeat="$1" caller gate_in after pred
  caller="$(reboot_caller "$repeat")"
  case_start "R2.1 reboot $caller #$repeat" && case_prepare "$caller" 8 -60000 "WRITE 0 ReturnFalseBeforeCommit" \
    && case_open "$caller" || return 1
  r007_submit "$WRONG_PIN"; case_write_returned 0 || return 1
  sleep 1; gate_in="$(gate_now)"
  r007_set_faults || return 1
  reboot_and_inspect || return 1
  after="$(gate_after_restart "$caller")"
  pred="$(yn '[ "$gate_in" = blocked ] && [ "$R007_PAIR" = "$R007_FIXTURE_PAIR" ] && [ "$after" = open ]')"
  r007_mark R2.1-reboot "$caller" "$repeat" "$pred" "$(r007_residual "$pred")" fixture="(8,expired)" \
    script=WRITE_0_ReturnFalseBeforeCommit boot_id_after="$R007_BOOT_ID" \
    gate_in_process="$gate_in" pair_after="$R007_PAIR" gate_after_boot="$after"
}

# R3.1 held, reboot: one wrong PIN with the write held before the commit, then a reboot instead of a kill. The stored
# pair stays old.
r31_reboot() { # repeat
  local repeat="$1" caller count=0 name=Z after pred
  caller="$(reboot_caller "$repeat")"
  (( repeat % 2 )) || { count=4; name=C4; }
  case_start "R3.1held reboot $caller #$repeat" && case_prepare "$caller" "$count" 0 "WRITE 0 HoldBeforeCommit Normal" \
    && case_open "$caller" || return 1
  r007_submit "$WRONG_PIN"; case_write_held 0 || return 1
  r007_set_faults || return 1
  reboot_and_inspect || return 1
  after="$(gate_after_restart "$caller")"
  pred="$(yn '[ "$R007_PAIR" = "$R007_FIXTURE_PAIR" ] && [ "$after" = open ]')"
  r007_mark R3.1held-reboot "$caller" "$repeat" "$pred" "$(r007_residual "$pred")" fixture="$name" \
    script="WRITE_0_HoldBeforeCommit_Normal" boot_id_after="$R007_BOOT_ID" pair_after="$R007_PAIR" \
    gate_after_boot="$after"
}

# R4.1a reboot: from (4,0), a correct PIN whose clear fails, then a reboot. After the boot the stale (4,0) stays, and
# the first wrong PIN locks for 30 s.
r41a_reboot() { # repeat
  local repeat="$1" caller state window pred
  caller="$(reboot_caller "$repeat")"
  case_start "R4.1a reboot $caller #$repeat" && case_prepare "$caller" 4 0 "WRITE 0 ReturnFalseBeforeCommit" \
    && case_open "$caller" || return 1
  r007_submit "$PIN"; case_write_returned 0 || return 1
  r007_set_faults || return 1
  reboot_and_inspect || return 1
  [ "$caller" = S ] || r007_grant_detector || return 1
  case_open "$caller" || return 1
  r007_submit "$WRONG_PIN"; case_write_returned 0 || return 1
  sleep 1; state="$(gate_now)"
  window="$(case_write_window 0)" || window=unknown
  r007_stop_app
  pred="$(yn '[ "$R007_PAIR" = 4,0 ] && [ "$state" = blocked ] && [ "$window" != unknown ] &&
    within "$window" 30000 1000')"
  r007_mark R4.1a-reboot "$caller" "$repeat" "$pred" "$(r007_residual "$pred")" fixture="(4,0)" \
    script=WRITE_0_ReturnFalseBeforeCommit boot_id_after="$R007_BOOT_ID" stale_pair="$R007_PAIR" \
    gate_after_first_wrong_pin="$state" window_ms="$window"
}

segment_run() {
  local repeat n3
  n3="$(reps 3)"
  h05_reboot active
  h05_reboot expired
  for (( repeat=1; repeat<=n3; repeat++ )); do r21_reboot "$repeat"; done
  for (( repeat=1; repeat<=n3; repeat++ )); do r31_reboot "$repeat"; done
  for (( repeat=1; repeat<=n3; repeat++ )); do r41a_reboot "$repeat"; done
}
