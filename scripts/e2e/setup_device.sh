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
if [ -f "$APK" ]; then
  out="$(adbx install -r -g "$(host_path "$APK")" 2>&1)"
  if printf '%s' "$out" | grep -q 'Success'; then info "installed $(basename "$APK")"
  else info "install did not report Success: $(printf '%s' "$out" | tr '\n' ' ' | tail -c 200)"; fi
else info "APK not found at $APK — assuming already installed"; fi

step "setup: bind accessibility service"
# Non-destructive: if the service is already enabled (e.g. a working manual UI grant
# on a real device), DON'T delete+re-put it — that would reset a real device back to
# the broken "Restricted Settings" state. Only rebind when it isn't already enabled.
if sh_ settings get secure enabled_accessibility_services | grep -q "$A11Y_CLASS"; then
  sh_ settings put secure accessibility_enabled 1
  info "a11y already enabled — preserved (not resetting; a real-device grant survives)"
else
  rebind_a11y
  info "a11y enabled via adb ($A11Y_COMPONENT) — works on emulators; a real device >=API13"
  info "  will still show it 'malfunctioning' until granted via the Settings UI (see below)"
fi

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
# Is Clock already protected? Probe by behaviour — this backgrounds App Lock, so
# the self-gate (FR-108) will be up afterwards and open_app_list must clear it.
home; sleep 1; launch_pkg "$PROTECTED_PKG"
if wait_lockscreen; then   # full timeout: a false "not protected" here would toggle Clock OFF
  pass "Clock already protected (lock screen appeared)"; enter_pin "$PIN"; home; summary "setup"; exit 0
fi
info "Clock not yet protected — opening the App List (through the self-gate) to toggle it"
open_app_list || { fail "could not reach the App List to toggle Clock (PIN/self-gate not cleared)"; summary "setup"; exit 1; }
_screen_wh
# The app list is alphabetical and can be long on a real device; one big swipe
# overshoots the target row (jumps clean past it). Scroll to the top, then step
# down in small increments, checking for the label each time.
info "locating the '$PROTECTED_LABEL' row (scroll to top, then step down)"
for i in $(seq 1 6); do sh_ input swipe $((SCREEN_W/2)) $((SCREEN_H/4)) $((SCREEN_W/2)) $((SCREEN_H*3/4)) 200; done; sleep 1
label_re="text=\"$PROTECTED_LABEL\"[^>]*bounds=\"\\[[0-9]+,[0-9]+\\]\\[[0-9]+,[0-9]+\\]\""
found=""
for step in $(seq 1 20); do
  bounds="$(ui_xml | grep -oE "$label_re" | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | head -1)"
  if [ -n "$bounds" ]; then
    yc="$(printf '%s' "$bounds" | sed -E 's/bounds="\[[0-9]+,([0-9]+)\]\[[0-9]+,([0-9]+)\]"/\1 \2/' | awk '{printf "%d",($1+$2)/2}')"
    sw_x=$(awk "BEGIN{printf \"%d\", 0.92*$SCREEN_W}")   # switch sits at the right edge of the row
    info "tapping $PROTECTED_LABEL switch at ($sw_x,$yc) [step $step]"; sh_ input tap "$sw_x" "$yc"; sleep 1; found=1; break
  fi
  sh_ input swipe $((SCREEN_W/2)) $((SCREEN_H*55/100)) $((SCREEN_W/2)) $((SCREEN_H*40/100)) 250; sleep 0.7   # small scroll down
done
[ -n "$found" ] || { fail "could not locate the '$PROTECTED_LABEL' row after scrolling (locale? label mismatch?); toggle it manually"; summary "setup"; exit 1; }

# Confirm protection took AND accessibility is delivering events.
home; sleep 1; launch_pkg "$PROTECTED_PKG"
if wait_lockscreen; then pass "Clock now protected (lock screen appeared)"; enter_pin "$PIN"; home
else
  fail "Clock did not lock. Two possible causes:"
  info "  1. The wrong switch row was toggled (re-run; the locate step is geometric)."
  info "  2. Accessibility is enabled but NOT delivering events — the Android 13+"
  info "     'Restricted Settings' state (App Info shows 'malfunctioning'). adb cannot"
  info "     fix this on real devices. Grant it via the phone: Settings > Accessibility"
  info "     Settings > Accessibility > App Lock protection > toggle OFF then ON"
  info "     (on Android 15 the 'Allow restricted settings' menu may be gone — the"
  info "     off/on toggle is the way). Then re-run with --skip-setup."
fi

summary "setup"
