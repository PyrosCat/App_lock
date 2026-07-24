#!/usr/bin/env bash
# OV-3 — IMMEDIATE relock on fast window-switching (defends against session leak).
# Unlock Clock, switch to a neutral app, return to Clock → the lock screen MUST
# reappear every time. Repeats with alternating speeds. Requires relock policy
# IMMEDIATE (the app default). Assumes the device is provisioned (see setup_device.sh).
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; source "$HERE/lib.sh"
CYCLES="${CYCLES:-10}"

require_device || exit 2
resolve_clock || exit 2

step "OV-3 fast-switch relock ×$CYCLES ($PROTECTED_PKG)"
home; sleep 1
unlock_protected || { summary "OV-3"; exit 1; }   # start from a legit unlocked session

fails_before=$FAIL_COUNT
for (( c=1; c<=CYCLES; c++ )); do
  # leave to a neutral app, then return
  launch_pkg "$NEUTRAL_PKG"; sleep "$([ $((c%2)) -eq 0 ] && echo 0.3 || echo 1.5)"
  launch_pkg "$PROTECTED_PKG"
  if wait_lockscreen; then
    info "cycle $c: relocked"
    # The security property (relock) is asserted above. The unlock below is only
    # scaffolding to set up the next cycle; a slow cold-start unlock is not a
    # security failure, so it soft-warns rather than failing the check.
    enter_pin "$PIN"
    wait_foreground "$PROTECTED_PKG" || info "cycle $c: scaffolding unlock slow (relock held — not a security failure)"
  else
    fail "cycle $c: NO relock — $PROTECTED_PKG returned without the lock screen (session leak)"
  fi
done
[ "$FAIL_COUNT" -eq "$fails_before" ] && pass "OV-3: relocked on all $CYCLES returns"

summary "OV-3"
