#!/usr/bin/env bash
# OV-3 — IMMEDIATE relock on fast window-switching (defends against session leak).
# Unlock Clock, switch to a neutral app, return to Clock → the overlay lock surface
# MUST reappear every time. Repeats with alternating speeds. Requires relock policy
# IMMEDIATE (the app default) + PIN unlock, so this is a PROD-engine check (the spike
# has no PIN/relock); validated at WP2. Assumes the device is provisioned (setup_device.sh).
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; source "$HERE/lib.sh"
CYCLES="${CYCLES:-10}"
[ "$LOCK_ENGINE" = spike ] && { step "OV-3 fast-switch relock"; info "SKIPPED (exit 3, unsupported) — prod-path (PIN + IMMEDIATE relock); the spike has neither. Runs under LOCK_ENGINE=prod at WP2."; exit 3; }

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
    fail "cycle $c: NO relock — $PROTECTED_PKG returned without the overlay lock surface (session leak)"
  fi
done
[ "$FAIL_COUNT" -eq "$fails_before" ] && pass "OV-3: relocked on all $CYCLES returns"

summary "OV-3"
