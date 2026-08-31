#!/usr/bin/env bash
# smoke_core — the core lock/unlock path most likely to regress under a refactor:
# launch a protected app → overlay lock surface appears → PIN unlock → protected app
# surfaces → leave. Needs PIN unlock, so this is a PROD-engine check (the spike has no
# PIN pad); validated at WP2. (Vault import/export round-trip is a separate, heavier
# check: SAF/DocumentsUI automation is fragile — kept manual, see README.)
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; source "$HERE/lib.sh"
[ "$LOCK_ENGINE" = spike ] && { step "smoke_core"; info "SKIPPED (exit 3, unsupported) — prod-path (PIN unlock); the spike has no PIN pad. Runs under LOCK_ENGINE=prod at WP2."; exit 3; }

require_device || exit 2
resolve_clock || exit 2

step "smoke_core: lock → PIN → unlock ($PROTECTED_PKG)"
home; sleep 1

launch_pkg "$PROTECTED_PKG"
if wait_lockscreen; then pass "overlay lock surface shown on protected-app launch"
else fail "no overlay lock surface on launching $PROTECTED_PKG (top=$(top_component))"; summary "smoke_core"; exit 1; fi

_screen_wh && enter_pin "$PIN"
if wait_foreground "$PROTECTED_PKG"; then pass "PIN unlock surfaced the protected app"
else fail "protected app not foreground after PIN (top=$(top_component))"; fi

# a wrong-then-right sanity: relaunch, one wrong PIN stays locked, correct unlocks
home; sleep 1; launch_pkg "$PROTECTED_PKG"; wait_lockscreen || true
_screen_wh && enter_pin "0000"; sleep 1
if is_lockscreen; then pass "wrong PIN kept the overlay lock surface up"
else fail "wrong PIN did NOT keep the overlay lock surface (top=$(top_component))"; fi
enter_pin "$PIN"; wait_foreground "$PROTECTED_PKG" && info "recovered with correct PIN" || true
home

summary "smoke_core"
