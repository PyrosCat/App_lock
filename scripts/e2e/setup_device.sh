#!/usr/bin/env bash
# setup_device — provision a booted device/emulator to the M7 harness baseline:
# install the APK, grant Usage Access + "display over other apps" via appops (this
# replaced the old accessibility-service bind), keep the screen awake, and provision
# the active lock engine. Idempotent-ish: safe to re-run; run_all.sh calls it unless
# --skip-setup.
#
# LOCK_ENGINE=spike (WP1 default): the throwaway spike has no PIN/app-list, so setup
# hands its poll FGS the Clock target and confirms the overlay comes up, then stops.
# LOCK_ENGINE=prod (WP2+): set the PIN and protect Clock through the app-list UI
# (there is no programmatic protect API, and the DB is SQLCipher-encrypted): it finds
# the "Clock" row and taps its switch — the most device-fragile step; failures guide.
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; source "$HERE/lib.sh"
APK="${APK:-$HERE/../../app/build/outputs/apk/prod/debug/app-prod-debug.apk}"   # WP4 flavors: prod/debug

require_device || exit 2
resolve_clock || exit 2

step "setup: install APK"
# Fail-closed (CR-005): a missing APK or a non-Success install aborts, so a stale
# on-device build can't silently be cited as evidence. USE_PREINSTALLED=1 is the
# explicit diagnostic escape (provenance then NOT established).
if [ "${USE_PREINSTALLED:-}" = 1 ]; then
  info "USE_PREINSTALLED=1 — skipping install (diagnostic; provenance NOT established)"
elif [ ! -f "$APK" ]; then
  fail "APK not found at $APK (build :app:assembleProdDebug, set APK=, or USE_PREINSTALLED=1)"; summary "setup"; exit 1
else
  out="$(adbx install -r -g "$(host_path "$APK")" 2>&1)"
  if printf '%s' "$out" | grep -q 'Success'; then info "installed $(basename "$APK")"
  else fail "install did not report Success: $(printf '%s' "$out" | tr '\n' ' ' | tail -c 200)"; summary "setup"; exit 1; fi
fi

step "setup: grant Usage Access + overlay (appops)"
# Replaces the old accessibility rebind: both ops grant cleanly over adb on emulators
# AND real devices (the a11y path trapped real devices >=API13 in "Restricted Settings").
grant_usage_access; grant_overlay
if grants_ok; then info "granted android:get_usage_stats + android:system_alert_window"
else fail "appops grants did not stick (overlay=$(sh_ appops get "$APP_ID" android:system_alert_window))"; fi

step "setup: harness environment (WP0-report lessons)"
screen_stayon   # poll pauses on screen-off, so keep the screen awake mid-run (Moto G battery report)
boost_logcat    # grow the logcat ring so M7Spike counts don't rotate out under bursts (Moto G note)
info "screen kept awake; logcat buffer -> 16M"

step "setup: grant CAMERA (intruder selfie, optional)"
sh_ pm grant "$APP_ID" android.permission.CAMERA >/dev/null 2>&1 || true

# --- spike engine (WP1): no PIN / no protect-toggle UI. Drive the poll FGS directly. -
if [ "$LOCK_ENGINE" = spike ]; then
  step "setup: provision spike engine (target=$PROTECTED_LABEL / $PROTECTED_PKG)"
  warm_detection   # arrange the target + prime the detector past the post-install first-detection gap
  if detection_working; then pass "spike overlay comes up for $PROTECTED_LABEL"
  else
    fail "spike overlay did NOT come up for $PROTECTED_LABEL. Check:"
    info "  1. appops overlay=$(sh_ appops get "$APP_ID" android:system_alert_window) usage=$(sh_ appops get "$APP_ID" android:get_usage_stats)"
    info "  2. the poll FGS started (target lives in SpikeState, reset by process death)"
    info "  3. the target is a NORMAL app, not Settings (overlays are force-hidden over Settings)"
  fi
  summary "setup"; exit $?
fi

# --- prod engine (WP2+): the app's own PIN + protect-toggle UI (detection lands WP2/WP3) ---
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

# Confirm protection took AND detection is delivering (the overlay comes up).
home; sleep 1; launch_pkg "$PROTECTED_PKG"
if wait_lockscreen; then pass "Clock now protected (overlay lock surface appeared)"; enter_pin "$PIN"; home
else
  fail "Clock did not lock. Two possible causes:"
  info "  1. The wrong switch row was toggled (re-run; the locate step is geometric)."
  info "  2. A capability grant did not stick — check appops:"
  info "     usage=$(sh_ appops get "$APP_ID" android:get_usage_stats) overlay=$(sh_ appops get "$APP_ID" android:system_alert_window)"
  info "     (both must read 'allow'; re-grant, then re-run with --skip-setup)."
fi

summary "setup"
