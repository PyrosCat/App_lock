#!/usr/bin/env bash
# M7 security-regression harness — shared library.
# Sourced by every check script. Encodes the app's identifiers and the hard-won
# emulator/adb recipes (PIN-pad geometry, tap timing, focus + overlay detection) so the
# OV-3 / OV-4 / F3 gating checks run headless and are asserted mechanically, no screenshots.
#
# M7/WP1 reworked this off the old engine: the lock surface is a drawn SYSTEM_ALERT_WINDOW
# overlay (found by window title via `dumpsys window`), and capabilities are granted with
# `appops` (Usage Access + overlay), replacing the resumed-LockScreenActivity assertions and
# the accessibility rebind. The gating semantics it defends (F3 self-gate resume, F4/OV-4
# fast-relaunch) are where the two Phase-3 bypasses lived; run it before AND after any change
# to the lock engine, detector, session manager, or self-gate.
#
# LOCK_ENGINE=spike (default, WP1) drives the throwaway WP0 spike overlay; WP2 flips it to
# prod and repoints OVERLAY_WINDOW_TITLE / POLL_SERVICE to the production surface.

set -uo pipefail
export MSYS_NO_PATHCONV=1   # Git Bash must not mangle device paths like /sdcard/...

# ---- configuration (override via env) -------------------------------------
: "${APP_ID:=com.applock}"                 # applicationId (WP4 adds .dev/.qa/... suffixes)
: "${PIN:=1234}"                           # test PIN (campaign baseline)
# --- M7 lock engine (WP1) --------------------------------------------------
# The lock surface is now a drawn SYSTEM_ALERT_WINDOW overlay, found by its stable window
# title via `dumpsys window` (the resumed-LockScreenActivity + a11y model died with the old
# engine, M7_PLAN WP1). Two constants are repointed spike->prod at WP2; nothing else changes.
: "${LOCK_ENGINE:=spike}"                  # spike | prod — engine the harness drives (WP1 = spike)
: "${OVERLAY_WINDOW_TITLE:=AppLockSpikeOverlay}"   # SpikeConfig.OVERLAY_WINDOW_TITLE; WP2 -> prod title
: "${POLL_SERVICE:=com.applock/com.applock.platform.spike.UsagePollService}"  # detection FGS; WP2 -> prod
: "${SPIKE_LAUNCHER:=com.applock/com.applock.platform.spike.SpikeLauncherActivity}"  # spike-only foregrounder
: "${POLL_INTERVAL_MS:=400}"               # poll interval P handed to the spike FGS (prod D1 = 200; sweepable)
# MainActivity moved to presentation/applist in WP6; kept here for reference only —
# launch_main() resolves the launcher activity dynamically so package moves don't stale it out.
MAIN_ACTIVITY="${APP_ID}/com.applock.presentation.applist.MainActivity"
# UI text signals (from res/values/strings.xml):
UI_APPLIST_SIGNAL="Open vault"             # content-desc present ONLY in the unlocked App List
UI_PIN_SIGNAL="Enter your PIN"             # subtitle on the self-gate / lock screen
UI_PIN_SETUP_SIGNAL="Create a PIN"         # first-run PIN setup

SERIAL="${SERIAL:-}"                        # set by -s or auto-detected
PROTECTED_PKG="${PROTECTED_PKG:-}"          # resolved to the Clock package
PROTECTED_LABEL="${PROTECTED_LABEL:-Clock}" # its display label in the app list (locale-specific)
NEUTRAL_PKG="${NEUTRAL_PKG:-com.android.settings}"  # an unprotected app to switch to

# PIN-pad tap geometry as fractions of screen size (derived from the 1080x2340
# campaign coords; portable across the pixel_5-profile matrix AVDs).
PIN_COL_FRAC=(0.276 0.499 0.723)           # columns 1|2|3
PIN_ROW_FRAC=(0.432 0.535 0.639 0.742)     # rows for 1-3 | 4-6 | 7-9 | 0
TAP_GAP="${TAP_GAP:-0.9}"                   # seconds between PIN taps (>=0.8 on slow emu)
LOCKSCREEN_WAIT="${LOCKSCREEN_WAIT:-8}"     # seconds to wait for the lock screen to appear
FG_WAIT="${FG_WAIT:-10}"                    # seconds to wait for an app to surface foreground
                                            # (cold slow hosts are sluggish — raise via env)

# ---- counters / logging ---------------------------------------------------
PASS_COUNT=0 FAIL_COUNT=0
_c() { case "${1}" in green) printf '\033[32m';; red) printf '\033[31m';; yellow) printf '\033[33m';; *) printf '';; esac; }
info() { printf '  %s\n' "$*"; }
step() { printf '\n\033[1m» %s\033[0m\n' "$*"; }
pass() { PASS_COUNT=$((PASS_COUNT+1)); printf '  %sPASS\033[0m %s\n' "$(_c green)" "$*"; }
fail() { FAIL_COUNT=$((FAIL_COUNT+1)); printf '  %sFAIL\033[0m %s\n' "$(_c red)" "$*"; }

# ---- adb plumbing ---------------------------------------------------------
adbx() { adb -s "$SERIAL" "$@"; }
sh_() { adb -s "$SERIAL" shell "$@" 2>/dev/null | tr -d '\r'; }

detect_serial() {
  [ -n "$SERIAL" ] && return 0
  SERIAL="$(adb devices | awk '/\tdevice$/{print $1; exit}')"
  [ -n "$SERIAL" ] || { fail "no online device (adb devices shows none 'device')"; return 1; }
  info "device: $SERIAL"
}

require_device() {
  detect_serial || return 1
  local booted; booted="$(sh_ getprop sys.boot_completed)"
  [ "$booted" = "1" ] || { fail "device $SERIAL not fully booted (sys.boot_completed=$booted)"; return 1; }
}

# ---- screen / input -------------------------------------------------------
_screen_wh() {
  local s; s="$(sh_ wm size | sed -n 's/.*: *\([0-9]*\)x\([0-9]*\).*/\1 \2/p' | tail -1)"
  SCREEN_W="${s% *}"; SCREEN_H="${s#* }"
  [ -n "${SCREEN_W:-}" ] && [ -n "${SCREEN_H:-}" ]
}
tap_frac() { # xfrac yfrac
  local x y; x=$(awk "BEGIN{printf \"%d\", $1*$SCREEN_W}"); y=$(awk "BEGIN{printf \"%d\", $2*$SCREEN_H}")
  sh_ input tap "$x" "$y"
}
_pin_digit() { # 0-9 -> col,row
  local d="$1" col row
  case "$d" in
    1) col=0 row=0;; 2) col=1 row=0;; 3) col=2 row=0;;
    4) col=0 row=1;; 5) col=1 row=1;; 6) col=2 row=1;;
    7) col=0 row=2;; 8) col=1 row=2;; 9) col=2 row=2;;
    0) col=1 row=3;;
  esac
  tap_frac "${PIN_COL_FRAC[$col]}" "${PIN_ROW_FRAC[$row]}"
}
# Center-of-node coords for a PIN digit, located by its Compose Text ("1".."0")
# in the current uiautomator dump. Resolution-independent — works on any screen
# where Compose exposes the button text (it does; PinKey renders Text(label)).
_digit_xy() { # xml digit -> "x y" or empty
  printf '%s' "$1" \
    | grep -oE "text=\"$2\"[^>]*bounds=\"\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]\"" \
    | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | head -1 \
    | sed -E 's/bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]"/\1 \2 \3 \4/' \
    | awk 'NF==4{printf "%d %d",($1+$3)/2,($2+$4)/2}'
}
enter_pin() { # pin-string ; taps each digit at its located center, else by fraction
  _screen_wh || { fail "could not read screen size"; return 1; }
  local xml; xml="$(ui_xml)"     # dump once; key positions are static during entry
  local i ch xy
  for (( i=0; i<${#1}; i++ )); do
    ch="${1:$i:1}"; xy="$(_digit_xy "$xml" "$ch")"
    if [ -n "$xy" ]; then sh_ input tap $xy; else _pin_digit "$ch"; fi   # by-text, else geometry
    sleep "$TAP_GAP"
  done
  sleep 1
}
home()    { sh_ input keyevent KEYCODE_HOME; }
recents() { sh_ input keyevent KEYCODE_APP_SWITCH; }

# ---- focus / state introspection ------------------------------------------
top_component() { # -> pkg/activity of the resumed activity (portable across API 26-35)
  local c
  # API 30/33 print "mResumedActivity:"; Android 15 prints "topResumedActivity="/
  # "ResumedActivity:". Match any, excluding the *Last*/*Paused* history entries.
  c="$(sh_ dumpsys activity activities | grep -iE 'ResumedActivity' | grep -ivE 'LastResumed|LastPaused|PausedActivity' | grep -Eo '[A-Za-z0-9_.]+/[A-Za-z0-9_.]+' | head -1)"
  [ -n "$c" ] || c="$(sh_ dumpsys window | grep -iE 'mCurrentFocus|mFocusedApp' | grep -Eo '[A-Za-z0-9_.]+/[A-Za-z0-9_.]+' | head -1)"
  printf '%s' "$c"
}
# --- overlay lock-surface probe (M7 engine) --------------------------------
# z-order of our overlay window ($OVERLAY_WINDOW_TITLE) via `dumpsys window` — NOT
# `dumpsys window windows`, which omits the mCurrentFocus line and makes every present
# overlay read BEHIND (mirrors OverlayRaceUiTest.zOrder()). Prints TOP | BEHIND | ABSENT.
# NB: the dumpsys format varies by API level AND OEM skin. WP0's FTL sweep saw Samsung One UI
# possibly mis-parse mCurrentFocus (a focused overlay scoring a false BEHIND); the WP2 open item
# sets the production overlay focus flags and revalidates this grep. ABSENT (the security-critical
# state) read robustly on every OEM; TOP/BEHIND is the soft boundary. Revalidate across the §10
# lanes — One UI coverage comes via FTL, not the AOSP NucBox lanes.
overlay_z() {
  local dump; dump="$(sh_ dumpsys window)"
  if printf '%s\n' "$dump" | grep -E 'mCurrentFocus' | grep -qF "$OVERLAY_WINDOW_TITLE"; then printf 'TOP'
  elif printf '%s\n' "$dump" | grep -qF "$OVERLAY_WINDOW_TITLE"; then printf 'BEHIND'
  else printf 'ABSENT'; fi
}
overlay_present() { [ "$(overlay_z)" != ABSENT ]; }
overlay_on_top()  { [ "$(overlay_z)" = TOP ]; }
# "lock screen up" now means the overlay lock surface is drawn AND focus-holding (TOP). The
# name is kept so the OV-3/OV-4/smoke callers stay stable through the engine swap (M7_PLAN WP1).
is_lockscreen()   { overlay_on_top; }
foreground_is()   { top_component | grep -q "^$1/"; }

wait_lockscreen() { # timeout-seconds ; returns 0 when lock screen is up
  local t="${1:-$LOCKSCREEN_WAIT}" i
  for (( i=0; i<t*2; i++ )); do is_lockscreen && return 0; sleep 0.5; done
  return 1
}
wait_foreground() { # pkg [timeout=$FG_WAIT]
  local pkg="$1" t="${2:-$FG_WAIT}" i
  for (( i=0; i<t*2; i++ )); do foreground_is "$pkg" && return 0; sleep 0.5; done
  return 1
}

# ---- UI hierarchy (for in-MainActivity self-gate vs app-list distinction) --
ui_xml() { # dump the current window hierarchy to stdout (one retry)
  sh_ uiautomator dump /sdcard/e2e_ui.xml >/dev/null
  local x; x="$(sh_ cat /sdcard/e2e_ui.xml)"
  [ -n "$x" ] || { sh_ uiautomator dump /sdcard/e2e_ui.xml >/dev/null; x="$(sh_ cat /sdcard/e2e_ui.xml)"; }
  printf '%s' "$x"
}
ui_has() { ui_xml | grep -qF "$1"; }

# ---- app control ----------------------------------------------------------
launch_pkg() { # resolve the launcher activity and am-start it (more reliable than monkey)
  local comp; comp="$(sh_ cmd package resolve-activity --brief -c android.intent.category.LAUNCHER "$1" | tail -1)"
  if [ -n "$comp" ] && printf '%s' "$comp" | grep -q '/'; then sh_ am start -n "$comp" >/dev/null
  else sh_ monkey -p "$1" -c android.intent.category.LAUNCHER 1 >/dev/null; fi
}
launch_main() { launch_pkg "$APP_ID"; }  # resolves MainActivity dynamically (survives package moves)
dismiss_anr() { # tap "Wait"/"Close app" ANR dialogs that this slow emulator throws
  ui_has "isn't responding" && { sh_ input keyevent KEYCODE_BACK; sleep 1; }
  return 0
}

# Convert a host path for tools that need a native path (adb install takes a HOST
# path, unlike shell/push which take device paths). cygpath on Git Bash; identity
# elsewhere. Needed because MSYS_NO_PATHCONV=1 (set for device paths) would
# otherwise hand adb.exe an unresolvable /c/... path.
host_path() { cygpath -w "$1" 2>/dev/null || printf '%s' "$1"; }

# Bring App Lock to the App List, clearing the self-gate (FR-108) if it is up.
# Returns 1 if the app is still at first-run PIN setup (caller must set a PIN first).
open_app_list() {
  launch_main; sleep 2; dismiss_anr
  ui_has "$UI_PIN_SETUP_SIGNAL" && return 1
  if ui_has "$UI_PIN_SIGNAL"; then _screen_wh && enter_pin "$PIN"; sleep 2; dismiss_anr; fi
  ui_has "$UI_APPLIST_SIGNAL"
}

resolve_clock() {
  [ -n "$PROTECTED_PKG" ] && return 0
  PROTECTED_PKG="$(sh_ pm list packages | sed 's/package://' \
    | grep -iE 'deskclock|\.clock$' | grep -iE 'google' | head -1)"
  [ -n "$PROTECTED_PKG" ] || PROTECTED_PKG="$(sh_ pm list packages | sed 's/package://' | grep -iE 'deskclock|\.clock$' | head -1)"
  [ -n "$PROTECTED_PKG" ] || { fail "could not resolve a Clock package to use as the protected app"; return 1; }
  info "protected app (Clock): $PROTECTED_PKG"
}

# --- capability grants (replace the a11y rebind) ---------------------------
# Usage Access + "display over other apps", both grantable over adb via appops on emulators
# AND real devices (unlike the old a11y rebind, which real devices >=API13 trapped in the
# "Restricted Settings" malfunctioning state). Mirrors OverlayRaceUiTest's @Before grants.
grant_usage_access() { sh_ appops set "$APP_ID" android:get_usage_stats allow >/dev/null; }
grant_overlay()      { sh_ appops set "$APP_ID" android:system_alert_window allow >/dev/null; }
grants_ok() { # both ops report allow?
  sh_ appops get "$APP_ID" android:system_alert_window | grep -qi allow \
    && sh_ appops get "$APP_ID" android:get_usage_stats | grep -qi allow
}
revoke_overlay() { sh_ appops set "$APP_ID" android:system_alert_window ignore >/dev/null; }  # negative control (CR-006)
is_pos_int() { case "${1:-}" in ''|*[!0-9]*|0) return 1;; *) return 0;; esac; }                # positive integer only (CR-003)

# Arrange the lock surface for $PROTECTED_PKG under the active engine, so a later launch of
# that package raises the overlay. Spike: hand the throwaway poll FGS the target directly
# (no PIN / protect-toggle exists in the spike). Prod (WP2): the app's own FGS already runs
# from provisioning, so this is a placeholder repointed when prod detection lands.
arrange_protected() {
  case "$LOCK_ENGINE" in
    spike)
      sh_ am start -n "$SPIKE_LAUNCHER" >/dev/null; sleep 0.5
      sh_ am start-foreground-service -n "$POLL_SERVICE" --es target "$PROTECTED_PKG" --el interval "$POLL_INTERVAL_MS" >/dev/null
      sleep 0.5; home ;;
    prod) : ;;   # production keeps its own detector alive once PIN set + app protected (WP2 wires this)
    *)    fail "unknown LOCK_ENGINE=$LOCK_ENGINE (want spike|prod)"; return 1 ;;
  esac
}

# Clear the lock surface after a positive detection, engine-appropriately.
clear_lock() {
  case "$LOCK_ENGINE" in
    spike) sh_ am start-foreground-service -n "$POLL_SERVICE" -a com.applock.spike.DISMISS >/dev/null ;;
    prod)  enter_pin "$PIN"; wait_foreground "$PROTECTED_PKG" >/dev/null 2>&1 || true ;;
  esac
  home
}

# Tear down detection (spike only; prod's FGS is app-managed). Best-effort, always returns 0.
stop_detection() {
  [ "$LOCK_ENGINE" = spike ] && sh_ am start-foreground-service -n "$POLL_SERVICE" -a com.applock.spike.STOP >/dev/null
  return 0
}

# Behavioural probe that the detection->overlay path is live: launching the protected app
# raises the overlay lock surface. Replaces a11y_working(). Returns 0 if the overlay comes
# up (detection delivering), 1 otherwise (a missing grant / dead detector — the negative
# control WP1 acceptance requires). Assumes arrange_protected has run (spike).
detection_working() {
  home; sleep 1; launch_pkg "$PROTECTED_PKG"
  if wait_lockscreen; then clear_lock; return 0; else home; return 1; fi
}

# --- WP0-report operational lessons (fold the campaign findings into setup) -
# Keep the screen awake: the poll pauses on screen-off (§2.4 / Moto G battery report), so a
# sleeping device stalls detection mid-run (svc power stayon true — Moto G method note).
screen_stayon() { sh_ svc power stayon true >/dev/null 2>&1 || true; }
# Grow the logcat ring: under a rapid-relaunch burst the default buffer rotates M7Spike lines
# out and a logcat-based count plateaus (Moto G measurement-fix note). Only matters for
# count-from-logcat steps; harmless otherwise.
boost_logcat() { adbx logcat -G 16M >/dev/null 2>&1 || true; }
# Prime the detector after (re)install: WP0's api30 biometric run found queryEvents misses the
# FIRST foreground detection for a window right after install (~90%, n=10; steady-state is 21/21
# reliable). setup_device.sh reinstalls each run, so warm the detector once and discard the
# result before any check asserts on detection. (Spike: arrange the target first.)
warm_detection() {
  [ "$LOCK_ENGINE" = spike ] && arrange_protected
  home; sleep 1; launch_pkg "$PROTECTED_PKG"; wait_lockscreen >/dev/null 2>&1 || true
  [ "$LOCK_ENGINE" = spike ] && sh_ am start-foreground-service -n "$POLL_SERVICE" -a com.applock.spike.DISMISS >/dev/null 2>&1
  home
}

# Bring App Lock to a known LOCKED-out state for a protected app, then unlock it.
unlock_protected() { # launches Clock, expects lock screen, enters PIN, expects Clock fg
  launch_pkg "$PROTECTED_PKG"
  wait_lockscreen || { fail "expected lock screen after launching $PROTECTED_PKG"; return 1; }
  enter_pin "$PIN"
  wait_foreground "$PROTECTED_PKG" || { fail "PIN unlock did not surface $PROTECTED_PKG"; return 1; }
  return 0
}

summary() { # label
  printf '\n\033[1m%s: %d passed, %d failed\033[0m\n' "${1:-Result}" "$PASS_COUNT" "$FAIL_COUNT"
  [ "$FAIL_COUNT" -eq 0 ]
}
