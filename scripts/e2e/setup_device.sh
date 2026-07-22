#!/usr/bin/env bash
# setup_device — provision a booted device/emulator to the harness baseline:
# install the debug APK, set the PIN, bind the accessibility service, and protect
# the Clock app. Idempotent-ish: safe to re-run. Run once before the check scripts
# (run_all.sh calls it unless --skip-setup).
#
# Protecting Clock is driven through the UI (there is no programmatic protect API,
# and the DB is SQLCipher-encrypted): it finds the "Clock" row in the app list and
# taps its switch. That is the most device-fragile step; failures print guidance.
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; source "$HERE/lib.sh"
APK="${APK:-$HERE/../../app/build/outputs/apk/debug/app-debug.apk}"

require_device || exit 2
resolve_clock || exit 2

step "setup: install APK"
if [ -f "$APK" ]; then adbx install -r -g "$APK" >/dev/null 2>&1 && info "installed $(basename "$APK")" || info "install returned nonzero (may already be current)"
else info "APK not found at $APK — assuming already installed"; fi

step "setup: bind accessibility service"
rebind_a11y; info "a11y set to $A11Y_COMPONENT (binds on next launch)"

step "setup: grant CAMERA (intruder selfie, optional)"
sh_ pm grant "$APP_ID" android.permission.CAMERA >/dev/null 2>&1 || true

step "setup: PIN"
home; sleep 1; launch_main; sleep 2; dismiss_anr
if ui_has "$UI_PIN_SETUP_SIGNAL"; then
  _screen_wh; enter_pin "$PIN"; sleep 1; enter_pin "$PIN"; sleep 2   # create + confirm
  info "PIN created ($PIN)"
elif ui_has "$UI_PIN_SIGNAL"; then
  _screen_wh; enter_pin "$PIN"; sleep 2; info "PIN already set — passed self-gate"
else
  info "no PIN prompt (already past gate)"
fi

step "setup: protect Clock via the app list"
if ! ui_has "$UI_APPLIST_SIGNAL"; then fail "not on the App List — cannot toggle Clock protection"; exit 1; fi
# Verify by behaviour: is Clock already protected?
home; sleep 1; launch_pkg "$PROTECTED_PKG"
if wait_lockscreen 5; then
  pass "Clock already protected (lock screen appeared)"; enter_pin "$PIN"; home; summary "setup"; exit 0
fi
info "Clock not yet protected — locating its switch in the app list"
launch_main; sleep 2; dismiss_anr
_screen_wh
found=""
for attempt in 1 2 3 4 5; do
  xml="$(ui_xml)"
  # find a node whose text is exactly Clock; extract its bounds "[x1,y1][x2,y2]"
  bounds="$(printf '%s' "$xml" | grep -oE 'text="Clock"[^>]*bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | head -1)"
  if [ -n "$bounds" ]; then
    y1="$(printf '%s' "$bounds" | sed -E 's/.*\],\[[0-9]+,([0-9]+)\]"/x/; s/bounds="\[[0-9]+,([0-9]+)\].*/\1/')"
    yc="$(printf '%s' "$bounds" | sed -E 's/bounds="\[[0-9]+,([0-9]+)\]\[[0-9]+,([0-9]+)\]"/\1 \2/' | awk '{printf "%d",($1+$2)/2}')"
    sw_x=$(awk "BEGIN{printf \"%d\", 0.92*$SCREEN_W}")   # switch sits at the right edge of the row
    info "tapping Clock switch at ($sw_x,$yc)"; sh_ input tap "$sw_x" "$yc"; sleep 1; found=1; break
  fi
  sh_ input swipe $((SCREEN_W/2)) $((SCREEN_H*3/4)) $((SCREEN_W/2)) $((SCREEN_H/4)) 300; sleep 1   # scroll down
done
[ -n "$found" ] || { fail "could not locate the 'Clock' row (app list may render labels differently); toggle it manually"; summary "setup"; exit 1; }

# Confirm protection took.
home; sleep 1; launch_pkg "$PROTECTED_PKG"
if wait_lockscreen 6; then pass "Clock now protected (lock screen appeared)"; enter_pin "$PIN"; home
else fail "toggled a switch but Clock did not lock — verify the correct row was hit"; fi

summary "setup"
