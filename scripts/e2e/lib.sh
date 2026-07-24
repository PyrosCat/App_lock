#!/usr/bin/env bash
# WP2 regression harness — shared library.
# Sourced by every check script. Encodes the app's identifiers and the hard-won
# emulator/adb recipes from the Phase 3 campaign (PIN-pad geometry, a11y rebind,
# tap timing, focus detection) so the OV-3 / OV-4 / F3 gating checks can run
# headless and be asserted mechanically — no screenshot parsing.
#
# The gating semantics these checks defend (F3 self-gate resume, F4 fast-relaunch)
# are exactly where the two Phase-3 security bypasses lived; run this before AND
# after any change to the lock engine, session manager, or self-gate (M1 WP5/WP6).

set -uo pipefail
export MSYS_NO_PATHCONV=1   # Git Bash must not mangle device paths like /sdcard/...

# ---- configuration (override via env) -------------------------------------
: "${APP_ID:=com.applock}"                 # applicationId (WP4 adds .dev/.qa/... suffixes)
: "${PIN:=1234}"                           # test PIN (campaign baseline)
: "${A11Y_CLASS:=com.applock.applocker.service.AppDetectionService}"  # FQCN pinned (ADR-013)
LOCK_ACTIVITY_MATCH="LockScreenActivity"   # substring identifying the lock screen activity
MAIN_ACTIVITY="${APP_ID}/com.applock.ui.MainActivity"
A11Y_COMPONENT="${APP_ID}/${A11Y_CLASS}"
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
is_lockscreen()   { top_component | grep -q "$LOCK_ACTIVITY_MATCH"; }
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
launch_main() { sh_ am start -n "$MAIN_ACTIVITY" >/dev/null; }
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

rebind_a11y() { # delete-then-put; binding completes on next app launch (see gotchas memo)
  # NOTE: this adb path works on EMULATORS, but on real devices >= Android 13 the
  # "Restricted Settings" hardening enables the service in a "malfunctioning" state
  # that never delivers events (verified on Moto G 2025 / Android 15, where the
  # "Allow restricted settings" escape hatch is also removed). On such devices the
  # operator MUST grant accessibility via the real Settings UI (toggle off/on). See
  # a11y_working() and the setup guidance.
  sh_ settings delete secure enabled_accessibility_services
  sh_ settings put secure enabled_accessibility_services "$A11Y_COMPONENT"
  sh_ settings put secure accessibility_enabled 1
}

# Behavioural test that accessibility events actually reach the engine: a protected
# app must show the lock screen. Assumes $PROTECTED_PKG is protected. Returns 0 if
# the lock screen appears (a11y is delivering), 1 otherwise (grant needed via UI).
a11y_working() {
  home; sleep 1; launch_pkg "$PROTECTED_PKG"
  if wait_lockscreen; then enter_pin "$PIN"; home; return 0; else home; return 1; fi
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
