#!/usr/bin/env bash
# F3 — self-gate re-locks on resume (defends against the F3 vault-exposure bypass).
# Unlock App Lock's own gate → App List; background it; resume → the self-gate MUST
# reappear (the vault/log must not be reachable without re-auth, FR-108).
# The self-gate and App List are both MainActivity, so this asserts on UI content
# (the "Open vault" content-desc appears ONLY in the unlocked App List) rather than
# on the resumed activity.
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; source "$HERE/lib.sh"

require_device || exit 2

step "F3 self-gate re-gates on resume"
# 1. Reach the App List (pass the gate). Cold-launch → gate; enter PIN.
home; sleep 1; launch_main; sleep 2; dismiss_anr
if ui_has "$UI_PIN_SETUP_SIGNAL"; then
  fail "F3: no PIN set — run setup_device.sh first"; summary "F3"; exit 1
fi
if ui_has "$UI_PIN_SIGNAL"; then _screen_wh && enter_pin "$PIN"; sleep 2; fi
if ! ui_has "$UI_APPLIST_SIGNAL"; then
  fail "F3: could not reach the App List after entering PIN (top=$(top_component))"; summary "F3"; exit 1
fi
info "reached App List (unlocked)"

# 2. Background, then resume the live instance.
home; sleep 2
launch_main; sleep 2; dismiss_anr

# 3. Assert re-gated: App List signal gone AND PIN prompt present.
if ui_has "$UI_APPLIST_SIGNAL"; then
  fail "F3: App List reachable on resume WITHOUT re-auth — vault exposed (FR-108 bypass)"
elif ui_has "$UI_PIN_SIGNAL"; then
  pass "F3: self-gate re-appeared on resume (App List not reachable without PIN)"
else
  fail "F3: ambiguous post-resume state (top=$(top_component)); neither App List nor PIN prompt seen"
fi

summary "F3"
