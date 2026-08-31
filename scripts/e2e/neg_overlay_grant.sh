#!/usr/bin/env bash
# Negative control (WP1 acceptance, CR-006): with the overlay op REVOKED, detection must
# NOT present the overlay — proving the probe is sensitive to the capability it asserts
# (not matching stale window state or the wrong title). Then restore the grant and confirm
# recovery. Spike engine only (drives the spike detector); the production equivalent is WP2.
#
# A PASS here means "the harness detected the deliberately-broken capability" — NOT a
# product-protection PASS.
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; source "$HERE/lib.sh"
[ "$LOCK_ENGINE" = spike ] || { step "neg_overlay_grant"; info "SKIPPED — spike-only negative control (production equivalent lands at WP2)."; exit 3; }

require_device || exit 2
resolve_clock || exit 2

step "neg control: REVOKE overlay grant → overlay must NOT appear"
stop_detection; revoke_overlay
if grants_ok; then
  fail "overlay op still reports 'allow' after revoke — cannot run the negative control"
  grant_overlay; summary "neg_overlay_grant"; exit 1
fi
arrange_protected                       # detector runs, but presentation cannot draw (no SAW)
home; sleep 1; launch_pkg "$PROTECTED_PKG"
if wait_lockscreen; then
  fail "NEGATIVE CONTROL FAILED — overlay appeared with system_alert_window revoked (probe not sensitive / stale-window match)"
else
  pass "overlay absent while grant revoked (probe is sensitive to the capability)"
fi
home

step "neg control: RESTORE overlay grant → overlay recovers"
grant_overlay
home; sleep 1; launch_pkg "$PROTECTED_PKG"
if wait_lockscreen; then pass "overlay recovers after re-granting"; clear_lock
else fail "overlay did NOT recover after re-granting the overlay op"; fi
home

summary "neg_overlay_grant"
