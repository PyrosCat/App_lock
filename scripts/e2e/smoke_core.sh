#!/usr/bin/env bash
# smoke_core — the core lock/unlock path most likely to regress under the M1
# Hilt/package refactors: launch a protected app → lock screen appears → PIN
# unlock → protected app surfaces → leave. (Vault import/export round-trip is a
# separate, heavier check: SAF/DocumentsUI automation is fragile and would cause
# false failures in a fast regression loop — kept manual, see README.)
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; source "$HERE/lib.sh"

require_device || exit 2
resolve_clock || exit 2

step "smoke_core: lock → PIN → unlock ($PROTECTED_PKG)"
home; sleep 1

launch_pkg "$PROTECTED_PKG"
if wait_lockscreen; then pass "lock screen shown on protected-app launch"
else fail "no lock screen on launching $PROTECTED_PKG (top=$(top_component))"; summary "smoke_core"; exit 1; fi

_screen_wh && enter_pin "$PIN"
if wait_foreground "$PROTECTED_PKG"; then pass "PIN unlock surfaced the protected app"
else fail "protected app not foreground after PIN (top=$(top_component))"; fi

# a wrong-then-right sanity: relaunch, one wrong PIN stays locked, correct unlocks
home; sleep 1; launch_pkg "$PROTECTED_PKG"; wait_lockscreen || true
_screen_wh && enter_pin "0000"; sleep 1
if is_lockscreen; then pass "wrong PIN kept the lock screen up"
else fail "wrong PIN did NOT keep the lock screen (top=$(top_component))"; fi
enter_pin "$PIN"; wait_foreground "$PROTECTED_PKG" && info "recovered with correct PIN" || true
home

summary "smoke_core"
