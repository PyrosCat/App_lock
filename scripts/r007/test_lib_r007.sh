#!/usr/bin/env bash
# Host-only tests of lib_r007.sh with a stub adb: no device is needed. Each case sets the stub's behaviour through
# environment variables and checks that the library fails closed: a failed capture, kill, query, or script write is
# never taken for success. Each library call runs in a subshell, so its own PASS/FAIL counters stay separate.
#
# Usage: scripts/r007/test_lib_r007.sh
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/bin"
export STUB_DEVICE="$WORK/device"

# The stub adb. The app's private directory is $STUB_DEVICE; logcat reads $STUB_DEVICE/logcat.
cat > "$WORK/bin/adb" <<'STUB'
#!/usr/bin/env bash
[ "${1:-}" = -s ] && shift 2
cmd="$1"; shift
case "$cmd" in
  logcat)
    [ -n "${STUB_LOGCAT_FAIL:-}" ] && { echo "error: device offline" >&2; exit 1; }
    case " $* " in *" -c "*) : > "$STUB_DEVICE/logcat" ;; *) cat "$STUB_DEVICE/logcat" ;; esac ;;
  exec-in)
    [ -n "${STUB_WRITE_FAIL:-}" ] && exit 1
    target="$(sed -E "s/.*cat > ([^' ]+)'.*/\1/" <<< "$*")"
    mkdir -p "$STUB_DEVICE/$(dirname "$target")"
    # A write that the connection breaks: only the first line arrives.
    [ -n "${STUB_WRITE_PARTIAL:-}" ] && { head -1 > "$STUB_DEVICE/$target"; exit 1; }
    # A write that reports success but stores other content.
    [ -n "${STUB_WRITE_CORRUPT:-}" ] && { cat > /dev/null; echo "corrupt" > "$STUB_DEVICE/$target"; exit 0; }
    cat > "$STUB_DEVICE/$target"
    echo "exec-in $target" >> "$STUB_DEVICE/ops" ;;
  shell)
    # Like the real adb, a shell command reads the stdin that it inherits, so a `while read` loop around it loses input.
    while read -t 0 2>/dev/null && IFS= read -r _; do :; done
    line="$*"
    case "$line" in
      "run-as "*" kill -9 "*) [ -n "${STUB_KILL_FAIL:-}" ] && exit 1; echo "kill" >> "$STUB_DEVICE/ops" ;;
      *"/proc/"*) [ -n "${STUB_PROC_FAIL:-}" ] && exit 1; echo "${STUB_PROC_ANSWER-absent}" ;;
      "pidof "*) echo "${STUB_PID:-4242}" ;;
      "if pidof "*) [ -n "${STUB_PIDOF_FAIL:-}" ] && exit 1; echo "${STUB_APP_PROC-absent}" ;;
      "am force-stop "*) [ -n "${STUB_STOP_FAIL:-}" ] && exit 1; echo "force-stop" >> "$STUB_DEVICE/ops" ;;
      "dumpsys activity activities")
        [ -n "${STUB_DUMPSYS_FAIL:-}" ] && exit 1
        printf '  KeyguardController:\n%s\n' "${STUB_ACTIVITIES-    mKeyguardShowing=false}" ;;
      "dumpsys window") [ -n "${STUB_DUMPSYS_FAIL:-}" ] && exit 1; printf '%s\n' "${STUB_WINDOW-}" ;;
      *".bak ]; then echo present"*) [ -n "${STUB_QUERY_FAIL:-}" ] && exit 1; echo "${STUB_BAK-absent}" ;;
      "run-as "*" sha256sum "*)
        [ -n "${STUB_HASH_FAIL:-}" ] && exit 1
        f="$STUB_DEVICE/${line##* }"; [ -f "$f" ] || exit 1
        h="$(sha256sum < "$f")"; echo "${h%% *}  ${line##* }" ;;
      "run-as "*"sh -c "*"cat "*" > "*)
        [ -n "${STUB_COPY_FAIL:-}" ] && exit 1
        src="$(sed -E "s/.*cat ([^ ]+) > ([^' ]+)'.*/\1/" <<< "$line")"
        dst="$(sed -E "s/.*cat ([^ ]+) > ([^' ]+)'.*/\2/" <<< "$line")"
        # As in a real shell, the redirection empties the target before cat reads, unless a "[ -f src ] &&" guard
        # stops the command first.
        case "$line" in *"[ -f $src ] && "*) [ -f "$STUB_DEVICE/$src" ] || exit 1 ;; esac
        mkdir -p "$STUB_DEVICE/$(dirname "$dst")"; : > "$STUB_DEVICE/$dst"
        cat "$STUB_DEVICE/$src" > "$STUB_DEVICE/$dst" 2>/dev/null || exit 1 ;;
      "run-as "*" cat "*) cat "$STUB_DEVICE/${line##* }" ;;
      "run-as "*" mv -f "*)
        set -- $line
        mv -f "$STUB_DEVICE/${@: -2:1}" "$STUB_DEVICE/${@: -1}" && echo "mv ${@: -1}" >> "$STUB_DEVICE/ops" ;;
      "am instrument "*)
        [ -n "${STUB_INSPECT_WRITES:-}" ] \
          && echo "<!-- rewritten -->" >> "$STUB_DEVICE/shared_prefs/applock_lockout.xml"
        printf '%s\n' "${STUB_INSTRUMENT:-}" ;;
      # Device settings live in $STUB_DEVICE/settings/<namespace>.<key>; a missing file is an unset setting.
      "settings get "*)
        [ -n "${STUB_SETTINGS_GET_FAIL:-}" ] && exit 1
        set -- $line; [ "${STUB_SETTINGS_BAD:-}" = "$4" ] && { echo "garbage"; exit 0; }
        if [ -f "$STUB_DEVICE/settings/$3.$4" ]; then cat "$STUB_DEVICE/settings/$3.$4"; else echo null; fi ;;
      "settings put "*)
        [ -n "${STUB_SETTINGS_PUT_FAIL:-}" ] && exit 1
        set -- $line; mkdir -p "$STUB_DEVICE/settings"
        [ "${STUB_SETTINGS_IGNORE:-}" = "$4" ] || echo "$5" > "$STUB_DEVICE/settings/$3.$4"
        echo "put $3 $4 $5" >> "$STUB_DEVICE/ops" ;;
      "settings delete "*)
        set -- $line; rm -f "$STUB_DEVICE/settings/$3.$4"; echo "delete $3 $4" >> "$STUB_DEVICE/ops" ;;
      *"r007_settings.pending ]; then echo present"*)
        [ -n "${STUB_RECORD_QUERY_FAIL:-}" ] && exit 1
        if [ -e "$STUB_DEVICE/data/local/tmp/r007_settings.pending" ]; then echo present; else echo absent; fi ;;
      "cat /data/local/tmp/r007_settings.pending"*) cat "$STUB_DEVICE/${line#cat }" ;;
      "mv -f /data/local/tmp/r007_settings.pending"*)
        [ -n "${STUB_RECORD_MV_FAIL:-}" ] && exit 1
        [ -n "${STUB_RECORD_MV_LOST:-}" ] && exit 0   # the rename reports success but does not happen
        set -- $line; mv -f "$STUB_DEVICE/$3" "$STUB_DEVICE/$4"; echo "mv $3 $4" >> "$STUB_DEVICE/ops" ;;
      "rm -f /data/local/tmp/r007_settings.pending"*)
        [ -n "${STUB_RECORD_RM_FAIL:-}" ] && exit 1
        set -- $line; shift 2; for f in "$@"; do rm -f "$STUB_DEVICE/$f"; done ;;
      "dumpsys window displays")
        # The display sizes stay natural, so only the rotation field shows a rotation (as at 180 degrees).
        [ -n "${STUB_DISPLAYS_FAIL:-}" ] && exit 1
        echo "WINDOW MANAGER DISPLAY CONTENTS (dumpsys window displays)"
        echo "  Display: mDisplayId=0 rootTasks=3"
        echo "    init=720x1604 280dpi cur=720x1604 app=720x1604 rng=720x720-1604x1604"
        [ -n "${STUB_ROTATION_DECOY:-}" ] && echo "    mRotation=0 outside the DisplayRotation part"
        echo "    winConfig={ mBounds=Rect(0, 0 - 720, 1604) mRotation=ROTATION_${STUB_ROTATION:-0} }"
        if [ -z "${STUB_ROTATION_MISSING:-}" ]; then
          echo "    DisplayRotation"
          echo "      mCurrentAppOrientation=SCREEN_ORIENTATION_UNSPECIFIED"
          echo "      mRotation=${STUB_ROTATION:-0} mDeferredRotationPauseCount=0"
        fi
        echo "  Display: mDisplayId=1 rootTasks=1"
        echo "    DisplayRotation"
        echo "      mRotation=0 mDeferredRotationPauseCount=0" ;;
      "dumpsys power")
        # The screen state is STUB_WAKEFULNESS (default Awake) until a wake key turns it on; with STUB_WAKE_IGNORED
        # the key has no effect. mStayOn follows the stay-awake setting while the device is powered, as on a device.
        [ -n "${STUB_POWER_FAIL:-}" ] && exit 1
        wake="${STUB_WAKEFULNESS:-Awake}"
        [ -e "$STUB_DEVICE/woken" ] && [ -z "${STUB_WAKE_IGNORED:-}" ] && wake=Awake
        stay="$(cat "$STUB_DEVICE/settings/global.stay_on_while_plugged_in" 2>/dev/null || echo 0)"
        echo "  mWakefulness=$wake"
        if [ "$stay" != 0 ] && [ -z "${STUB_UNPOWERED:-}" ]; then echo "  mStayOn=true"
        else echo "  mStayOn=false"; fi ;;
      "input keyevent KEYCODE_WAKEUP")
        [ -n "${STUB_WAKE_FAIL:-}" ] && exit 1
        touch "$STUB_DEVICE/woken"; echo "wake" >> "$STUB_DEVICE/ops" ;;
      "getprop sys.boot_completed") echo 1 ;;
      *) : ;;
    esac ;;
esac
STUB
chmod +x "$WORK/bin/adb"
export PATH="$WORK/bin:$PATH"
export SERIAL=stub

TESTS=0 BAD=0
ok()  { TESTS=$((TESTS+1)); printf '  ok   %s\n' "$1"; }
bad() { TESTS=$((TESTS+1)); BAD=$((BAD+1)); printf '  FAIL %s\n' "$1"; }

# Runs a library call in a fresh subshell with the library loaded; its output goes to $WORK/out.
lib() { ( source "$HERE/lib_r007.sh"; "$@" ) > "$WORK/out" 2>&1; }
expect_ok()   { local label="$1"; shift; if lib "$@"; then ok "$label"; else bad "$label"; fi; }
expect_fail() { local label="$1"; shift; if lib "$@"; then bad "$label"; else ok "$label"; fi; }

# A store file in the shape that EncryptedSharedPreferences writes: two keysets and two encrypted entries.
STORE_XML="<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name=\"__androidx_security_crypto_encrypted_prefs_key_keyset__\">12a9017c89dd</string>
    <string name=\"ARvWAU0hJztGu9JPCFYD2+/ST84nIyK+xUvgOaZsbRT2pg==\">AT6IpST5vIhIq0Lm8Tz4Kx==</string>
    <string name=\"__androidx_security_crypto_encrypted_prefs_value_keyset__\">12880161d5a9</string>
    <string name=\"ARvWAU2YWvROlzabWFmSEm4G6aRlerLZdG3stk9i9ICFyg==\">AT6IpSQ4o5cjZZ9a</string>
</map>"
STORE="shared_prefs/applock_lockout.xml"

reset_device() {
  rm -rf "$STUB_DEVICE"; mkdir -p "$STUB_DEVICE/shared_prefs"
  : > "$STUB_DEVICE/logcat"; : > "$STUB_DEVICE/ops"
  printf '%s\n' "$STORE_XML" > "$STUB_DEVICE/$STORE"
}
store_is() { [ "$(cat "$STUB_DEVICE/$STORE" 2>/dev/null)" = "$1" ]; }

INSPECTION=$'INSTRUMENTATION_STATUS: r007_pid=777\nINSTRUMENTATION_STATUS: r007_count=3\n'
INSPECTION+=$'INSTRUMENTATION_STATUS_CODE: 7\nINSTRUMENTATION_CODE: -1'
MARKERS=$'1.0 777 777 I R007Inspect: pid=777 phase=INSPECT_BEGIN\n'
MARKERS+=$'1.1 777 777 I R007Inspect: pid=777 phase=INSPECT_END r007_count=3 r007_pid=777'

echo "inspection evidence"
reset_device; printf '%s\n' "$MARKERS" > "$STUB_DEVICE/logcat"
STUB_INSTRUMENT="$INSPECTION" expect_ok "a clean inspection is accepted" r007_inspect_checked
reset_device; printf '%s\n' "$MARKERS" > "$STUB_DEVICE/logcat"
STUB_INSTRUMENT="$INSPECTION" STUB_LOGCAT_FAIL=1 expect_fail "an unreadable logcat rejects the inspection" \
  r007_inspect_checked
reset_device
STUB_INSTRUMENT="$INSPECTION" expect_fail "missing inspector markers reject the inspection" r007_inspect_checked
reset_device
{ echo "0.9 777 777 I R007Fault: pid=777 thread=main op=READ index=0 phase=BEGIN script=Normal"
  for (( i=0; i<20000; i++ )); do echo "0.9 1 1 I R007Fault: pid=1 thread=main op=READ index=$i phase=BEGIN"; done
  printf '%s\n' "$MARKERS"; } > "$STUB_DEVICE/logcat"
STUB_INSTRUMENT="$INSPECTION" expect_fail "a graph line early in a long log rejects the inspection" \
  r007_inspect_checked
reset_device
printf '%s\n' "1.1 777 777 I R007Inspect: pid=777 phase=INSPECT_BEGIN" \
  "1.2 777 777 I R007Inspect: pid=777 phase=INSPECT_END r007_count=9" > "$STUB_DEVICE/logcat"
STUB_INSTRUMENT="$INSPECTION" expect_fail "an end marker with another count rejects the inspection" r007_inspect_checked
reset_device; printf '%s\n' "$MARKERS" > "$STUB_DEVICE/logcat"
STUB_INSTRUMENT=$'INSTRUMENTATION_STATUS: r007_pid=777\nINSTRUMENTATION_CODE: 0' \
  expect_fail "an instrumentation run that did not complete is rejected" r007_inspect_checked
reset_device; printf '%s\n' "$MARKERS" > "$STUB_DEVICE/logcat"
STUB_INSTRUMENT="$INSPECTION" STUB_INSPECT_WRITES=1 \
  expect_fail "an inspection that changes the store file is rejected" r007_inspect_checked
reset_device; printf '%s\n' "$MARKERS" > "$STUB_DEVICE/logcat"
STUB_INSTRUMENT="$INSPECTION" STUB_APP_PROC=present expect_fail "an inspection while the app runs is rejected" \
  r007_inspect_checked
reset_device; printf '%s\n' "$MARKERS" > "$STUB_DEVICE/logcat"; rm "$STUB_DEVICE/$STORE"
STUB_INSTRUMENT="$INSPECTION" expect_fail "an inspection of a store that cannot be hashed is rejected" \
  r007_inspect_checked
reset_device; printf '%s\n' "$MARKERS" > "$STUB_DEVICE/logcat"
STUB_INSTRUMENT="$INSPECTION" STUB_HASH_FAIL=1 expect_fail "a failed hash query rejects the inspection" \
  r007_inspect_checked
reset_device
printf '%s\n' "1.0 778 778 I R007Inspect: pid=778 phase=INSPECT_BEGIN" \
  "1.1 778 778 I R007Inspect: pid=778 phase=INSPECT_END r007_pid=778 r007_read_error=java.lang.SecurityException" \
  > "$STUB_DEVICE/logcat"
READ_ERROR=$'INSTRUMENTATION_STATUS: r007_pid=778\n'
READ_ERROR+=$'INSTRUMENTATION_STATUS: r007_read_error=java.lang.SecurityException\nINSTRUMENTATION_CODE: -1'
STUB_INSTRUMENT="$READ_ERROR" expect_ok "a read-error inspection with no count is accepted" r007_inspect_checked

echo "store file"
reset_device
expect_ok "the store is hashed" r007_store_hash
grep -qE '^[0-9a-f]{64}$' "$WORK/out" && ok "the store hash is one SHA-256" || bad "the store hash: $(cat "$WORK/out")"
reset_device; rm "$STUB_DEVICE/$STORE"
expect_fail "a missing store has no hash" r007_store_hash
reset_device
STUB_HASH_FAIL=1 expect_fail "a failed hash query gives no hash" r007_store_hash
reset_device
expect_ok "the store is saved" r007_store_save
cmp -s "$STUB_DEVICE/$STORE" "$STUB_DEVICE/files/r007/lockout.orig" \
  && ok "the copy equals the store" || bad "the copy differs from the store"
reset_device
STUB_BAK=present expect_fail "a store with a backup file is not saved" r007_store_save
[ ! -e "$STUB_DEVICE/files/r007/lockout.orig" ] && ok "no copy after the refusal" || bad "a copy after the refusal"
reset_device
STUB_QUERY_FAIL=1 expect_fail "a failed backup-file query refuses the save" r007_store_save
reset_device
STUB_APP_PROC=present expect_fail "the store is not saved while the app runs" r007_store_save
reset_device
expect_ok "an encrypted value is tampered" r007_store_tamper
value="AT6IpST5vIhIq0Lm8Tz4Kx=="; mid=$(( ${#value} / 2 ))
store_is "${STORE_XML/"$value"/"${value:0:mid}A${value:mid+1}"}" \
  && ok "only the middle character of the first encrypted value changes" \
  || bad "unexpected tampered store: $(cat "$STUB_DEVICE/$STORE")"
reset_device
printf '%s\n' "<map>" \
  '    <string name="__androidx_security_crypto_encrypted_prefs_key_keyset__">12a9017c89dd</string>' "</map>" \
  > "$STUB_DEVICE/$STORE"
expect_fail "a store with keysets only is not tampered" r007_store_tamper
reset_device
STUB_APP_PROC=present expect_fail "the store is not tampered while the app runs" r007_store_tamper
reset_device
( source "$HERE/lib_r007.sh"; h="$(r007_store_save)" && r007_store_tamper >/dev/null && r007_store_restore "$h" ) \
  > "$WORK/out" 2>&1 && ok "the saved store is restored after a tamper" || bad "the restore after a tamper failed"
store_is "$STORE_XML" && ok "the restored store equals the original" || bad "the restored store differs"
reset_device
( source "$HERE/lib_r007.sh"; h="$(r007_store_save)" && r007_store_tamper >/dev/null \
  && rm "$STUB_DEVICE/files/r007/lockout.orig" && r007_store_restore "$h" ) > "$WORK/out" 2>&1 \
  && bad "a restore without the copy is refused" || ok "a restore without the copy is refused"
[ -s "$STUB_DEVICE/$STORE" ] && ok "a missing copy leaves the store in place" || bad "a missing copy emptied the store"
reset_device
( source "$HERE/lib_r007.sh"; r007_store_save >/dev/null && r007_store_restore "$(printf '0%.0s' {1..64})" ) \
  > "$WORK/out" 2>&1 && bad "a restore to another hash fails" || ok "a restore to another hash fails"
reset_device
( source "$HERE/lib_r007.sh"; R007_STORE_SAVED="$(r007_store_save)" || exit 9; trap r007_restore_pending EXIT
  r007_store_tamper >/dev/null; exit 3 ) > "$WORK/out" 2>&1
store_is "$STORE_XML" && ok "an early exit restores the saved store" || bad "an early exit left the tampered store"
reset_device
( source "$HERE/lib_r007.sh"; R007_STORE_SAVED="$(r007_store_save)" || exit 9; r007_store_tamper >/dev/null
  export STUB_COPY_FAIL=1; r007_restore_pending; [ -n "$R007_STORE_SAVED" ] ) > "$WORK/out" 2>&1 \
  && ok "a failed restore keeps the restore pending" || bad "a failed restore cleared the pending restore"
reset_device
( source "$HERE/lib_r007.sh"; R007_STORE_SAVED="$(r007_store_save)" || exit 9; r007_store_tamper >/dev/null
  r007_restore_pending && [ -z "$R007_STORE_SAVED" ] ) > "$WORK/out" 2>&1 \
  && ok "a verified restore clears the pending restore" || bad "a verified restore left the restore pending"

echo "process death"
reset_device
expect_ok "an explicit absent answer confirms the death" r007_kill
reset_device
STUB_KILL_FAIL=1 expect_fail "a failed kill is not a death" r007_kill
reset_device
STUB_PROC_FAIL=1 R007_KILL_POLLS=2 expect_fail "a failed /proc query is not a death" r007_kill
reset_device
STUB_PROC_ANSWER=present R007_KILL_POLLS=2 expect_fail "a process that stays present is not a death" r007_kill
reset_device
STUB_PROC_ANSWER="" R007_KILL_POLLS=2 expect_fail "an empty answer is not a death" r007_kill
reset_device
STUB_PID=$'4242\n4343' expect_fail "two app processes are refused" r007_kill

echo "preconditions"
reset_device
expect_ok "a keyguard that is not showing is unlocked" r007_unlocked
reset_device
STUB_ACTIVITIES="    mKeyguardShowing=true" expect_fail "a showing keyguard is locked" r007_unlocked
reset_device
STUB_ACTIVITIES="" STUB_WINDOW="    isKeyguardShowing=false" \
  expect_ok "the window state is used when the activity state has no keyguard field" r007_unlocked
reset_device
STUB_ACTIVITIES="" STUB_WINDOW="    isKeyguardShowing=true" \
  expect_fail "a showing keyguard in the window state is locked" r007_unlocked
reset_device
STUB_ACTIVITIES="" STUB_WINDOW="" expect_fail "a missing keyguard field is not unlocked" r007_unlocked
reset_device
STUB_DUMPSYS_FAIL=1 expect_fail "a failed keyguard query is not unlocked" r007_unlocked
reset_device
expect_ok "a force-stop with an explicit absent answer stops the app" r007_stop_app
[ "$(cat "$STUB_DEVICE/ops")" = "force-stop" ] && ok "the app is stopped with am force-stop" \
  || bad "unexpected stop sequence: $(tr '\n' ';' < "$STUB_DEVICE/ops")"
reset_device
STUB_STOP_FAIL=1 expect_fail "a failed force-stop is not a stop" r007_stop_app
reset_device
STUB_APP_PROC=present R007_KILL_POLLS=2 expect_fail "a process that stays present is not stopped" r007_stop_app
reset_device
STUB_APP_PROC="" R007_KILL_POLLS=2 expect_fail "an empty answer is not a stop" r007_stop_app
reset_device
STUB_PIDOF_FAIL=1 R007_KILL_POLLS=2 expect_fail "a failed process query is not a stop" r007_stop_app

echo "device settings"
RECORD_PATH="/data/local/tmp/r007_settings.pending"
RECORD="$STUB_DEVICE$RECORD_PATH"
ORIG=$'global:stay_on_while_plugged_in=0\nsystem:accelerometer_rotation=1\nsystem:user_rotation=1'
seed_settings() { # the Moto G values before a run: stay-awake off, auto-rotate on, rotation 1
  mkdir -p "$STUB_DEVICE/settings"
  echo 0 > "$STUB_DEVICE/settings/global.stay_on_while_plugged_in"
  echo 1 > "$STUB_DEVICE/settings/system.accelerometer_rotation"
  echo 1 > "$STUB_DEVICE/settings/system.user_rotation"
}
setting() { if [ -f "$STUB_DEVICE/settings/$1" ]; then cat "$STUB_DEVICE/settings/$1"; else echo null; fi; }
settings_are() { # "stay-awake auto-rotate rotation"
  local now
  now="$(setting global.stay_on_while_plugged_in) $(setting system.accelerometer_rotation)"
  [ "$now $(setting system.user_rotation)" = "$1" ]
}
untouched() { ! grep -q "^put " "$STUB_DEVICE/ops" && [ ! -e "$RECORD" ] && settings_are "0 1 1"; }
# A real run opens its evidence file before it changes a setting. The settings cases share this one.
export R007_LOG_OUT="$WORK/settings.log"
mkdir -p "$WORK/unwritable.log"   # a directory: an append to it fails, also on Git Bash, where chmod is unreliable
reset_device; seed_settings
expect_ok "the settings are read" r007_settings_read
[ "$(cat "$WORK/out")" = "$ORIG" ] && ok "each setting reads as namespace:key=value" \
  || bad "unexpected settings: $(cat "$WORK/out")"
reset_device; seed_settings; rm "$STUB_DEVICE/settings/system.user_rotation"
lib r007_settings_read; grep -qx "system:user_rotation=null" "$WORK/out" \
  && ok "an unset setting reads as null" || bad "an unset setting: $(cat "$WORK/out")"
reset_device; seed_settings
STUB_SETTINGS_GET_FAIL=1 expect_fail "a failed settings read is refused" r007_settings_read
reset_device; seed_settings
STUB_SETTINGS_BAD=user_rotation expect_fail "an unexpected settings value is refused" r007_settings_read
reset_device
expect_ok "the rotation of the default display is read" r007_display_rotation
[ "$(cat "$WORK/out")" = 0 ] && ok "the default display reads rotation 0" || bad "rotation: $(cat "$WORK/out")"
reset_device
STUB_ROTATION=2 lib r007_display_rotation; [ "$(cat "$WORK/out")" = 2 ] \
  && ok "a display at 180 degrees with its natural size reads rotation 2" || bad "rotation: $(cat "$WORK/out")"
reset_device
STUB_ROTATION=1 STUB_ROTATION_DECOY=1 lib r007_display_rotation; [ "$(cat "$WORK/out")" = 1 ] \
  && ok "only the DisplayRotation field of display 0 counts" || bad "rotation: $(cat "$WORK/out")"
reset_device
STUB_ROTATION_MISSING=1 expect_fail "a missing rotation field is refused, even when display 1 has one" \
  r007_display_rotation
reset_device
STUB_ROTATION=7 expect_fail "an unexpected rotation value is refused" r007_display_rotation
reset_device
STUB_DISPLAYS_FAIL=1 expect_fail "a failed display query is refused" r007_display_rotation
stay_awake_set() { mkdir -p "$STUB_DEVICE/settings"; echo 7 > "$STUB_DEVICE/settings/global.stay_on_while_plugged_in"; }
reset_device; stay_awake_set
expect_ok "an awake, unlocked screen with stay-awake in effect passes" r007_wake_screen
grep -qx "wake" "$STUB_DEVICE/ops" && ok "the wake key is sent" \
  || bad "no wake key: $(tr '\n' ';' < "$STUB_DEVICE/ops")"
reset_device; stay_awake_set
STUB_WAKEFULNESS=Asleep expect_ok "a screen that went off before the lock is turned on again" r007_wake_screen
reset_device; stay_awake_set
STUB_WAKEFULNESS=Asleep STUB_WAKE_IGNORED=1 R007_WAKE_POLLS=2 expect_fail "a screen that stays off fails" \
  r007_wake_screen
reset_device; stay_awake_set
STUB_WAKE_FAIL=1 expect_fail "a failed wake key fails" r007_wake_screen
reset_device; stay_awake_set
STUB_POWER_FAIL=1 R007_WAKE_POLLS=2 expect_fail "a failed power query fails" r007_wake_screen
reset_device; seed_settings
expect_fail "a screen without the stay-awake setting fails" r007_wake_screen
reset_device; stay_awake_set
STUB_UNPOWERED=1 expect_fail "stay-awake on an unpowered device fails" r007_wake_screen
grep -q "no power source" "$WORK/out" && ok "the failure names the missing power" \
  || bad "the failure: $(cat "$WORK/out")"
reset_device; stay_awake_set
STUB_ACTIVITIES="    mKeyguardShowing=true" expect_fail "a keyguard that locked before the wake fails" r007_wake_screen
grep -q "unlock it" "$WORK/out" && ok "the failure asks for an unlock" || bad "the failure: $(cat "$WORK/out")"
reset_device; seed_settings
( source "$HERE/lib_r007.sh"; R007_LOG_OUT="$WORK/settings.log"; : > "$R007_LOG_OUT"; r007_settings_apply \
  && echo "## next-section" >> "$R007_LOG_OUT" ) \
  > "$WORK/out" 2>&1 && ok "the settings are recorded and changed" || bad "the apply failed: $(cat "$WORK/out")"
[ "$(cat "$RECORD" 2>/dev/null)" = "$ORIG" ] && ok "the record holds the original values" \
  || bad "unexpected record: $(cat "$RECORD" 2>/dev/null)"
settings_are "7 0 0" && ok "the screen stays on and the rotation is locked to 0" || bad "the settings did not change"
[ "$(head -2 "$STUB_DEVICE/ops" | tr '\n' ';')" = "exec-in $RECORD_PATH.tmp;mv $RECORD_PATH.tmp $RECORD_PATH;" ] \
  && [ ! -e "$RECORD.tmp" ] && ok "the record is written to a temporary file and renamed before any setting changes" \
  || bad "unexpected order: $(tr '\n' ';' < "$STUB_DEVICE/ops")"
grep -qxF "## settings-before" "$WORK/settings.log" && grep -qxF "system:user_rotation=1" "$WORK/settings.log" \
  && grep -qxF "## next-section" "$WORK/settings.log" \
  && ok "the evidence holds the original values, and the next section starts on its own line" \
  || bad "the evidence lines: $(tr '\n' ';' < "$WORK/settings.log")"
reset_device; seed_settings; mkdir -p "$(dirname "$RECORD")"; echo "global:stay_on_while_plugged_in=5" > "$RECORD"
expect_fail "a record of an earlier run stops the run" r007_settings_apply
! grep -q "^put " "$STUB_DEVICE/ops" && [ "$(cat "$RECORD")" = "global:stay_on_while_plugged_in=5" ] \
  && ok "the earlier record and the settings stay unchanged" || bad "the earlier record or the settings changed"
reset_device; seed_settings
STUB_RECORD_QUERY_FAIL=1 expect_fail "a failed record query stops the run" r007_settings_apply
untouched && ok "no record and no setting change after the failed query" || bad "a change after the failed query"
reset_device; seed_settings
STUB_SETTINGS_GET_FAIL=1 expect_fail "a failed capture stops the run" r007_settings_apply
untouched && ok "no record and no setting change after the failed capture" || bad "a change after the failed capture"
reset_device; seed_settings
STUB_WRITE_FAIL=1 expect_fail "a failed record write stops the run" r007_settings_apply
untouched && ok "no record and no setting change after the failed record write" \
  || bad "a change after the failed record write"
reset_device; seed_settings
( unset R007_LOG_OUT; source "$HERE/lib_r007.sh"; r007_settings_apply ) > "$WORK/out" 2>&1 \
  && bad "an apply without an evidence file is refused" || ok "an apply without an evidence file is refused"
untouched && ! grep -q "^exec-in " "$STUB_DEVICE/ops" && ok "no record and no setting change without an evidence file" \
  || bad "a change without an evidence file"
reset_device; seed_settings
( source "$HERE/lib_r007.sh"; R007_LOG_OUT="$WORK/unwritable.log"; r007_settings_apply; rc=$?
  r007_settings_restore_owned; echo "rc=$rc fails=$FAIL_COUNT" ) > "$WORK/out" 2>&1
grep -qx "rc=1 fails=1" "$WORK/out" && ok "an unsaved settings-before section fails the apply" \
  || bad "an unsaved settings-before section: $(tr '\n' ';' < "$WORK/out")"
grep -qx "mv $RECORD_PATH.tmp $RECORD_PATH" "$STUB_DEVICE/ops" && untouched \
  && ok "the record is removed and no setting changes after the unsaved settings-before section" \
  || bad "a record or a setting change after the unsaved settings-before section"
reset_device; seed_settings
( source "$HERE/lib_r007.sh"; R007_LOG_OUT="$WORK/unwritable.log"; export STUB_RECORD_RM_FAIL=1
  trap 'r007_settings_restore_owned; echo "fails=$FAIL_COUNT"' EXIT
  r007_settings_apply; echo "rc=$? owned=$R007_SETTINGS_OWNED" ) > "$WORK/out" 2>&1
grep -qx "rc=1 owned=1" "$WORK/out" && ok "a record that is not removed after an aborted apply stays owned by the run" \
  || bad "a record that is not removed: $(tr '\n' ';' < "$WORK/out")"
grep -qx "put global stay_on_while_plugged_in 0" "$STUB_DEVICE/ops" && settings_are "0 1 1" \
  && [ "$(cat "$RECORD" 2>/dev/null)" = "$ORIG" ] && grep -qx "fails=2" "$WORK/out" \
  && ok "the exit cleanup restores the unchanged values and keeps the unsaved record" \
  || bad "the exit cleanup after the aborted apply: $(tr '\n' ';' < "$WORK/out")"
rm -f "$WORK/recover.log"
R007_LOG_OUT="$WORK/recover.log" bash "$HERE/restore_settings.sh" > "$WORK/out" 2>&1 && [ ! -e "$RECORD" ] \
  && grep -qxF "match=yes" "$WORK/recover.log" && ok "restore_settings.sh removes the record of the aborted apply" \
  || bad "restore_settings.sh after the aborted apply: $(cat "$WORK/out")"
reset_device; seed_settings
STUB_WRITE_PARTIAL=1 STUB_RECORD_RM_FAIL=1 expect_fail "a partly written record stops the run" r007_settings_apply
untouched && ok "a partly written record is never published, even when its removal fails" \
  || bad "a partly written record: $(cat "$RECORD" 2>/dev/null)"
expect_ok "a temporary file of an aborted apply does not stop the next run" r007_settings_apply
[ "$(cat "$RECORD" 2>/dev/null)" = "$ORIG" ] && ok "the next run records the complete original values" \
  || bad "the record of the next run: $(cat "$RECORD" 2>/dev/null)"
reset_device; seed_settings
STUB_WRITE_CORRUPT=1 STUB_RECORD_RM_FAIL=1 expect_fail "a record that reads back wrong stops the run" \
  r007_settings_apply
untouched && ! grep -q "^mv " "$STUB_DEVICE/ops" \
  && ok "a record that reads back wrong is never published, even when its removal fails" \
  || bad "a record that reads back wrong: $(cat "$RECORD" 2>/dev/null)"
reset_device; seed_settings
STUB_RECORD_MV_FAIL=1 expect_fail "a failed record rename stops the run" r007_settings_apply
untouched && [ ! -e "$RECORD.tmp" ] \
  && ok "no record, no temporary file, and no setting change after the failed rename" \
  || bad "a change after the failed rename"
reset_device; seed_settings
STUB_RECORD_MV_LOST=1 expect_fail "a rename that does not happen stops the run" r007_settings_apply
untouched && ok "no setting changes without a record on the device" || bad "a setting changed without a record"
reset_device; seed_settings
STUB_ROTATION=1 R007_ROTATION_POLLS=2 expect_fail "a display that stays rotated fails the apply" r007_settings_apply
[ -e "$RECORD" ] && ok "the record stays for the restore after the failed apply" || bad "the record is gone"
reset_device; seed_settings
STUB_ROTATION=2 R007_ROTATION_POLLS=2 expect_fail "a display at 180 degrees fails the apply" r007_settings_apply
reset_device; seed_settings
STUB_ROTATION_MISSING=1 R007_ROTATION_POLLS=2 expect_fail "a display without a rotation field fails the apply" \
  r007_settings_apply
reset_device; seed_settings
STUB_SETTINGS_IGNORE=accelerometer_rotation expect_fail "a setting write that does not take effect fails the apply" \
  r007_settings_apply
[ -e "$RECORD" ] && [ "$(setting system.accelerometer_rotation)" = 1 ] \
  && ok "the record stays for the restore after the ignored write" || bad "the record is gone after the ignored write"
reset_device; seed_settings
( source "$HERE/lib_r007.sh"; R007_LOG_OUT="$WORK/settings.log"; : > "$R007_LOG_OUT"
  r007_settings_apply && r007_settings_restore ) > "$WORK/out" 2>&1 \
  && ok "the settings are restored" || bad "the restore failed: $(cat "$WORK/out")"
settings_are "0 1 1" && [ ! -e "$RECORD" ] && ok "the original values are back and the record is removed" \
  || bad "the restore left $(setting global.stay_on_while_plugged_in) $(setting system.user_rotation) or a record"
grep -qxF "system:user_rotation recorded=1 now=1" "$WORK/settings.log" && grep -qxF "match=yes" "$WORK/settings.log" \
  && ok "the evidence holds the comparison" || bad "the evidence lacks the comparison"
reset_device; seed_settings; rm "$STUB_DEVICE/settings/system.user_rotation"
( source "$HERE/lib_r007.sh"; r007_settings_apply && r007_settings_restore ) > "$WORK/out" 2>&1 \
  && settings_are "0 1 null" && grep -qx "delete system user_rotation" "$STUB_DEVICE/ops" \
  && ok "an unset setting is deleted again" || bad "an unset setting was not deleted again"
reset_device; seed_settings
( source "$HERE/lib_r007.sh"; R007_LOG_OUT="$WORK/settings.log"; : > "$R007_LOG_OUT"; r007_settings_apply || exit 9
  export STUB_SETTINGS_IGNORE=user_rotation; r007_settings_restore ) > "$WORK/out" 2>&1 \
  && bad "a setting that does not read back as recorded fails the restore" \
  || ok "a setting that does not read back as recorded fails the restore"
[ -e "$RECORD" ] && grep -qxF "match=no" "$WORK/settings.log" \
  && ok "the record stays and the evidence shows the mismatch" || bad "the mismatch was not kept"
reset_device; seed_settings
( source "$HERE/lib_r007.sh"; r007_settings_apply || exit 9
  R007_LOG_OUT="$WORK/unwritable.log"; r007_settings_restore; echo "rc=$? fails=$FAIL_COUNT" ) > "$WORK/out" 2>&1
grep -qx "rc=1 fails=1" "$WORK/out" && ! grep -q "PASS" "$WORK/out" \
  && ok "an unsaved comparison fails the restore" || bad "an unsaved comparison: $(tr '\n' ';' < "$WORK/out")"
settings_are "0 1 1" && [ "$(cat "$RECORD" 2>/dev/null)" = "$ORIG" ] \
  && ok "the settings are restored, and the record stays after the unsaved comparison" \
  || bad "after the unsaved comparison: $(setting global.stay_on_while_plugged_in) $(setting system.user_rotation)"
rm -f "$WORK/recover.log"
R007_LOG_OUT="$WORK/recover.log" bash "$HERE/restore_settings.sh" > "$WORK/out" 2>&1 && [ ! -e "$RECORD" ] \
  && grep -qxF "match=yes" "$WORK/recover.log" \
  && ok "restore_settings.sh saves the comparison and removes the kept record" \
  || bad "restore_settings.sh after the unsaved comparison: $(cat "$WORK/out")"
reset_device; seed_settings
( source "$HERE/lib_r007.sh"; r007_settings_apply || exit 9
  unset R007_LOG_OUT; r007_settings_restore; echo "rc=$? fails=$FAIL_COUNT" ) > "$WORK/out" 2>&1
grep -qx "rc=1 fails=1" "$WORK/out" && settings_are "0 1 1" && [ -e "$RECORD" ] \
  && ok "a restore without an evidence file restores the settings, keeps the record, and fails" \
  || bad "a restore without an evidence file: $(tr '\n' ';' < "$WORK/out")"
reset_device; seed_settings; mkdir -p "$(dirname "$RECORD")"; echo "garbage" > "$RECORD"
expect_fail "an invalid record is not applied" r007_settings_restore
grep -qF "remove $RECORD_PATH" "$WORK/out" && ok "the failure for an invalid record gives the manual recovery" \
  || bad "the failure for an invalid record: $(cat "$WORK/out")"
reset_device; seed_settings; mkdir -p "$(dirname "$RECORD")"; echo "global:stay_on_while_plugged_in=0" > "$RECORD"
expect_fail "a record without every setting is not applied" r007_settings_restore
! grep -q "^put " "$STUB_DEVICE/ops" && settings_are "0 1 1" && ok "an incomplete record changes nothing" \
  || bad "an incomplete record changed a setting"
reset_device; seed_settings; mkdir -p "$(dirname "$RECORD")"; printf '%s\n' "$ORIG" > "$RECORD"
expect_ok "a restore that this run does not own does nothing" r007_settings_restore_owned
[ -e "$RECORD" ] && ! grep -q "^put " "$STUB_DEVICE/ops" && ok "the earlier record stays for restore_settings.sh" \
  || bad "the earlier record was used"
reset_device; seed_settings
( source "$HERE/lib_r007.sh"; trap r007_settings_restore_owned EXIT; r007_settings_apply || exit 9; exit 3 ) \
  > "$WORK/out" 2>&1
settings_are "0 1 1" && [ ! -e "$RECORD" ] && ok "an early exit restores the settings" \
  || bad "an early exit left the settings changed"
reset_device; seed_settings
( source "$HERE/lib_r007.sh"; r007_settings_apply ) > /dev/null 2>&1   # a crashed run: changed settings, a record
R007_LOG_OUT="$WORK/restore.log" bash "$HERE/restore_settings.sh" > "$WORK/out" 2>&1 \
  && settings_are "0 1 1" && [ ! -e "$RECORD" ] \
  && ok "restore_settings.sh restores the record of a crashed run" || bad "restore_settings.sh: $(cat "$WORK/out")"
reset_device; seed_settings
R007_LOG_OUT="$WORK/restore.log" bash "$HERE/restore_settings.sh" > "$WORK/out" 2>&1 && untouched \
  && ok "restore_settings.sh without a record changes nothing" || bad "restore_settings.sh: $(cat "$WORK/out")"
unset R007_LOG_OUT

echo "run end"
# Runs BODY in a subshell with the exit handling of p1_validate.sh. The output goes to $WORK/out, the exit status to
# $WORK/rc, and the evidence to $WORK/end.log.
run_end() { # body
  rm -f "$WORK/end.log"
  ( source "$HERE/lib_r007.sh"; R007_LOG_OUT="$WORK/end.log"; r007_trap_finish "stub run"; eval "$1" ) \
    > "$WORK/out" 2>&1
  echo "$?" > "$WORK/rc"
}
plain_out() { sed 's/\x1b\[[0-9;]*m//g' "$WORK/out"; }
ended() { # status passed failed ; the exit status, and one summary with these counts on the last line
  [ "$(cat "$WORK/rc")" = "$1" ] && [ "$(plain_out | tail -1)" = "stub run: $2 passed, $3 failed" ] \
    && [ "$(plain_out | grep -c '^stub run: ')" = 1 ]
}
unexplained() { plain_out | grep -q "the run stopped with exit status"; }
reset_device; seed_settings
run_end 'r007_settings_apply || r007_stop_run; pass "a case"; exit 0'
ended 0 2 0 && ! unexplained && settings_are "0 1 1" && [ ! -e "$RECORD" ] \
  && ok "a clean run restores the settings and ends with status 0 and one summary" \
  || bad "a clean run: $(plain_out | tr '\n' ';')"
restored="$(plain_out | grep -n 'PASS the device settings are restored' | cut -d: -f1)"
evidence="$(plain_out | grep -n '  evidence: ' | cut -d: -f1)"
[ -n "$restored" ] && [ -n "$evidence" ] && [ "$restored" -lt "$evidence" ] \
  && ok "the evidence location and the summary follow the cleanup" || bad "the order: $(plain_out | tr '\n' ';')"
reset_device
run_end 'fail "a case"; exit 0'
ended 1 0 1 && ! unexplained && ok "a recorded failure gives status 1 after a normal end" \
  || bad "a recorded failure: $(plain_out | tr '\n' ';')"
reset_device
run_end 'fail "a check"; r007_stop_run'
ended 1 0 1 && ! unexplained && ok "a stop after a counted failure keeps status 1 and adds no failure" \
  || bad "a counted stop: $(plain_out | tr '\n' ';')"
reset_device
run_end 'exit 3'
ended 3 0 1 && unexplained && ok "an unexplained exit status is reported and kept" \
  || bad "an unexplained exit: $(plain_out | tr '\n' ';')"
reset_device
run_end 'fail "a case"; exit 5'
ended 5 0 2 && unexplained && ok "an unexplained exit after a counted failure is reported" \
  || bad "an unexplained exit after a failure: $(plain_out | tr '\n' ';')"
reset_device
run_end 'r007_stop_run'
ended 1 0 1 && unexplained && ok "a stop without a counted failure is reported" \
  || bad "a stop without a failure: $(plain_out | tr '\n' ';')"
reset_device; seed_settings
run_end 'r007_settings_apply || r007_stop_run; kill -INT $BASHPID; sleep 5'
ended 130 1 1 && unexplained && settings_are "0 1 1" && [ ! -e "$RECORD" ] \
  && ok "an interrupt is reported, the settings are restored, and status 130 stays" \
  || bad "an interrupt: rc $(cat "$WORK/rc"): $(plain_out | tr '\n' ';')"
reset_device; seed_settings
run_end 'r007_settings_apply || r007_stop_run; export STUB_SETTINGS_IGNORE=user_rotation; exit 0'
ended 1 0 1 && [ "$(grep -c '^## settings-restore' "$WORK/end.log")" = 1 ] \
  && [ "$(grep -cx 'put global stay_on_while_plugged_in 0' "$STUB_DEVICE/ops")" = 1 ] && [ -e "$RECORD" ] \
  && ok "a failed settings restore runs once, gives status 1, and keeps the record" \
  || bad "a failed settings restore: $(plain_out | tr '\n' ';')"
reset_device; seed_settings
run_end 'R007_STORE_SAVED="$(r007_store_save)" || r007_stop_run; r007_store_tamper >/dev/null
  r007_settings_apply || r007_stop_run; export STUB_COPY_FAIL=1; exit 0'
ended 1 1 1 && ! store_is "$STORE_XML" && settings_are "0 1 1" && [ ! -e "$RECORD" ] \
  && ok "a failed store restore does not stop the settings restore" \
  || bad "a failed store restore: $(plain_out | tr '\n' ';')"
reset_device
run_end 'R007_STORE_SAVED="$(r007_store_save)" || r007_stop_run; r007_store_tamper >/dev/null; exit 0'
ended 0 1 0 && store_is "$STORE_XML" && ok "the exit cleanup restores a pending store copy" \
  || bad "a pending store copy: $(plain_out | tr '\n' ';')"

echo "fault rules"
for rule in "READ * Throw" "READ 0 HoldThenThrow" "WRITE 3 HoldBeforeCommit" \
  "WRITE 3 HoldBeforeCommit ReturnFalseBeforeCommit" "WRITE * CommitThenReportFalse"; do
  expect_ok "valid rule: $rule" r007_valid_rule "$rule"
done
for rule in "WRITE 0 Explode" "READ 0 HoldBeforeCommit" "WRITE 0 Normal Throw" \
  "WRITE 0 HoldBeforeCommit CommitThenHold" "WRITE -1 Normal" "READ 0 Throw Normal" "DELETE 0 Normal" \
  "WRITE 0" "WRITE 0 HoldBeforeCommit Normal Normal"; do
  expect_fail "invalid rule: $rule" r007_valid_rule "$rule"
done
reset_device
expect_fail "an invalid rule is not published" r007_set_faults "WRITE 0 Explode"
[ ! -e "$STUB_DEVICE/files/r007/faults" ] \
  && ok "no script file after the rejected rule" || bad "a script file after the rejected rule"

echo "atomic publication"
reset_device
expect_ok "a valid script is published" r007_set_faults "WRITE 0 Throw" "READ * Normal"
[ "$(cat "$STUB_DEVICE/files/r007/faults" 2>/dev/null)" = $'WRITE 0 Throw\nREAD * Normal' ] \
  && ok "the published script holds the rules" || bad "the published script holds the rules"
[ ! -e "$STUB_DEVICE/files/r007/faults.tmp" ] \
  && ok "the temporary file is renamed away" || bad "the temporary file remains"
[ "$(cat "$STUB_DEVICE/ops")" = $'exec-in files/r007/faults.tmp\nmv files/r007/faults' ] \
  && ok "the script is written to a temporary file, then renamed" \
  || bad "unexpected write sequence: $(tr '\n' ';' < "$STUB_DEVICE/ops")"
reset_device
STUB_WRITE_FAIL=1 expect_fail "a failed write is reported" r007_set_faults "WRITE 0 Throw"
reset_device
expect_ok "an empty script is published for a clear" r007_set_faults
[ -e "$STUB_DEVICE/files/r007/faults" ] && [ ! -s "$STUB_DEVICE/files/r007/faults" ] \
  && ok "the cleared script is an empty file" || bad "the cleared script is an empty file"

echo "evidence"
reset_device
( unset R007_LOG_OUT; source "$HERE/lib_r007.sh"; r007_save_log "case" ) > "$WORK/out" 2>&1 \
  && bad "saving without an evidence file is refused" || ok "saving without an evidence file is refused"
reset_device
( R007_LOG_OUT="$WORK/evidence/run.log"; source "$HERE/lib_r007.sh"; r007_evidence_init "stub-run" ) \
  > "$WORK/out" 2>&1 && [ -s "$WORK/evidence/run.log" ] \
  && ok "the evidence file is created with a header" || bad "the evidence file is created with a header"
grep -qE '^# utc=[^ ]+ host_rev=[0-9a-f]{40} host_changed_files=[0-9]+$' "$WORK/evidence/run.log" \
  && ok "the header records the host revision and the changed-file count" \
  || bad "the header lacks the host revision: $(grep '^# utc=' "$WORK/evidence/run.log")"
reset_device
printf '%s\n' '1.0 5 5 I R007Fault: pid=5 thread=main op=SCRIPT phase=ERROR message="line 1"' > "$STUB_DEVICE/logcat"
R007_LOG_OUT="$WORK/evidence/run.log" expect_fail "a script error fails the case" r007_save_log "case"
reset_device
STUB_LOGCAT_FAIL=1 R007_LOG_OUT="$WORK/evidence/run.log" expect_fail "an unreadable logcat fails the case" \
  r007_save_log "case"
( unset R007_LOG_OUT; source "$HERE/lib_r007.sh"; r007_evidence_init "stub-run" && printf '%s' "$R007_LOG_OUT" ) \
  > "$WORK/default" 2>/dev/null
default_log="$(tail -1 "$WORK/default")"
case "$default_log" in
  */build/r007-evidence/stub-run-stub-*.log) [ -s "$default_log" ] && ok "the default evidence file is created" \
    || bad "the default evidence file is created"; rm -f "$default_log" ;;
  *) bad "the default evidence path: $default_log" ;;
esac

printf '\n%d tests, %d failed\n' "$TESTS" "$BAD"
[ "$BAD" -eq 0 ]
