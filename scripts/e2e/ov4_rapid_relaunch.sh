#!/usr/bin/env bash
# OV-4 — rapid-relaunch gating (defends against the F4 fast-relaunch bypass).
# Fire `am start` at the protected app N times in quick succession; afterwards the
# lock screen MUST be up and the protected app's content MUST NOT be foreground.
# This is the exact shape of the Phase-3 F4 defect (content visible, 0 lock screens).
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; source "$HERE/lib.sh"
BURST="${BURST:-5}"

require_device || exit 2
resolve_clock || exit 2
comp="$(sh_ cmd package resolve-activity --brief -c android.intent.category.LAUNCHER "$PROTECTED_PKG" | tail -1)"

step "OV-4 rapid relaunch ×$BURST ($PROTECTED_PKG)"
home; sleep 1

for (( i=1; i<=BURST; i++ )); do sh_ am start -n "$comp" >/dev/null; sleep 0.2; done

if wait_lockscreen 6; then
  if foreground_is "$PROTECTED_PKG"; then
    fail "OV-4: lock screen present but $PROTECTED_PKG also foreground — inconsistent"
  else
    pass "OV-4: lock screen up after $BURST rapid launches, protected content not exposed"
  fi
else
  fail "OV-4: NO lock screen after $BURST rapid launches (top=$(top_component)) — F4-class bypass"
fi
enter_pin "$PIN" >/dev/null 2>&1; home   # leave clean

summary "OV-4"
