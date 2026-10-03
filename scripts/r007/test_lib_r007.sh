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

# The stub adb. The app's private directory is $STUB_DEVICE; logcat reads $STUB_DEVICE/logcat, and the events buffer
# is $STUB_DEVICE/events.
cat > "$WORK/bin/adb" <<'STUB'
#!/usr/bin/env bash
[ "${1:-}" = -s ] && shift 2
cmd="$1"; shift
case "$cmd" in
  # With STUB_OFFLINE, adb cannot see the device. After a reboot, the first STUB_REBOOT_ABSENT state reads fail, as on a
  # phone that connects adb only after the unlock.
  get-state)
    [ -n "${STUB_OFFLINE:-}" ] && { echo "error: device not found" >&2; exit 1; }
    absent="$(cat "$STUB_DEVICE/absent" 2>/dev/null || echo 0)"
    if [ "$absent" -gt 0 ]; then
      echo $(( absent - 1 )) > "$STUB_DEVICE/absent"; echo "error: device not found" >&2; exit 1
    fi
    echo device ;;
  # A reboot gives a new boot id only with STUB_REBOOT_NEW_ID. With STUB_REBOOT_LAG, that many boot id reads after the
  # reboot still give the old id, as a phone that still shuts down. The first STUB_REBOOT_LOCKED keyguard reads after
  # the reboot show a locked device.
  reboot)
    if [ -n "${STUB_REBOOT_NEW_ID:-}" ]; then
      echo "$STUB_REBOOT_NEW_ID" > "$STUB_DEVICE/boot_id.next"; echo "${STUB_REBOOT_LAG:-0}" > "$STUB_DEVICE/boot_lag"
    fi
    echo "${STUB_REBOOT_ABSENT:-0}" > "$STUB_DEVICE/absent"; echo "${STUB_REBOOT_LOCKED:-0}" > "$STUB_DEVICE/locked"
    echo "reboot" >> "$STUB_DEVICE/ops" ;;
  logcat)
    # Like the real adb, logcat waits for a device that is not connected (here for 20 s).
    [ -n "${STUB_OFFLINE:-}" ] && { echo "- waiting for device -" >&2; sleep 20; exit 1; }
    [ -n "${STUB_LOGCAT_FAIL:-}" ] && { echo "error: device offline" >&2; exit 1; }
    case " $* " in
      *" -c "*) : > "$STUB_DEVICE/logcat" ;;
      *" -b events "*) cat "$STUB_DEVICE/events" ;;
      *) cat "$STUB_DEVICE/logcat" ;;
    esac ;;
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
      # A timed tap of r007_tap: the device times and the exit status of `input tap` (STUB_TAP_TIMES).
      "s=\$(date +%s%3N); input tap "*)
        echo "tap $(sed -E 's/.*input tap ([0-9]+ [0-9]+).*/\1/' <<< "$line")" >> "$STUB_DEVICE/ops"
        echo "${STUB_TAP_TIMES-1000 1080 0}" ;;
      # The background tap with a kill of r007_submit_kill: the times, the kill status, and the tap status, then the
      # stat lines of the main thread before the tap and at the kill (13 CPU ticks apart by default).
      "s=\$(date +%s%3N); a=\$(cat /proc/"*)
        echo "tap $(sed -E 's/.*input tap ([0-9]+ [0-9]+).*/\1/' <<< "$line")" >> "$STUB_DEVICE/ops"
        echo "kill" >> "$STUB_DEVICE/ops"
        echo "${STUB_KILL_TIMES-1000 1350 1380 0 0}"
        echo "${STUB_STAT_BEFORE-4242 (com.applock) S 1025 1025 0 0 -1 4194624 18238 0 1314 0 200 27 0 0 10}"
        echo "${STUB_STAT_KILL-4242 (com.applock) R 1025 1025 0 0 -1 4194624 18238 0 1314 0 210 30 0 0 10}" ;;
      "date +%s%3N") echo "${STUB_NOW_MS-1790000000000}" ;;
      # A dump writes STUB_UI, or the file STUB_UI_FILE for a dump too large for an environment variable. A dump with
      # STUB_DUMP_FAIL writes no file, as a dump without an idle state.
      "uiautomator dump "*)
        sleep "${STUB_DUMP_DELAY:-0}"
        [ -n "${STUB_DUMP_FAIL:-}" ] && { echo "ERROR: could not get idle state."; exit 0; }
        mkdir -p "$STUB_DEVICE/sdcard"
        if [ -n "${STUB_UI_FILE:-}" ]; then cat "$STUB_UI_FILE"; else printf '%s\n' "${STUB_UI-}"; fi \
          > "$STUB_DEVICE/sdcard/e2e_ui.xml" ;;
      "cat /sdcard/e2e_ui.xml") cat "$STUB_DEVICE/sdcard/e2e_ui.xml" ;;
      "rm -f /sdcard/e2e_ui.xml") rm -f "$STUB_DEVICE/sdcard/e2e_ui.xml" ;;
      # A release of a held operation (r007_release); it fails with STUB_RELEASE_FAIL.
      "run-as "*"/release && touch "*)
        [ -n "${STUB_RELEASE_FAIL:-}" ] && exit 1; echo "release" >> "$STUB_DEVICE/ops" ;;
      "run-as "*" kill -9 "*) [ -n "${STUB_KILL_FAIL:-}" ] && exit 1; echo "kill" >> "$STUB_DEVICE/ops" ;;
      # The boot id read fails with STUB_BOOT_FAIL, and gives an empty line with STUB_BOOT_EMPTY.
      "cat /proc/sys/kernel/random/boot_id")
        [ -n "${STUB_BOOT_FAIL:-}" ] && exit 1
        [ -n "${STUB_BOOT_EMPTY:-}" ] && { echo; exit 0; }
        if [ -e "$STUB_DEVICE/boot_id.next" ]; then
          lag="$(cat "$STUB_DEVICE/boot_lag")"
          if [ "$lag" -le 0 ]; then mv "$STUB_DEVICE/boot_id.next" "$STUB_DEVICE/boot_id"
          else echo $(( lag - 1 )) > "$STUB_DEVICE/boot_lag"; fi
        fi
        cat "$STUB_DEVICE/boot_id" 2>/dev/null || echo "boot-1" ;;
      "run-as "*" id") echo "uid=10467(u0_a467) gid=10467(u0_a467)" ;;
      "pm list packages "*) echo "package:com.applock.test" ;;
      "run-as "*" chmod "*) set -- $line; echo "chmod $4 $5" >> "$STUB_DEVICE/ops" ;;
      "wm size") echo "Physical size: 720x1604" ;;
      "run-as "*" sh files/r007/sweep.sh "*) echo "${STUB_SWEEP_ANSWER-killed}" ;;
      "input tap "*) echo "tap ${line#input tap }" >> "$STUB_DEVICE/ops" ;;
      *"/proc/"*) [ -n "${STUB_PROC_FAIL:-}" ] && exit 1; echo "${STUB_PROC_ANSWER-absent}" ;;
      "pidof "*)
        # STUB_PID_UNBOUND is the pid after the detector grant is removed (a process that did not stay).
        if [ -n "${STUB_PID_UNBOUND:-}" ] && ! grep -qs AppDetectionService \
          "$STUB_DEVICE/settings/secure.enabled_accessibility_services"; then echo "$STUB_PID_UNBOUND"
        else echo "${STUB_PID:-4242}"; fi ;;
      # With STUB_APP_PROC=until-stop, an app process runs until the first force-stop.
      "if pidof "*)
        [ -n "${STUB_PIDOF_FAIL:-}" ] && exit 1
        if [ "${STUB_APP_PROC-}" = until-stop ]; then
          if grep -qx force-stop "$STUB_DEVICE/ops"; then echo absent; else echo present; fi
        else echo "${STUB_APP_PROC-absent}"; fi ;;
      "am force-stop "*) [ -n "${STUB_STOP_FAIL:-}" ] && exit 1; echo "force-stop" >> "$STUB_DEVICE/ops" ;;
      "dumpsys activity activities")
        [ -n "${STUB_DUMPSYS_FAIL:-}" ] && exit 1
        locked="$(cat "$STUB_DEVICE/locked" 2>/dev/null || echo 0)"
        if [ "$locked" -gt 0 ]; then
          echo $(( locked - 1 )) > "$STUB_DEVICE/locked"; printf '  KeyguardController:\n    mKeyguardShowing=true\n'
        else printf '  KeyguardController:\n%s\n' "${STUB_ACTIVITIES-    mKeyguardShowing=false}"; fi ;;
      "dumpsys window") [ -n "${STUB_DUMPSYS_FAIL:-}" ] && exit 1; printf '%s\n' "${STUB_WINDOW-}" ;;
      *".bak ]; then echo present"*) [ -n "${STUB_QUERY_FAIL:-}" ] && exit 1; echo "${STUB_BAK-absent}" ;;
      "run-as "*"then sha256sum "*"else echo absent"*)
        [ -n "${STUB_STATE_FAIL:-}" ] && exit 1
        f="$(sed -E 's/.*\[ -e ([^ ]+) \].*/\1/' <<< "$line")"
        if [ -e "$STUB_DEVICE/$f" ]; then h="$(sha256sum < "$STUB_DEVICE/$f")"; echo "${h%% *}  $f"
        else echo absent; fi ;;
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
        cat "$STUB_DEVICE/$src" > "$STUB_DEVICE/$dst" 2>/dev/null || exit 1
        echo "copy $src $dst" >> "$STUB_DEVICE/ops" ;;
      "run-as "*" cat "*) cat "$STUB_DEVICE/${line##* }" ;;
      "run-as "*" mv -f "*)
        set -- $line
        mv -f "$STUB_DEVICE/${@: -2:1}" "$STUB_DEVICE/${@: -1}" && echo "mv ${@: -1}" >> "$STUB_DEVICE/ops" ;;
      "am instrument "*"-e r007 fixture "*)
        echo "instrument fixture" >> "$STUB_DEVICE/ops"; printf '%s\n' "${STUB_FIXTURE_OUT:-}" ;;
      "am instrument "*)
        echo "instrument" >> "$STUB_DEVICE/ops"
        [ -n "${STUB_INSPECT_WRITES:-}" ] \
          && echo "<!-- rewritten -->" >> "$STUB_DEVICE/shared_prefs/applock_lockout.xml"
        printf '%s\n' "${STUB_INSTRUMENT:-}" ;;
      "run-as "*" rm -f "*) rm -f "$STUB_DEVICE/${line##* }" ;;
      "run-as "*" rm -rf "*) set -- $line; shift 4; for f in "$@"; do rm -rf "${STUB_DEVICE:?}/$f"; done ;;
      # The detector is bound while the service list names it and accessibility_enabled is 1.
      "dumpsys accessibility")
        [ -n "${STUB_A11Y_FAIL:-}" ] && exit 1
        [ -n "${STUB_A11Y_NO_LINE:-}" ] && { echo "ACCESSIBILITY MANAGER"; exit 0; }
        svc="$(cat "$STUB_DEVICE/settings/secure.enabled_accessibility_services" 2>/dev/null)"
        on="$(cat "$STUB_DEVICE/settings/secure.accessibility_enabled" 2>/dev/null)"
        state="${STUB_A11Y_STUCK:-}"
        if [ -z "$state" ]; then
          state=unbound
          case "$svc" in *AppDetectionService*) [ "$on" = 1 ] && state=bound ;; esac
        fi
        if [ "$state" = bound ]; then
          echo "    Bound services:{Service[label=App Lock protection, feedbackType[FEEDBACK_GENERIC]]}"
        else echo "    Bound services:{}"; fi
        echo "    Enabled services:{{$svc}}" ;;
      # Device settings live in $STUB_DEVICE/settings/<namespace>.<key>; a missing file is an unset setting.
      "settings get "*)
        [ -n "${STUB_SETTINGS_GET_FAIL:-}" ] && exit 1
        set -- $line; [ "${STUB_SETTINGS_BAD:-}" = "$4" ] && { echo "garbage"; exit 0; }
        if [ -f "$STUB_DEVICE/settings/$3.$4" ]; then cat "$STUB_DEVICE/settings/$3.$4"; else echo null; fi ;;
      "settings put "*)
        # The device shell removes the single quotes around the value, so '' is an empty string.
        [ -n "${STUB_SETTINGS_PUT_FAIL:-}" ] && exit 1
        set -- $line; mkdir -p "$STUB_DEVICE/settings"; v="${5-}"; v="${v//\'/}"
        [ "${STUB_SETTINGS_IGNORE:-}" = "$4" ] || echo "$v" > "$STUB_DEVICE/settings/$3.$4"
        echo "put $3 $4 $v" >> "$STUB_DEVICE/ops" ;;
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
  : > "$STUB_DEVICE/logcat"; : > "$STUB_DEVICE/events"; : > "$STUB_DEVICE/ops"
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
reset_device
printf '%s\n' "0.5 4242 4242 I R007Fault: pid=4242 thread=main op=INIT phase=CREATED" "$MARKERS" > "$STUB_DEVICE/logcat"
STUB_INSTRUMENT="$INSPECTION" STUB_APP_PROC=present \
  expect_fail "an inspection while an app process that used the store runs is rejected" r007_inspect_checked
! grep -q "force-stop" "$STUB_DEVICE/ops" && ok "an app process that used the store is not stopped before the refusal" \
  || bad "the process that used the store: $(tr '\n' ';' < "$STUB_DEVICE/ops")"
reset_device; printf '%s\n' "$MARKERS" > "$STUB_DEVICE/logcat"; rm -f "$WORK/quiesce.log"
R007_LOG_OUT="$WORK/quiesce.log" STUB_INSTRUMENT="$INSPECTION" STUB_APP_PROC=until-stop \
  expect_ok "an app process that did not use the store is stopped, and the inspection runs" r007_inspect_checked
grep -qx "force-stop" "$STUB_DEVICE/ops" && grep -qx "## quiesce stopped_pid=4242 store_lines=0" "$WORK/quiesce.log" \
  && ok "the stop of an app process before an inspection goes to the evidence file" \
  || bad "the quiesce evidence: $(cat "$WORK/quiesce.log" 2>/dev/null)"
reset_device; printf '%s\n' "$MARKERS" > "$STUB_DEVICE/logcat"
STUB_INSTRUMENT="$INSPECTION" STUB_APP_PROC=present R007_KILL_POLLS=2 \
  expect_fail "an app process that does not stop is rejected" r007_inspect_checked
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
ORIG+=$'\nsecure:enabled_accessibility_services=null\nsecure:accessibility_enabled=0'
seed_settings() { # the Moto G values before a run: stay-awake off, auto-rotate on, rotation 1, no accessibility
  mkdir -p "$STUB_DEVICE/settings"
  echo 0 > "$STUB_DEVICE/settings/global.stay_on_while_plugged_in"
  echo 1 > "$STUB_DEVICE/settings/system.accelerometer_rotation"
  echo 1 > "$STUB_DEVICE/settings/system.user_rotation"
  echo 0 > "$STUB_DEVICE/settings/secure.accessibility_enabled"
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
SERVICES="com.applock/com.applock.applocker.service.AppDetectionService:com.other/.Reader\$Service"
services_file="$STUB_DEVICE/settings/secure.enabled_accessibility_services"
reset_device; seed_settings; echo "$SERVICES" > "$services_file"
lib r007_settings_read; grep -qxF "secure:enabled_accessibility_services=$SERVICES" "$WORK/out" \
  && ok "a list of accessibility services reads as a string value" || bad "the services value: $(cat "$WORK/out")"
reset_device; seed_settings; echo "" > "$services_file"
lib r007_settings_read; grep -qx "secure:enabled_accessibility_services=" "$WORK/out" \
  && ok "an empty list of accessibility services reads as an empty value" || bad "the empty list: $(cat "$WORK/out")"
reset_device; seed_settings; echo "com.a/.B com.c/.D" > "$services_file"
expect_fail "a list of accessibility services with a space is refused" r007_settings_read
reset_device; seed_settings; echo "$SERVICES" > "$STUB_DEVICE/settings/secure.accessibility_enabled"
expect_fail "a service name as the value of an integer setting is refused" r007_settings_read
reset_device; seed_settings
lib r007_settings_apply
! grep -q "^put secure \|^delete secure " "$STUB_DEVICE/ops" && [ "$(setting secure.accessibility_enabled)" = 0 ] \
  && ok "the apply records the accessibility settings and does not change them" \
  || bad "the apply changed an accessibility setting: $(tr '\n' ';' < "$STUB_DEVICE/ops")"
reset_device; seed_settings; echo "$SERVICES" > "$services_file"
echo 1 > "$STUB_DEVICE/settings/secure.accessibility_enabled"
( source "$HERE/lib_r007.sh"; r007_settings_apply || exit 9
  sh_ settings delete secure enabled_accessibility_services; sh_ settings put secure accessibility_enabled 0
  r007_settings_restore ) > "$WORK/out" 2>&1 \
  && [ "$(setting secure.enabled_accessibility_services)" = "$SERVICES" ] \
  && [ "$(setting secure.accessibility_enabled)" = 1 ] && [ ! -e "$RECORD" ] \
  && ok "a list of accessibility services is restored as one string" || bad "the services restore: $(cat "$WORK/out")"
reset_device; seed_settings; echo "" > "$services_file"
( source "$HERE/lib_r007.sh"; r007_settings_apply || exit 9
  sh_ settings put secure enabled_accessibility_services "$SERVICES"; r007_settings_restore ) > "$WORK/out" 2>&1 \
  && [ -f "$services_file" ] && [ -z "$(setting secure.enabled_accessibility_services)" ] \
  && grep -q "secure:enabled_accessibility_services recorded='' now=''" "$WORK/out" \
  && ok "an empty list of accessibility services is restored as an empty string, not as unset" \
  || bad "the empty-list restore: $(cat "$WORK/out")"
for invalid in "secure:enabled_accessibility_services=com.a/.B com.c/.D" "secure:accessibility_enabled=on"; do
  reset_device; seed_settings; mkdir -p "$(dirname "$RECORD")"
  sed "s|^${invalid%%=*}=.*|$invalid|" <<< "$ORIG" > "$RECORD"
  expect_fail "a record with the value '${invalid#*=}' is not applied" r007_settings_restore
done
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
reset_device; seed_settings
run_end 'r007_finish_extra() { fail "the cleanup of the caller"; return 1; }
  r007_settings_apply || r007_stop_run; exit 0'
extra="$(plain_out | grep -n 'FAIL the cleanup of the caller' | cut -d: -f1)"
restored="$(plain_out | grep -n 'PASS the device settings are restored' | cut -d: -f1)"
ended 1 1 1 && [ -n "$extra" ] && [ -n "$restored" ] && [ "$extra" -lt "$restored" ] \
  && ok "the cleanup of the caller runs before the settings restore, and its failure gives status 1" \
  || bad "the cleanup of the caller: $(plain_out | tr '\n' ';')"

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

# ---- phase P2 library (lib_p2.sh) --------------------------------------------------------------
lib2() { ( source "$HERE/lib_p2.sh"; "$@" ) > "$WORK/out" 2>&1; }
expect2_ok()   { local label="$1"; shift; if lib2 "$@"; then ok "$label"; else bad "$label"; fi; }
expect2_fail() { local label="$1"; shift; if lib2 "$@"; then bad "$label"; else ok "$label"; fi; }
out_is() { [ "$(cat "$WORK/out")" = "$1" ]; }
# Runs BODY in a subshell with lib_p2.sh and an evidence file ($WORK/p2.log); the output goes to $WORK/out.
p2() { rm -f "$WORK/p2.log"; : > "$WORK/p2.log"
  ( source "$HERE/lib_p2.sh"; R007_LOG_OUT="$WORK/p2.log"; eval "$1" ) > "$WORK/out" 2>&1; }
DETECTOR="com.applock/com.applock.applocker.service.AppDetectionService"

echo "P2 fixture writer"
for args in "0 0" "4 0" "5 +30000" "8 -60000" "11 1790706559542"; do
  expect2_ok "valid fixture arguments: $args" r007_fixture_args_ok $args
done
for args in "-1 0" "4" "x 0" "4 now" "4 +" "1234567890 0" "4 +-5"; do
  expect2_fail "invalid fixture arguments: '$args'" r007_fixture_args_ok $args
done
fixture_out() { # count until commit [extra line]
  printf '%s\n' "INSTRUMENTATION_STATUS: r007_pid=900" "INSTRUMENTATION_STATUS: r007_count=$1" \
    "INSTRUMENTATION_STATUS: r007_lockout_until=$2" "INSTRUMENTATION_STATUS: r007_commit=$3" ${4:+"$4"} \
    "INSTRUMENTATION_CODE: -1"
}
FIXTURE_MARKERS=$'2.0 900 900 I R007Fixture: pid=900 phase=FIXTURE_BEGIN\n'
FIXTURE_MARKERS+='2.1 900 900 I R007Fixture: pid=900 phase=FIXTURE_END r007_commit=true r007_count=4 r007_pid=900'
INSPECT4=$'INSTRUMENTATION_STATUS: r007_pid=777\nINSTRUMENTATION_STATUS: r007_count=4\n'
INSPECT4+=$'INSTRUMENTATION_STATUS: r007_lockout_until=0\nINSTRUMENTATION_CODE: -1'
MARKERS4=$'3.0 777 777 I R007Inspect: pid=777 phase=INSPECT_BEGIN\n'
MARKERS4+='3.1 777 777 I R007Inspect: pid=777 phase=INSPECT_END r007_count=4 r007_lockout_until=0 r007_pid=777'
fixture_case() { # label expectation(ok|fail) fixture-out logcat args...
  local label="$1" expect="$2" out="$3" log="$4"; shift 4
  reset_device; printf '%s\n' "$log" > "$STUB_DEVICE/logcat"
  if STUB_FIXTURE_OUT="$out" STUB_INSTRUMENT="$INSPECT4" p2 'r007_fixture '"$*"' && echo "pair=$R007_FIXTURE_PAIR"'
  then [ "$expect" = ok ] && grep -qx "pair=4,0" "$WORK/out" && ok "$label" || bad "$label: $(cat "$WORK/out")"
  else [ "$expect" = fail ] && ok "$label" || bad "$label: $(cat "$WORK/out")"; fi
}
fixture_case "a confirmed fixture is written and inspected" ok "$(fixture_out 4 0 true)" \
  "$FIXTURE_MARKERS"$'\n'"$MARKERS4" 4 0
fixture_case "a commit that returns false fails the fixture" fail "$(fixture_out 4 0 false)" \
  "$FIXTURE_MARKERS"$'\n'"$MARKERS4" 4 0
fixture_case "a refused argument fails the fixture" fail \
  $'INSTRUMENTATION_STATUS: r007_pid=900\nINSTRUMENTATION_STATUS: r007_error=deadline\nINSTRUMENTATION_CODE: -1' \
  "$FIXTURE_MARKERS"$'\n'"$MARKERS4" 4 0
fixture_case "a writer run that did not complete fails the fixture" fail \
  "$(fixture_out 4 0 true | sed 's/CODE: -1/CODE: 0/')" "$FIXTURE_MARKERS"$'\n'"$MARKERS4" 4 0
fixture_case "a written count other than the request fails the fixture" fail "$(fixture_out 5 0 true)" \
  "$FIXTURE_MARKERS"$'\n'"$MARKERS4" 4 0
fixture_case "a written deadline other than the absolute request fails the fixture" fail "$(fixture_out 4 0 true)" \
  "$FIXTURE_MARKERS"$'\n'"$MARKERS4" 4 100
fixture_case "missing writer markers fail the fixture" fail "$(fixture_out 4 0 true)" "$MARKERS4" 4 0
fixture_case "a graph line in the writer process fails the fixture" fail "$(fixture_out 4 0 true)" \
  "$FIXTURE_MARKERS"$'\n'"2.05 900 900 I R007Fault: pid=900 thread=main op=INIT phase=CREATED"$'\n'"$MARKERS4" 4 0
fixture_case "an inspection that reads another pair fails the fixture" fail "$(fixture_out 4 0 true)" \
  "$FIXTURE_MARKERS"$'\n'"${MARKERS4/r007_count=4/r007_count=3}" 4 0
reset_device
STUB_FIXTURE_OUT="$(fixture_out 4 0 true)" p2 'r007_fixture 4 soon'
grep -q "not valid" "$WORK/out" && ok "invalid fixture arguments stop the writer before it runs" \
  || bad "invalid arguments: $(cat "$WORK/out")"

echo "P2 detector"
lib2 r007_services_without null; out_is null && ok "no services stay null" || bad "null: $(cat "$WORK/out")"
lib2 r007_services_without ""; out_is null && ok "an empty list gives null" || bad "empty: $(cat "$WORK/out")"
lib2 r007_services_without "$DETECTOR"; out_is null && ok "the detector alone gives null" \
  || bad "the detector alone: $(cat "$WORK/out")"
lib2 r007_services_without "com.applock/.applocker.service.AppDetectionService:com.x/.Y"; out_is "com.x/.Y" \
  && ok "the short form of the detector is removed" || bad "the short form: $(cat "$WORK/out")"
lib2 r007_services_without "com.x/.Y:COM.APPLOCK/com.applock.applocker.service.appdetectionservice:com.z/.W"
out_is "com.x/.Y:com.z/.W" && ok "the detector is removed in any case, and the other services keep their order" \
  || bad "the mixed list: $(cat "$WORK/out")"
reset_device; seed_settings
lib2 r007_detector_state; out_is unbound && ok "no detector in the list reads as unbound" \
  || bad "the state: $(cat "$WORK/out")"
reset_device
STUB_A11Y_FAIL=1 expect2_fail "a failed accessibility query has no state" r007_detector_state
reset_device
STUB_A11Y_NO_LINE=1 expect2_fail "a dump without a Bound services line has no state" r007_detector_state
reset_device; seed_settings
p2 'r007_settings_apply && r007_detector_init && r007_grant_detector && echo "granted=$(r007_detector_state)" \
  && r007_revoke_detector && echo "revoked=$(r007_detector_state)"'
grep -qx "granted=bound" "$WORK/out" && grep -qx "revoked=unbound" "$WORK/out" \
  && [ ! -e "$services_file" ] && [ "$(setting secure.accessibility_enabled)" = 0 ] \
  && ok "a grant binds the detector, and a revoke writes the recorded values back" \
  || bad "the grant cycle: $(cat "$WORK/out")"
grep -n "secure enabled_accessibility_services" "$STUB_DEVICE/ops" | head -2 | tr '\n' ';' \
  | grep -qE "^[0-9]+:delete secure enabled_accessibility_services;[0-9]+:put secure enabled_accessibility_services \
$DETECTOR;$" \
  && ok "the grant deletes the service list before it writes the detector" \
  || bad "the grant order: $(tr '\n' ';' < "$STUB_DEVICE/ops")"
reset_device; seed_settings; echo "com.x/.Y" > "$services_file"
echo 1 > "$STUB_DEVICE/settings/secure.accessibility_enabled"
p2 'r007_settings_apply && r007_detector_init && r007_grant_detector \
  && echo "granted=$(cat "$STUB_DEVICE/settings/secure.enabled_accessibility_services")" && r007_revoke_detector'
grep -qx "granted=com.x/.Y:$DETECTOR" "$WORK/out" \
  && [ "$(setting secure.enabled_accessibility_services)" = "com.x/.Y" ] \
  && [ "$(setting secure.accessibility_enabled)" = 1 ] \
  && ok "the grant keeps the other services, and the revoke leaves them enabled" \
  || bad "the grant with other services: $(cat "$WORK/out")"
reset_device; seed_settings
p2 'r007_grant_detector'; grep -q "needs r007_detector_init" "$WORK/out" \
  && ok "a grant without the recorded values is refused" || bad "a grant without init: $(cat "$WORK/out")"
reset_device; seed_settings
STUB_A11Y_STUCK=unbound R007_BIND_POLLS=2 p2 'r007_settings_apply && r007_detector_init && r007_grant_detector'
grep -q "did not bind" "$WORK/out" && ok "a grant that does not bind fails" || bad "no bind: $(cat "$WORK/out")"
reset_device; seed_settings
STUB_A11Y_STUCK=bound R007_BIND_POLLS=2 p2 'r007_settings_apply && r007_detector_init && r007_revoke_detector'
grep -q "stayed bound" "$WORK/out" && ok "a revoke that leaves the detector bound fails" \
  || bad "stays bound: $(cat "$WORK/out")"
reset_device; seed_settings
p2 'r007_settings_apply && r007_detector_init && r007_grant_detector && r007_kill_for_inspection \
  && echo "state=$(r007_detector_state)"'
# The grant and the revoke each delete the service list; the delete of the revoke must come before the kill.
revoke_line="$(grep -nx "delete secure enabled_accessibility_services" "$STUB_DEVICE/ops" | tail -1 | cut -d: -f1)"
kill_line="$(grep -nx "kill" "$STUB_DEVICE/ops" | head -1 | cut -d: -f1)"
grep -qx "state=unbound" "$WORK/out" && [ -n "$kill_line" ] && [ -n "$revoke_line" ] \
  && [ "$(grep -cx "delete secure enabled_accessibility_services" "$STUB_DEVICE/ops")" = 2 ] \
  && [ "$revoke_line" -lt "$kill_line" ] \
  && ok "a kill with the detector bound removes the grant first" || bad "the kill: $(tr '\n' ';' < "$STUB_DEVICE/ops")"
reset_device; seed_settings
STUB_PID_UNBOUND=5151 p2 'r007_settings_apply && r007_detector_init && r007_grant_detector && r007_kill_for_inspection'
grep -q "changed when the grant was removed" "$WORK/out" && ! grep -qx "kill" "$STUB_DEVICE/ops" \
  && ok "a process that changes when the grant is removed stops the kill" \
  || bad "the changed process: $(cat "$WORK/out")"
reset_device; seed_settings
p2 'r007_kill_for_inspection && echo killed'
grep -qx "killed" "$WORK/out" && grep -qx "kill" "$STUB_DEVICE/ops" && ! grep -q "secure" "$STUB_DEVICE/ops" \
  && ok "a kill with the detector unbound changes no setting" || bad "the unbound kill: $(cat "$WORK/out")"

echo "P2 gates and V"
gate_is() { # expected xml
  lib2 r007_gate_state "$2"; out_is "$1" && ok "the gate state '$1' is parsed" || bad "gate '$1': $(cat "$WORK/out")"
}
gate_is blocked '<node text="Too many failed attempts" /><node text="Try again in 0:28" />'
gate_is open '<node text="Clock" /><node text="Enter your PIN" /><node text="1" />'
gate_is incorrect '<node text="Incorrect PIN — try again" /><node text="1" />'
gate_is biometric '<node text="Unlock Clock" /><node text="Use PIN" /><node text="Enter your PIN" />'
gate_is anr '<node text="App Lock isn'"'"'t responding" /><node text="Enter your PIN" />'
gate_is none '<node text="World Clock" />'
lib2 r007_gate_state '<node text="Clock isn'"'"'t responding" /><node text="Enter your PIN" />'
out_is open && ok "the ANR dialog of another app is not an ANR of the gate" || bad "another ANR: $(cat "$WORK/out")"
expect2_ok "the self-gate activity is recognized" r007_top_is S "com.applock/.presentation.applist.MainActivity"
expect2_ok "the full name of the lock screen is recognized" r007_top_is L \
  "com.applock/com.applock.presentation.authentication.LockScreenActivity"
expect2_fail "the self-gate is not the lock screen" r007_top_is L "com.applock/.presentation.applist.MainActivity"
expect2_fail "another package is not the self-gate" r007_top_is S "com.applockx/.MainActivity"
expect2_fail "Clock is not a gate" r007_top_is L "com.google.android.deskclock/com.android.deskclock.DeskClock"
LOCK_TOP="    topResumedActivity=ActivityRecord{1 u0 com.applock/.presentation.authentication.LockScreenActivity t9}"
reset_device
STUB_ACTIVITIES="$LOCK_TOP" STUB_UI='<node text="Enter your PIN" />' \
  p2 'R007_CLOCK=com.google.android.deskclock; r007_open_gate L && echo "gate=$R007_GATE retries=$R007_GATE_RETRIES"'
grep -qx "gate=open retries=0" "$WORK/out" && ok "the legacy gate opens on the first launch" \
  || bad "the legacy gate: $(cat "$WORK/out")"
reset_device
STUB_ACTIVITIES="$LOCK_TOP" STUB_UI='<node text="World Clock" />' R007_GATE_TRIES=2 R007_GATE_WAIT=1 \
  p2 'R007_CLOCK=com.google.android.deskclock; r007_open_gate L'
[ "$(grep -c "^## gate-retry caller=L" "$WORK/p2.log")" = 2 ] && grep -q "after 2 launches" "$WORK/out" \
  && ok "a gate that does not show is launched again, each retry is recorded, and the open fails" \
  || bad "the retries: $(cat "$WORK/out")"
reset_device
STUB_ACTIVITIES="$LOCK_TOP" STUB_UI='<node text="Try again in 0:10" />' R007_GATE_TRIES=1 R007_GATE_WAIT=1 \
  p2 'R007_CLOCK=c; r007_open_gate L "open incorrect"'
grep -q "did not show" "$WORK/out" && ok "a gate state outside the accepted states does not open the gate" \
  || bad "the accepted states: $(cat "$WORK/out")"
PAD='<node text="Enter your PIN" /><node text="0" bounds="[300,1200][420,1300]" />'
reset_device
STUB_UI="$PAD" p2 'r007_v_reset; r007_submit 0000; echo "state=$R007_SUBMIT_STATE"
  export STUB_UI="<node text=\"Try again in 0:30\" />"; r007_submit 0000
  echo "state=$R007_SUBMIT_STATE submissions=$R007_SUBMISSIONS v=$R007_V_INFERRED"; echo "times=$R007_TAP_TIMES"'
grep -qx "state=open" "$WORK/out" && grep -qx "state=blocked submissions=2 v=1" "$WORK/out" \
  && [ "$(grep -c "^tap 360 1250$" "$STUB_DEVICE/ops")" = 4 ] \
  && ok "only a submission to an open gate counts for the inferred V, and the keys are tapped at their text" \
  || bad "the submissions: $(cat "$WORK/out")"
grep -qx "times=1000-1080 1000-1080 1000-1080 1000-1080" "$WORK/out" \
  && ok "a submission records the device times of each of its taps" || bad "the tap times: $(cat "$WORK/out")"
reset_device
STUB_UI="$PAD" STUB_TAP_TIMES="" p2 'r007_v_reset; r007_submit 0000
  echo "rc=$? times=$R007_TAP_TIMES submissions=$R007_SUBMISSIONS v=$R007_V_INFERRED fails=$FAIL_COUNT"'
grep -qx "rc=1 times=?-? submissions=0 v=0 fails=1" "$WORK/out" && [ "$(grep -c "^tap " "$STUB_DEVICE/ops")" = 1 ] \
  && grep -q "gave no device times" "$WORK/out" \
  && ok "a tap without device times fails the submission, which then does not count" \
  || bad "no times: $(cat "$WORK/out")"
reset_device
STUB_UI="$PAD" STUB_TAP_TIMES="1000 1080 23" p2 'r007_v_reset; r007_submit 0000
  echo "rc=$? times=$R007_TAP_TIMES submissions=$R007_SUBMISSIONS fails=$FAIL_COUNT"'
grep -qx "rc=1 times=?-? submissions=0 fails=1" "$WORK/out" && grep -q "answer: '1000 1080 23'" "$WORK/out" \
  && ok "a tap whose input command exits with status 23 fails the submission, although it gave device times" \
  || bad "the tap status: $(cat "$WORK/out")"
lib2 eval 'SCREEN_W=720 SCREEN_H=1604; r007_key_xy "<node text=\"Enter your PIN\" />" 0'
out_is "359 1190" && ok "a PIN key without text in the dump gets its position from the screen geometry" \
  || bad "the key geometry: $(cat "$WORK/out")"
VLOG="1.0 50 60 I R007Fault: pid=50 thread=lockout-io op=WRITE index=0 phase=BEGIN script=Normal count=1 until=0
1.1 50 60 I R007Fault: pid=50 thread=lockout-io op=WRITE index=0 phase=RETURNED script=Normal result=true
1.2 50 60 I R007Fault: pid=50 thread=lockout-io op=WRITE index=1 phase=BEGIN script=HoldBeforeCommit count=2 until=0
1.3 50 60 I R007Fault: pid=50 thread=lockout-io op=WRITE index=1 phase=HELD script=HoldBeforeCommit
1.4 51 61 I R007Fault: pid=51 thread=lockout-io op=WRITE index=0 phase=BEGIN script=Normal count=5 until=1790708066748
1.5 50 60 I R007Fault: pid=50 thread=main op=READ index=0 phase=BEGIN script=Normal"
v_case() { # label expected function args...
  local label="$1" expected="$2"; shift 2
  lib2 "$@"; out_is "$expected" && ok "$label" || bad "$label: $(cat "$WORK/out")"
}
v_case "the exact V counts the WRITE BEGIN lines of one process" 2 r007_v_exact "$VLOG" 50
v_case "the exact V of another process is its own" 1 r007_v_exact "$VLOG" 51
v_case "pid 5 does not match the lines of pid 50" 0 r007_v_exact "$VLOG" 5
v_case "a write that stayed held labels V as inferred" inferred r007_v_label "$VLOG" 50
v_case "a process without a held write labels V as exact" exact r007_v_label "$VLOG" 51
v_case "the pair of a BEGIN line is read" 2,0 r007_begin_pair "$VLOG" 50 1
v_case "the pair of a threshold write keeps its deadline" 5,1790708066748 r007_begin_pair "$VLOG" 51 0
v_case "a write without a BEGIN line has no pair" "" r007_begin_pair "$VLOG" 50 5

echo "P2 mid-commit"
SWEEP_BEGIN="1.0 70 71 I R007Fault: pid=70 thread=lockout-io op=WRITE index=0 phase=BEGIN script=Normal count=2 until=0"
SWEEP_RESULT="$SWEEP_BEGIN"$'\n'
SWEEP_RESULT+="1.1 70 71 I R007Fault: pid=70 thread=lockout-io op=WRITE index=0 phase=REAL_RESULT result=true"
HASH="$(printf '%064d' 7)"
v_case "no BEGIN line and the old pair: before the write" before-write r007_sweep_class "" 70 absent 1,0 1,0 ""
v_case "BEGIN, no backup file, the old pair: in the adapter" in-adapter \
  r007_sweep_class "$SWEEP_BEGIN" 70 absent 1,0 1,0 2,0
v_case "BEGIN, a backup file, the old pair: inside the platform write" platform-write \
  r007_sweep_class "$SWEEP_BEGIN" 70 "$HASH" 1,0 1,0 2,0
v_case "BEGIN, no backup file, the new pair: after the file write" after-file-write \
  r007_sweep_class "$SWEEP_BEGIN" 70 absent 2,0 1,0 2,0
v_case "REAL_RESULT and the new pair: after the return" after-return \
  r007_sweep_class "$SWEEP_RESULT" 70 absent 2,0 1,0 2,0
v_case "a backup file with the new pair is an anomaly" anomaly r007_sweep_class "$SWEEP_BEGIN" 70 "$HASH" 2,0 1,0 2,0
v_case "a read error is an anomaly" anomaly r007_sweep_class "$SWEEP_BEGIN" 70 absent error:SecurityException 1,0 2,0
v_case "REAL_RESULT with the old pair is an anomaly" anomaly r007_sweep_class "$SWEEP_RESULT" 70 absent 1,0 1,0 2,0
v_case "the lines of another process do not count" before-write r007_sweep_class "$SWEEP_RESULT" 7 absent 1,0 1,0 ""
reset_device
expect2_ok "the mid-commit loop is installed and read back" r007_sweep_install
[ -s "$STUB_DEVICE/files/r007/sweep.sh" ] && ok "the loop script is on the device" || bad "no loop script"
reset_device
p2 'r007_sweep_start 4242 0 && r007_sweep_wait && echo "answer=$R007_SWEEP_ANSWER"'
grep -qx "answer=killed" "$WORK/out" && ok "the loop answer is collected" || bad "the answer: $(cat "$WORK/out")"
reset_device
STUB_SWEEP_ANSWER=oops p2 'r007_sweep_start 4242 0 && r007_sweep_wait'
grep -q "answered 'oops'" "$WORK/out" && ok "an unknown loop answer fails" || bad "unknown answer: $(cat "$WORK/out")"
reset_device; echo x > "$STUB_DEVICE/$STORE.bak"
p2 'r007_sweep_start 4242 0'; grep -q "backup file of the store exists" "$WORK/out" \
  && ok "a backup file before the trial stops the loop start" || bad "a backup file: $(cat "$WORK/out")"
reset_device
p2 'r007_sweep_start 4242 1000'; grep -q "not 0 to 999" "$WORK/out" && ok "a delay of 1000 ms is refused" \
  || bad "the delay: $(cat "$WORK/out")"
# The loop body itself, in a local shell: it kills a sleeping process when the backup file appears, and it stops
# without a kill after its limit.
loopdir="$WORK/loop"; mkdir -p "$loopdir/shared_prefs"
( source "$HERE/lib_p2.sh"; printf '%s\n' "$R007_SWEEP_BODY" ) > "$loopdir/sweep.sh" 2>/dev/null
exec 4>&2 2>/dev/null   # the shell reports the killed test process on stderr
sleep 30 & victim=$!
( cd "$loopdir" && sh sweep.sh "$victim" 0 5 > answer ) & looper=$!
sleep 0.5; : > "$loopdir/shared_prefs/applock_lockout.xml.bak"
wait "$looper"; wait "$victim" 2>/dev/null; victim_rc=$?
[ "$(cat "$loopdir/answer")" = killed ] && [ "$victim_rc" = 137 ] \
  && ok "the loop kills the process when the backup file appears" \
  || bad "the loop: answer '$(cat "$loopdir/answer")', status $victim_rc"
rm -f "$loopdir/shared_prefs/applock_lockout.xml.bak"
sleep 30 & victim=$!
( cd "$loopdir" && sh sweep.sh "$victim" 0 1 > answer )
kill -0 "$victim" 2>/dev/null && [ "$(cat "$loopdir/answer")" = timeout ] \
  && ok "the loop stops at its limit without a kill" || bad "the loop limit: '$(cat "$loopdir/answer")'"
kill "$victim" 2>/dev/null; wait "$victim" 2>/dev/null
exec 2>&4 4>&-

echo "P2 files"
reset_device
lib2 r007_file_state "$STORE"; grep -qE '^[0-9a-f]{64}$' "$WORK/out" && ok "a file state is its hash" \
  || bad "the file state: $(cat "$WORK/out")"
lib2 r007_file_state shared_prefs/none.xml; out_is absent && ok "a missing file reads as absent" \
  || bad "a missing file: $(cat "$WORK/out")"
STUB_STATE_FAIL=1 expect2_fail "a failed file query has no state" r007_file_state "$STORE"
reset_device; printf '%s\n' "$MARKERS" > "$STUB_DEVICE/logcat"; echo old > "$STUB_DEVICE/$STORE.bak"
STUB_INSTRUMENT="$INSPECTION" STUB_INSPECT_WRITES=1 p2 'r007_inspect_files && echo "files=$R007_FILES" \
  && echo "pair=$(r007_pair "$R007_INSPECTION")"'
files="$(sed -n 's/^files=//p' "$WORK/out")"
[[ "$files" =~ ^store=([0-9a-f]{64})\ bak=[0-9a-f]{64}\ after=([0-9a-f]{64})$ ]] \
  && [ "${BASH_REMATCH[1]}" != "${BASH_REMATCH[2]}" ] && grep -qx "pair=3," "$WORK/out" \
  && ok "an inspection that changes the store records both states and does not fail" \
  || bad "the file inspection: $(cat "$WORK/out")"
reset_device
STUB_INSTRUMENT="$INSPECTION" p2 'r007_inspect_files'
grep -q "evidence failed" "$WORK/out" && ok "the file inspection still needs the inspector markers" \
  || bad "no markers: $(cat "$WORK/out")"
reset_device
expect2_ok "the store is damaged" r007_store_damage
store_is '<map><int' && ok "the damaged store holds XML that does not parse" \
  || bad "damaged: $(cat "$STUB_DEVICE/$STORE")"
reset_device
STUB_APP_PROC=present expect2_fail "the store is not damaged while the app runs" r007_store_damage
store_is "$STORE_XML" && ok "a refused damage leaves the store" || bad "the store changed"
lib2 r007_pair $'r007_count=8\nr007_lockout_until=1790706559542'; out_is 8,1790706559542 \
  && ok "an inspection gives its pair" || bad "the pair: $(cat "$WORK/out")"
lib2 r007_pair $'r007_read_error=java.lang.SecurityException'; out_is error:SecurityException \
  && ok "a read error gives its class" || bad "the read error: $(cat "$WORK/out")"

echo "P2 app setting"
APP_SETTINGS="shared_prefs/applock_settings.xml"
reset_device
p2 'r007_app_settings_save && r007_app_settings_biometric false \
  && cp "$STUB_DEVICE/'"$APP_SETTINGS"'" "'"$WORK"'/written.xml" && r007_app_settings_restore'
grep -qF '<boolean name="biometric_unlock" value="false" />' "$WORK/written.xml" \
  && [ ! -e "$STUB_DEVICE/$APP_SETTINGS" ] \
  && grep -qx "$APP_SETTINGS=absent" "$WORK/p2.log" \
  && ok "an absent settings file gets the setting for the run and is removed again" \
  || bad "the absent original: $(cat "$WORK/out")"
reset_device
printf '<map>\n    <string name="relock_policy">ON_SCREEN_OFF</string>\n</map>\n' > "$STUB_DEVICE/$APP_SETTINGS"
cp "$STUB_DEVICE/$APP_SETTINGS" "$WORK/original.xml"
p2 'r007_app_settings_save && r007_app_settings_biometric true && r007_app_settings_restore'
cmp -s "$STUB_DEVICE/$APP_SETTINGS" "$WORK/original.xml" && ok "an existing settings file is written back unchanged" \
  || bad "the existing original: $(cat "$WORK/out")"
reset_device
p2 'r007_app_settings_biometric false'; grep -q "were not saved" "$WORK/out" \
  && ok "a setting change without a saved original is refused" || bad "no save: $(cat "$WORK/out")"
reset_device
p2 'r007_app_settings_save && r007_app_settings_biometric maybe'; grep -q "not true or false" "$WORK/out" \
  && ok "a setting value other than true or false is refused" || bad "the value: $(cat "$WORK/out")"
reset_device
STUB_APP_PROC=present p2 'r007_app_settings_save'; grep -q "runs" "$WORK/out" \
  && ok "the settings file is not saved while the app runs" || bad "the app runs: $(cat "$WORK/out")"
reset_device; cp "$WORK/original.xml" "$STUB_DEVICE/$APP_SETTINGS"
p2 'r007_app_settings_save && r007_app_settings_biometric false && export STUB_COPY_FAIL=1
  r007_app_settings_restore; echo "pending=$R007_APP_SETTINGS_SAVED"'
grep -qE "^pending=[0-9a-f]{64}$" "$WORK/out" && ok "a failed restore keeps the restore pending" \
  || bad "the failed restore: $(cat "$WORK/out")"

echo "P2 reboot"
reset_device; stay_awake_set
STUB_REBOOT_NEW_ID=boot-2 R007_REBOOT_SETTLE=0 R007_BOOT_WAIT=2 p2 'r007_reboot && echo "boot=$R007_BOOT_ID"'
grep -qx "boot=boot-2" "$WORK/out" && ok "a reboot with a new boot id passes" || bad "the reboot: $(cat "$WORK/out")"
reset_device; stay_awake_set
R007_REBOOT_SETTLE=0 R007_BOOT_WAIT=2 R007_UNLOCK_WAIT=0 p2 'r007_reboot'
grep -q "boot id did not change" "$WORK/out" && ok "a reboot without a new boot id fails" \
  || bad "the same boot id: $(cat "$WORK/out")"
# A phone that connects adb only after the unlock: 6 state reads fail, longer than R007_BOOT_WAIT alone, then 2
# keyguard reads show it locked. Each sleep moves the clock 1 s on instead of waiting.
reset_device; stay_awake_set
STUB_REBOOT_NEW_ID=boot-2 STUB_REBOOT_ABSENT=6 STUB_REBOOT_LOCKED=2 R007_REBOOT_SETTLE=0 R007_BOOT_WAIT=2 \
  R007_UNLOCK_WAIT=30 \
  p2 'sleep() { SECONDS=$((SECONDS + 1)); }; r007_reboot && echo "boot=$R007_BOOT_ID fails=$FAIL_COUNT"'
grep -qx "boot=boot-2 fails=0" "$WORK/out" && [ "$(grep -c "ACTION: unlock" "$WORK/out")" = 1 ] \
  && ok "a reboot passes when adb connects only after the unlock, with one unlock request" \
  || bad "the reboot with adb after the unlock: $(cat "$WORK/out")"
reset_device; stay_awake_set
STUB_REBOOT_NEW_ID=boot-2 STUB_REBOOT_ABSENT=999 R007_REBOOT_SETTLE=0 R007_BOOT_WAIT=4 R007_UNLOCK_WAIT=4 \
  p2 'sleep() { SECONDS=$((SECONDS + 2)); }; r007_reboot; echo "rc=$?"'
grep -q "did not connect in 8 s after the reboot" "$WORK/out" && grep -qx "rc=1" "$WORK/out" \
  && ok "a device that does not connect after the reboot fails at the shared deadline" \
  || bad "the device that does not connect: $(cat "$WORK/out")"
reset_device; stay_awake_set
STUB_REBOOT_NEW_ID=boot-2 STUB_REBOOT_LOCKED=999 R007_REBOOT_SETTLE=0 R007_BOOT_WAIT=4 R007_UNLOCK_WAIT=4 \
  p2 'sleep() { SECONDS=$((SECONDS + 2)); }; r007_reboot; echo "rc=$?"'
grep -q "stayed locked for 8 s after the reboot" "$WORK/out" && grep -qx "rc=1" "$WORK/out" \
  && ok "a device that stays locked after the reboot fails at the shared deadline" \
  || bad "the locked device: $(cat "$WORK/out")"
# The stub logcat waits 20 s for a device that is not connected, as the real one does.
reset_device
begin=$(date +%s); STUB_OFFLINE=1 lib r007_capture_log; rc=$?; took=$(( $(date +%s) - begin ))
[ "$rc" != 0 ] && [ "$took" -le 5 ] && ok "a log capture fails at once when adb cannot see the device" \
  || bad "the log capture without a device: rc=$rc took=${took}s $(cat "$WORK/out")"
reset_device; stay_awake_set
STUB_REBOOT_NEW_ID=boot-2 STUB_REBOOT_LAG=1 R007_REBOOT_SETTLE=0 R007_BOOT_WAIT=10 \
  p2 'r007_reboot && echo "boot=$R007_BOOT_ID fails=$FAIL_COUNT"'
grep -qx "boot=boot-2 fails=0" "$WORK/out" \
  && ok "a device that first answers with the old boot id is waited for until the new boot id shows" \
  || bad "the late boot id: $(cat "$WORK/out")"

echo "P2 markers and summary"
reset_device
p2 'r007_mark R3.1b S 1 yes no fixture=Z v=2 v_label=exact; r007_mark R3.1b S 2 yes no
  r007_mark R3.1b L 1 no yes; r007_mark H01 S 1 yes yes; r007_save_summary; echo "fails=$FAIL_COUNT"'
grep -qx "## CASE R3.1b caller=S repeat=1 boot_id=boot-1 fixture=Z v=2 v_label=exact predicted=yes objective=no" \
  "$WORK/p2.log" && ok "a case marker holds the case, the fields, and both verdicts" \
  || bad "the marker: $(grep '^## CASE' "$WORK/p2.log" | head -1)"
grep -qx "fails=1" "$WORK/out" && grep -q "R3.1b caller L repeat 1: the baseline differs from the prediction" \
  "$WORK/out" && ok "only the repeat that is not as predicted counts as a failure; an unmet objective does not" \
  || bad "the failure count: $(cat "$WORK/out")"
[ "$(sed -n '/^## SUMMARY/,$p' "$WORK/p2.log" | tail -3 | tr '\n' ';')" \
  = "| R3.1b | S | 2 | 2 of 2 | 0 of 2 |;| R3.1b | L | 1 | 0 of 1 | 1 of 1 |;| H01 | S | 1 | 1 of 1 | 1 of 1 |;" ] \
  && ok "the summary counts the repeats and verdicts per case and caller, in the order of the markers" \
  || bad "the summary: $(sed -n '/^## SUMMARY/,$p' "$WORK/p2.log" | tr '\n' ';')"
# A boot id read that fails, and one that gives an empty line: the marker is kept with boot_id=unreadable, the repeat
# is in the summary, and it counts as one failure.
for boot_case in STUB_BOOT_FAIL STUB_BOOT_EMPTY; do
  reset_device
  ( export "$boot_case=1"
    p2 'r007_mark H01 S 1 yes yes fixture=Z; echo "rc=$? fails=$FAIL_COUNT"; r007_save_summary' )
  marker="## CASE H01 caller=S repeat=1 boot_id=unreadable fixture=Z predicted=yes objective=yes"
  grep -qx "rc=1 fails=1" "$WORK/out" && grep -q "the boot id could not be read" "$WORK/out" \
    && grep -qxF "$marker" "$WORK/p2.log" \
    && grep -qxF "| H01 | S | 1 | 1 of 1 | 1 of 1 |" "$WORK/p2.log" \
    && ok "a case marker with an unreadable boot id ($boot_case) is kept, and the repeat fails" \
    || bad "the unreadable boot id ($boot_case): $(cat "$WORK/out") $(grep '^## CASE' "$WORK/p2.log")"
done
p2 'r007_mark X10 S 1 maybe no'; grep -q "are not valid" "$WORK/out" && ! grep -q "^## CASE" "$WORK/p2.log" \
  && ok "a verdict other than yes or no is refused" || bad "the verdict: $(cat "$WORK/out")"
p2 'r007_mark X10 S 1 na no'; grep -q "are not valid" "$WORK/out" \
  && ok "na is refused as the predicted verdict" || bad "na as predicted: $(cat "$WORK/out")"
p2 'r007_mark R1.4d S 1 yes na && r007_mark R1.4d S 2 yes na && r007_save_summary'
grep -qxF "| R1.4d | S | 2 | 2 of 2 | n/a |" "$WORK/p2.log" \
  && ok "a measurement case shows its objective as n/a in the summary" || bad "n/a: $(tail -2 "$WORK/p2.log")"
p2 'r007_mark R1.2b S 1 yes no; r007_mark R1.2b S 2 yes na; r007_mark R1.2b S 3 no na; r007_save_summary'
grep -qxF "| R1.2b | S | 3 | 2 of 3 | 0 of 1, 2 n/a |" "$WORK/p2.log" \
  && ok "the objective counts only the repeats with a verdict, and names the n/a repeats apart" \
  || bad "the mixed objective: $(tail -2 "$WORK/p2.log")"
v_case "a residual repeat as predicted shows an unmet objective" no r007_residual yes
v_case "a residual repeat that is not as predicted does not establish an unmet objective" na r007_residual no

echo "P2 write end"
THREW_LOG="1.0 80 81 I R007Fault: pid=80 thread=lockout-io op=WRITE index=0 phase=BEGIN script=Throw count=1 until=0
1.1 80 81 I R007Fault: pid=80 thread=lockout-io op=WRITE index=0 phase=THREW script=Throw wall=1790000000123"
reset_device; printf '%s\n' "$THREW_LOG" > "$STUB_DEVICE/logcat"
expect2_ok "a THREW line ends a write whose script throws" r007_wait_write_done 80 0 1
v_case "the end of a thrown write has its wall time" 1790000000123 r007_write_done_wall "$THREW_LOG" 80 0
reset_device; printf '%s\n' "${THREW_LOG%%$'\n'*}" > "$STUB_DEVICE/logcat"
expect2_fail "a write with only a BEGIN line has not ended" r007_wait_write_done 80 0 1

echo "pending app-data changes"
PENDING="$STUB_DEVICE/files/r007/pending"
reset_device
lib r007_pending_list; out_is "" && ok "no marker lists nothing" || bad "the empty list: $(cat "$WORK/out")"
( source "$HERE/lib_r007.sh"; r007_pending_set store "$(printf '%064d' 1)" && r007_pending_get store && echo
  r007_pending_list; echo; r007_pending_clear store && r007_pending_get store ) > "$WORK/out" 2>&1
[ "$(tr '\n' ';' < "$WORK/out")" = "$(printf '%064d' 1);store;absent" ] \
  && ok "a marker is written, read, listed, and removed" || bad "the marker cycle: $(tr '\n' ';' < "$WORK/out")"
reset_device
STUB_STATE_FAIL=1 expect_fail "a failed marker query is not an empty list" r007_pending_list
reset_device
( source "$HERE/lib_r007.sh"; h="$(r007_store_save)" && [ "$(r007_pending_get store)" = "$h" ] ) > "$WORK/out" 2>&1 \
  && ok "a saved store copy writes the store marker with its hash" || bad "the store marker: $(cat "$WORK/out")"
( source "$HERE/lib_r007.sh"; r007_store_save ) > "$WORK/out" 2>&1 \
  && bad "a second store save while the marker exists is refused" \
  || ok "a second store save while the marker exists is refused"
cp "$STUB_DEVICE/files/r007/lockout.orig" "$WORK/copy.orig"
( source "$HERE/lib_r007.sh"; r007_store_tamper >/dev/null && r007_store_restore "$(cat "$PENDING/store")" ) \
  > "$WORK/out" 2>&1 && [ ! -e "$PENDING/store" ] && store_is "$STORE_XML" \
  && ok "a verified store restore removes the store marker" || bad "the store restore: $(cat "$WORK/out")"
reset_device; mkdir -p "$PENDING"; echo 500 > "$PENDING/prefs_mode"; echo x > "$STUB_DEVICE/files/r007/lockout.orig"
expect_fail "the control directory stays while a marker exists" r007_remove_control
[ -e "$STUB_DEVICE/files/r007/lockout.orig" ] && ok "the copies stay with the control directory" \
  || bad "the control directory was removed"
reset_device
( source "$HERE/lib_r007.sh"; r007_prefs_readonly && cat "$PENDING/prefs_mode" && echo && r007_prefs_writable \
  && [ ! -e "$PENDING/prefs_mode" ] && echo cleared ) > "$WORK/out" 2>&1
[ "$(tr '\n' ';' < "$WORK/out")" = "500;cleared;" ] \
  && [ "$(grep chmod "$STUB_DEVICE/ops" | tr '\n' ';')" = "chmod 500 shared_prefs;chmod 771 shared_prefs;" ] \
  && ok "the marker is written before chmod 500 and removed after chmod 771" \
  || bad "the preferences mode: $(tr '\n' ';' < "$WORK/out") ops: $(tr '\n' ';' < "$STUB_DEVICE/ops")"
reset_device
run_end 'r007_prefs_readonly || r007_stop_run; exit 3'
grep -qx "chmod 771 shared_prefs" "$STUB_DEVICE/ops" && [ ! -e "$PENDING/prefs_mode" ] \
  && ok "the exit handler makes a read-only preferences directory of the run writable again" \
  || bad "the exit handler and the preferences mode: $(tr '\n' ';' < "$STUB_DEVICE/ops")"
reset_device
p2 'r007_app_settings_save && cat "'"$PENDING"'/app_settings"'
grep -qx "no-file" "$WORK/out" && ok "a missing app settings file is marked as no-file, not as absent" \
  || bad "the app settings marker: $(cat "$WORK/out")"
p2 'r007_app_settings_save'; grep -q "of an earlier run are pending" "$WORK/out" \
  && ok "an app settings save while its marker exists is refused" || bad "the second save: $(cat "$WORK/out")"
reset_device; cp "$WORK/original.xml" "$STUB_DEVICE/$APP_SETTINGS"
( source "$HERE/lib_p2.sh"; R007_LOG_OUT="$WORK/p2.log"; r007_app_settings_save && r007_app_settings_biometric false
  R007_STORE_SAVED="$(r007_store_save)" && r007_prefs_readonly ) > "$WORK/out" 2>&1   # an interrupted run
[ "$(ls "$PENDING" | tr '\n' ' ')" = "app_settings prefs_mode store " ] \
  && ok "an interrupted run leaves one marker for each change" || bad "the markers: $(ls "$PENDING" | tr '\n' ' ')"
: > "$STUB_DEVICE/ops"
R007_LOG_OUT="$WORK/recover.log" bash "$HERE/restore_settings.sh" > "$WORK/out" 2>&1
cmp -s "$STUB_DEVICE/$APP_SETTINGS" "$WORK/original.xml" && store_is "$STORE_XML" && [ -z "$(ls "$PENDING")" ] \
  && grep -qx "chmod 771 shared_prefs" "$STUB_DEVICE/ops" \
  && ok "restore_settings.sh undoes the changes of the markers and removes the markers" \
  || bad "the recovery: $(plain_out | tr '\n' ';')"
reset_device; mkdir -p "$PENDING"; echo "garbage" > "$PENDING/store"
if R007_LOG_OUT="$WORK/recover.log" bash "$HERE/restore_settings.sh" > "$WORK/out" 2>&1; then
  bad "a marker with a value that is not valid fails the recovery"
elif [ -e "$PENDING/store" ]; then ok "a marker with a value that is not valid fails the recovery and stays"
else bad "a marker with a value that is not valid was removed"; fi

echo "P2 refused preflight"
# The driver runs against the stub. A refused preflight must change no app data: no fixture, and the copies and
# markers of an earlier run stay.
refused_run() { # label
  R007_LOG_OUT="$WORK/refused.log" bash "$HERE/p2_device.sh" -s stub -r 1 healthy > "$WORK/out" 2>&1
  local rc=$?
  [ "$rc" != 0 ] && ! grep -q "^instrument" "$STUB_DEVICE/ops" && [ -e "$STUB_DEVICE/files/r007/lockout.orig" ] \
    && ok "$1" || bad "$1: rc $rc, ops $(tr '\n' ';' < "$STUB_DEVICE/ops"), $(plain_out | tail -3 | tr '\n' ';')"
}
reset_device; seed_settings; mkdir -p "$(dirname "$RECORD")" "$STUB_DEVICE/files/r007"
printf '%s\n' "$ORIG" > "$RECORD"; echo x > "$STUB_DEVICE/files/r007/lockout.orig"
refused_run "a preflight refused for a settings record of an earlier run writes no fixture and keeps the copies"
reset_device; seed_settings; mkdir -p "$PENDING"; echo "$(printf '%064d' 2)" > "$PENDING/store"
echo x > "$STUB_DEVICE/files/r007/lockout.orig"
refused_run "a preflight refused for a marker of an earlier run writes no fixture and keeps the copies"
! grep -q "^put " "$STUB_DEVICE/ops" && [ ! -e "$RECORD" ] \
  && ok "a preflight refused for a marker changes no device setting" \
  || bad "the settings after the marker refusal: $(tr '\n' ';' < "$STUB_DEVICE/ops")"
reset_device; seed_settings; mkdir -p "$STUB_DEVICE/files/r007"; echo x > "$STUB_DEVICE/files/r007/lockout.orig"
STUB_WAKE_FAIL=1 refused_run "a preflight that fails before the first app-data change writes no fixture"
[ ! -e "$RECORD" ] && settings_are "0 1 1" && ok "the device settings of that run are restored" \
  || bad "the settings after the wake failure: $(tr '\n' ';' < "$STUB_DEVICE/ops")"
# A caller selection that names no caller, or one caller twice, is refused before any device command.
for callers in "" " " "S S" "S,L"; do
  reset_device
  bash "$HERE/p2_device.sh" -s stub -r 1 -c "$callers" healthy > "$WORK/out" 2>&1; rc=$?
  [ "$rc" = 2 ] && [ ! -s "$STUB_DEVICE/ops" ] && grep -q "^-c " "$WORK/out" \
    && ok "the caller selection '$callers' is refused before the preflight" \
    || bad "the caller selection '$callers': rc $rc, $(tr '\n' ';' < "$WORK/out")"
done
# A case selection is refused before any device command when the segment has no case list, when it names a case that
# the segment does not have or one case twice, and when it is empty.
for selection in "healthy:R1.2a" "cold-read:R9.9" "cold-read:R1.2a R1.2a" "cold-read:" "cold-read: "; do
  reset_device
  bash "$HERE/p2_device.sh" -s stub -r 1 -k "${selection#*:}" "${selection%%:*}" > "$WORK/out" 2>&1; rc=$?
  [ "$rc" = 2 ] && [ ! -s "$STUB_DEVICE/ops" ] && grep -q "^-k" "$WORK/out" \
    && ok "the case selection '${selection#*:}' of the segment ${selection%%:*} is refused before the preflight" \
    || bad "the case selection '$selection': rc $rc, $(tr '\n' ';' < "$WORK/out")"
done
reset_device
SEGMENT_CASES=R1.2a bash "$HERE/p2_device.sh" -s stub -r 1 -k R1.2a healthy > "$WORK/out" 2>&1; rc=$?
[ "$rc" = 2 ] && [ ! -s "$STUB_DEVICE/ops" ] && grep -q "has no case selection" "$WORK/out" \
  && ok "a case list from the environment does not give the healthy segment a case selection" \
  || bad "the case list from the environment: rc $rc, $(tr '\n' ';' < "$WORK/out")"

echo "fault script cleanup"
reset_device; mkdir -p "$STUB_DEVICE/files/r007/release" "$PENDING"
echo "READ 0 HoldThenRead" > "$STUB_DEVICE/files/r007/faults"; : > "$STUB_DEVICE/files/r007/release/1_READ_0"
echo no-file > "$PENDING/app_settings"
R007_LOG_OUT="$WORK/recover.log" bash "$HERE/restore_settings.sh" > "$WORK/out" 2>&1; rc=$?
[ "$rc" = 0 ] && [ ! -e "$STUB_DEVICE/files/r007/faults" ] && [ ! -e "$STUB_DEVICE/files/r007/release" ] \
  && [ -z "$(ls "$PENDING")" ] && grep -qx "force-stop" "$STUB_DEVICE/ops" \
  && ok "the recovery stops the app and removes a fault script and its release files" \
  || bad "the fault script after the recovery: rc $rc, $(plain_out | tail -3 | tr '\n' ';')"
reset_device
run_end 'r007_set_faults "READ 0 HoldThenRead" || r007_stop_run; exit 3'
[ ! -e "$STUB_DEVICE/files/r007/faults" ] && grep -qx "force-stop" "$STUB_DEVICE/ops" \
  && ok "the exit handler stops the app and removes a fault script that the run published" \
  || bad "the fault script after the exit: $(cat "$STUB_DEVICE/files/r007/faults" 2>/dev/null)"
reset_device
run_end 'exit 0'
! grep -q "force-stop" "$STUB_DEVICE/ops" && ok "a run that published no fault script does not stop the app at exit" \
  || bad "the exit without a fault script: $(tr '\n' ';' < "$STUB_DEVICE/ops")"

echo "countdown check"
# A dump from 60.000 s to 64.000 s against a deadline at 100.000 s: the gate shows 36 to 41 s.
for shown in 36 38 41; do
  expect2_ok "a countdown of $shown s fits a dump of 4 s that ends 36 s before the deadline" \
    r007_countdown_ok "$shown" 100000 60000 64000
done
for shown in 35 42 ""; do
  expect2_fail "a countdown of '${shown}' s does not fit that dump" r007_countdown_ok "$shown" 100000 60000 64000
done
expect2_ok "a deadline inside the dump allows 0 s" r007_countdown_ok 0 62000 60000 64000

echo "recovery order and P1 marker check"
reset_device; seed_settings; mkdir -p "$(dirname "$RECORD")" "$PENDING"
sed "s|^secure:enabled_accessibility_services=null$|secure:enabled_accessibility_services=$DETECTOR|; \
s|^secure:accessibility_enabled=0$|secure:accessibility_enabled=1|" <<< "$ORIG" > "$RECORD"
echo no-file > "$PENDING/app_settings"; echo "<map />" > "$STUB_DEVICE/$APP_SETTINGS"; : > "$STUB_DEVICE/ops"
R007_LOG_OUT="$WORK/recover.log" bash "$HERE/restore_settings.sh" > "$WORK/out" 2>&1
line_of() { grep -nxF "$1" "$STUB_DEVICE/ops" | head -1 | cut -d: -f1; }
stop="$(line_of force-stop)"; put="$(line_of "put secure enabled_accessibility_services $DETECTOR")"
on="$(line_of "put secure accessibility_enabled 1")"
[ -n "$stop" ] && [ -n "$put" ] && [ -n "$on" ] && [ "$stop" -lt "$put" ] && [ "$put" -lt "$on" ] \
  && [ ! -e "$STUB_DEVICE/$APP_SETTINGS" ] && [ ! -e "$RECORD" ] \
  && ok "the recovery stops the app before it writes the accessibility settings" \
  || bad "the recovery order: $(tr '\n' ';' < "$STUB_DEVICE/ops")"
! grep -qx "delete secure enabled_accessibility_services" "$STUB_DEVICE/ops" \
  && ok "a service list that differs from the record is written without a delete" \
  || bad "the delete of a differing list: $(tr '\n' ';' < "$STUB_DEVICE/ops")"
reset_device; seed_settings; mkdir -p "$PENDING"; echo 500 > "$PENDING/prefs_mode"
R007_LOG_OUT="$WORK/p1.log" bash "$HERE/p1_validate.sh" -s stub > "$WORK/out" 2>&1; rc=$?
[ "$rc" != 0 ] && ! grep -q "^put " "$STUB_DEVICE/ops" && [ ! -e "$RECORD" ] && [ -e "$PENDING/prefs_mode" ] \
  && plain_out | grep -q "an earlier run left app data changed (prefs_mode)" \
  && ok "p1_validate.sh does not start while a marker of an earlier run exists" \
  || bad "p1_validate.sh with a marker: rc $rc, $(plain_out | tail -3 | tr '\n' ';')"

echo "recovery without work"
reset_device; seed_settings
R007_LOG_OUT="$WORK/recover.log" bash "$HERE/restore_settings.sh" > "$WORK/out" 2>&1; rc=$?
[ "$rc" = 0 ] && ! grep -q "force-stop" "$STUB_DEVICE/ops" \
  && plain_out | grep -q "no fault script and no pending app-data change" \
  && ok "a recovery with nothing to undo does not stop the app" \
  || bad "the empty recovery: rc $rc, ops $(tr '\n' ';' < "$STUB_DEVICE/ops")"
reset_device; mkdir -p "$STUB_DEVICE/files/r007"; echo "READ 0 Throw" > "$STUB_DEVICE/files/r007/faults"
R007_LOG_OUT="$WORK/recover.log" bash "$HERE/restore_settings.sh" > "$WORK/out" 2>&1; rc=$?
[ "$rc" = 0 ] && grep -qx "force-stop" "$STUB_DEVICE/ops" && [ ! -e "$STUB_DEVICE/files/r007/faults" ] \
  && ok "a fault script without a marker makes the recovery stop the app and remove the script" \
  || bad "the recovery of a fault script: rc $rc, $(plain_out | tail -3 | tr '\n' ';')"

echo "service list restore"
OTHER='com.other/.Reader$Service'
reset_device; seed_settings; printf '%s\n' "$OTHER" > "$services_file"
echo 1 > "$STUB_DEVICE/settings/secure.accessibility_enabled"
( R007_LOG_OUT="$WORK/settings.log"; source "$HERE/lib_r007.sh"; r007_settings_apply && r007_settings_restore ) \
  > "$WORK/out" 2>&1
! grep -q "secure enabled_accessibility_services" "$STUB_DEVICE/ops" \
  && [ "$(setting secure.enabled_accessibility_services)" = "$OTHER" ] && [ ! -e "$RECORD" ] \
  && ok "an unchanged list without a service of the app is not written again" \
  || bad "the unchanged list: $(tr '\n' ';' < "$STUB_DEVICE/ops")"
reset_device; seed_settings; printf '%s\n' "$SERVICES" > "$services_file"
echo 1 > "$STUB_DEVICE/settings/secure.accessibility_enabled"
( R007_LOG_OUT="$WORK/settings.log"; source "$HERE/lib_r007.sh"; r007_settings_apply && r007_settings_restore ) \
  > "$WORK/out" 2>&1
del="$(line_of "delete secure enabled_accessibility_services")"
put="$(line_of "put secure enabled_accessibility_services $SERVICES")"
[ -n "$del" ] && [ -n "$put" ] && [ "$del" -lt "$put" ] && [ ! -e "$RECORD" ] \
  && ok "an unchanged list with a service of the app is deleted and written again" \
  || bad "the list with the detector: $(tr '\n' ';' < "$STUB_DEVICE/ops")"
reset_device; seed_settings; printf '%s\n' "$OTHER" > "$services_file"
echo 1 > "$STUB_DEVICE/settings/secure.accessibility_enabled"
( R007_LOG_OUT="$WORK/settings.log"; source "$HERE/lib_r007.sh"; r007_settings_apply || exit 9
  sh_ settings put secure enabled_accessibility_services "$OTHER:$DETECTOR" >/dev/null; r007_settings_restore ) \
  > "$WORK/out" 2>&1
[ "$(grep -cxF "put secure enabled_accessibility_services $OTHER" "$STUB_DEVICE/ops")" = 1 ] \
  && ! grep -qx "delete secure enabled_accessibility_services" "$STUB_DEVICE/ops" \
  && [ "$(setting secure.enabled_accessibility_services)" = "$OTHER" ] \
  && ok "a changed list is written back without a delete" || bad "the changed list: $(tr '\n' ';' < "$STUB_DEVICE/ops")"
reset_device; seed_settings; echo "com.x/.Y" > "$services_file"
STUB_A11Y_STUCK=unbound R007_BIND_POLLS=1 p2 'r007_settings_apply && r007_detector_init && r007_grant_write \
  && echo written'
grep -qx "written" "$WORK/out" && [ "$(setting secure.enabled_accessibility_services)" = "com.x/.Y:$DETECTOR" ] \
  && [ "$(setting secure.accessibility_enabled)" = 1 ] \
  && ok "the grant write keeps the recorded services and does not wait for the bind" \
  || bad "the grant write: $(cat "$WORK/out")"

echo "P2 protected app"
ENGINE="D AppLockEngine: com.google.android.deskclock -> LockDecision(requiresAuthentication="
LOCK_LINE="  1790000000.500 55 55 ${ENGINE}true, reason=protected app, no session)"
FREE_LINE="  1790000000.500 55 55 ${ENGINE}false, reason=not protected)"
OLD_LINE="  1789999999.000 55 55 ${ENGINE}true, reason=protected app, no session)"
OTHER_APP_LINE="  1790000000.500 55 55 D AppLockEngine: com.other -> LockDecision(requiresAuthentication=true, x)"
protect() { # logcat-text
  reset_device; printf '%s\n' "$1" > "$STUB_DEVICE/logcat"
  R007_GATE_TRIES=2 R007_GATE_WAIT=0 p2 'R007_CLOCK=com.google.android.deskclock; r007_protect_clock; echo "rc=$?"'
}
protect "$LOCK_LINE"
grep -qx "rc=0" "$WORK/out" && ! grep -q "^tap " "$STUB_DEVICE/ops" \
  && grep -qx "## clock-probe launch=1 gate=none decisions=protected state=protected" "$WORK/p2.log" \
  && ok "a lock decision for Clock without a lock screen counts as protected, and nothing is tapped" \
  || bad "the lock decision: $(plain_out | tr '\n' ';')"
protect "$FREE_LINE"$'\n'"$LOCK_LINE"
grep -qx "rc=0" "$WORK/out" && ! grep -q "^tap " "$STUB_DEVICE/ops" \
  && ok "one lock decision among not-protected decisions counts as protected" \
  || bad "the mixed decisions: $(plain_out | tr '\n' ';')"
protect "$OLD_LINE"$'\n'"$FREE_LINE"
grep -qx "rc=1" "$WORK/out" && plain_out | grep -q "Clock is not protected in 2 launches" \
  && [ "$(grep -c "decisions=unprotected state=unprotected" "$WORK/p2.log")" = 2 ] \
  && ok "only not-protected decisions in each launch lead to the app list, and an older lock decision does not count" \
  || bad "the unprotected launches: $(plain_out | tr '\n' ';')"
protect "$OTHER_APP_LINE"
grep -qx "rc=1" "$WORK/out" && plain_out | grep -q "unknown after launch 1" \
  && ! plain_out | grep -q "the app list opens" && ! grep -q "^tap " "$STUB_DEVICE/ops" \
  && ok "a launch without a decision for Clock stops before the app list" \
  || bad "no decision: $(plain_out | tr '\n' ';')"
STUB_LOGCAT_FAIL=1 protect "$FREE_LINE"
grep -qx "rc=1" "$WORK/out" && plain_out | grep -q "unknown after launch 1" \
  && ok "an unreadable engine log stops before the app list" || bad "the unreadable log: $(plain_out | tr '\n' ';')"

echo "P2 gate wait"
reset_device
STUB_ACTIVITIES="$LOCK_TOP" STUB_UI='<node text="Enter your PIN" />' R007_GATE_WAIT=0 \
  expect2_ok "a gate wait with a limit of 0 s still checks once" r007_wait_gate L
# With dumps of 2 s and a limit of 3 s, a wait limited in seconds ends after about 6 s; a limit counted in polls of
# 0.5 s would make 6 dumps and take about 18 s. The bound of 10 s leaves room for the stub overhead under load.
reset_device
STUB_ACTIVITIES="$LOCK_TOP" STUB_UI='<node text="World Clock" />' STUB_DUMP_DELAY=2 R007_GATE_WAIT=3 \
  p2 'start=$SECONDS; r007_wait_gate L; echo "rc=$? elapsed=$(( SECONDS - start ))"'
elapsed="$(sed -n 's/^rc=1 elapsed=//p' "$WORK/out")"
[ -n "$elapsed" ] && [ "$elapsed" -le 10 ] \
  && ok "a gate wait ends after its limit in seconds when each UI dump takes 2 s" \
  || bad "the gate wait limit: $(cat "$WORK/out")"

echo "P2 kill during the PIN check"
v_case "the kill window runs from the longest prefix tap to the write begin" "106 604" \
  r007_kill_window "1000-1080 2000-2106 3000-3090 4000-4630" 4604
expect2_fail "a tap without times gives no kill window" r007_kill_window "1000-1080 ?-? 3000-3090 4000-4630" 4604
expect2_fail "a missing write begin gives no kill window" r007_kill_window "1000-1080 2000-2106 4000-4630" ""
expect2_fail "a single tap gives no kill window" r007_kill_window "4000-4630" 4604
reset_device
STUB_UI="$PAD" p2 'r007_v_reset; r007_submit_kill 0000 4242 350 \
  && echo "kill=$R007_KILL_TIMES submissions=$R007_SUBMISSIONS tap_rc=$R007_KILL_TAP_RC main=$R007_KILL_MAIN"'
grep -qx "kill=350 380 submissions=1 tap_rc=0 main=R 13" "$WORK/out" \
  && [ "$(grep -c "^tap " "$STUB_DEVICE/ops")" = 4 ] && [ "$(tail -1 "$STUB_DEVICE/ops")" = kill ] \
  && ok "the kill follows the last tap, records its times, the tap status, and the main-thread ticks, and counts" \
  || bad "the kill during the tap: $(cat "$WORK/out")"
reset_device
STUB_UI="$PAD" STUB_KILL_TIMES="1000 1350 1380 0 1" STUB_STAT_KILL="cat: /proc/4242/task/4242/stat: No such file" \
  p2 'r007_submit_kill 0000 4242 350 && echo "tap_rc=$R007_KILL_TAP_RC main=${R007_KILL_MAIN:-none}"'
grep -qx "tap_rc=1 main=none" "$WORK/out" \
  && ok "a failed tap status is recorded, and a stat line that cannot be read gives no main-thread ticks" \
  || bad "the kill evidence: $(cat "$WORK/out")"
v_case "a stat line gives the thread state and its CPU ticks" "R 344" r007_stat_ticks \
  "9237 (com.applock) R 1025 1025 0 0 -1 4194624 18238 0 1314 0 324 20 0 0 10 -10 40 0"
v_case "a command name with a space and a parenthesis does not shift the fields" "S 7" r007_stat_ticks \
  "12 (a b) c) S 1 1 0 0 -1 0 0 0 0 0 3 4 0 0"
expect2_fail "a stat line without the CPU fields gives no ticks" r007_stat_ticks "12 (x) S 1 1"
reset_device
STUB_UI="$PAD" p2 'r007_submit_kill 0000 4242 1000'
grep -q "not 0 to 999 ms" "$WORK/out" && ! grep -q "^tap " "$STUB_DEVICE/ops" \
  && ok "a kill delay of 1000 ms is refused before any tap" || bad "the delay: $(cat "$WORK/out")"
reset_device
STUB_UI="$PAD" STUB_PROC_ANSWER=present R007_KILL_POLLS=2 \
  p2 'r007_submit_kill 0000 4242 350; echo "rc=$?"'
grep -q "no confirmed death" "$WORK/out" && grep -qx "rc=1" "$WORK/out" \
  && ok "a process that stays after the kill is not a confirmed death" || bad "the stayed process: $(cat "$WORK/out")"
reset_device
STUB_UI="$PAD" STUB_KILL_TIMES="1000 1350 1380 1 0" \
  p2 'r007_submit_kill 0000 4242 350; echo "rc=$? kill_rc=$R007_KILL_RC submissions=$R007_SUBMISSIONS"'
grep -qx "rc=1 kill_rc=1 submissions=1" "$WORK/out" && grep -q "exited with status 1" "$WORK/out" \
  && ok "a kill that exits with an error fails the submission, although the process is gone" \
  || bad "the failed kill: $(cat "$WORK/out")"
reset_device
STUB_PROC_ANSWER=present R007_KILL_POLLS=2 lib r007_kill
grep -q "last answer: present" "$WORK/out" && ok "a kill that is not confirmed names the last /proc answer" \
  || bad "the kill message: $(cat "$WORK/out")"

echo "P2 countdown, window, and rotation"
v_case "a countdown of 1:05 is 65 s" 65 r007_countdown_s '<node text="Try again in 1:05" />'
v_case "a countdown of 0:08 is 8 s" 8 r007_countdown_s '<node text="Try again in 0:08" />'
v_case "a dump without a countdown gives no seconds" "" r007_countdown_s '<node text="Enter your PIN" />'
WLOG="1.0 90 91 I R007Fault: pid=90 thread=lockout-io op=WRITE index=0 phase=BEGIN script=Normal count=6"
WLOG+=" until=1790000060000 wall=1790000000000 elapsed=5"
v_case "the lockout window is the deadline minus the wall time of the BEGIN line" 60000 r007_write_window "$WLOG" 90 0
v_case "the wall time of a BEGIN line is read" 1790000000000 r007_begin_wall "$WLOG" 90 0
expect2_fail "a write without a BEGIN line has no window" r007_write_window "$WLOG" 90 1
reset_device
STUB_ROTATION=1 expect_ok "a display at the requested rotation ends the wait" r007_wait_rotation 1
reset_device
STUB_ROTATION=1 R007_ROTATION_POLLS=2 lib r007_wait_rotation 0
grep -q "did not reach rotation 0 (last value: 1)" "$WORK/out" \
  && ok "a display that keeps another rotation fails the wait and names its rotation" \
  || bad "the rotation wait: $(cat "$WORK/out")"

echo "P2 segment helpers"
# Runs a function of a segment file (SEGMENT) with lib_p2.sh loaded; the output goes to $WORK/out.
seg() { # segment function args...
  local segment="$1"; shift
  ( source "$HERE/lib_p2.sh"; source "$HERE/p2/$segment.sh"; "$@" ) > "$WORK/out" 2>&1
}
if seg death r31a_window_ok 103 615; then ok "a kill window of 512 ms is wide enough"; else bad "the 512 ms window"; fi
window_refused() { # low high
  if seg death r31a_window_ok "$1" "$2"; then bad "the kill window '$1'-'$2' is refused"
  else ok "the kill window '$1'-'$2' is refused"; fi
}
window_refused 103 250
window_refused 103 ""
window_refused "" ""
rate_is() { # expectation(ok|fail) label reads span-ms
  local expect="$1" label="$2"; shift 2
  if seg cold_read r12a_rate_ok "$@"; then [ "$expect" = ok ] && ok "$label" || bad "$label"
  else [ "$expect" = fail ] && ok "$label" || bad "$label"; fi
}
rate_is ok "R1.2a: 41 reads over 10 s are a rate of 4 per second" 41 10000
rate_is ok "R1.2a: 53 reads over a window that a slow adb call stretched to 13.1 s are a rate of 4 per second" \
  53 13132
rate_is ok "R1.2a: 31 reads over 10 s are the lowest rate of 3 per second" 31 10000
rate_is ok "R1.2a: 51 reads over 10 s are the highest rate of 5 per second" 51 10000
rate_is fail "R1.2a: 53 reads over 10 s are more than 5 per second" 53 10000
rate_is fail "R1.2a: 25 reads over 10 s are fewer than 3 per second" 25 10000
rate_is ok "R1.2a: 37 reads over 9 s, the shortest span, are a rate of 4 per second" 37 9000
rate_is fail "R1.2a: a poll that stops after 8.75 s does not count, although its rate is 4 per second" 36 8750
rate_is fail "R1.2a: reads without a span do not count" 41 ""
seg cold_read r12a_rate 53 13132
out_is 3.96 && ok "R1.2a: 53 reads over 13.1 s print as 3.96 reads per second" || bad "the rate: $(cat "$WORK/out")"
seg cold_read r12a_rate 41 ""
out_is none && ok "R1.2a: reads without a span print the rate none" || bad "the rate without a span: $(cat "$WORK/out")"
reason_is() { # expected lines sent tap-status main-ticks
  seg death r31a_reason "$2" "$3" "$4" "$5"
  out_is "$1" \
    && ok "write lines $2, a kill at '$3' ms, tap status '$4', and '$5' main-thread ticks give '${1:-counted}'" \
    || bad "the R3.1a reason for $2 '$3' '$4' '$5': $(cat "$WORK/out")"
}
reason_is "" 0 396 0 13
reason_is "" 0 396 0 8
reason_is write 2 396 0 13
reason_is no-times 0 "" "" ""
reason_is tap-failed 0 396 1 13
reason_is no-entry 0 396 0 7
reason_is no-entry 0 396 0 ""
reset_device
( source "$HERE/lib_p2.sh"; source "$HERE/p2/cross.sh"; export STUB_ROTATION=0 R007_ROTATION_POLLS=2; rotate 1
  echo "rc=$? fails=$FAIL_COUNT" ) > "$WORK/out" 2>&1
grep -qx "rc=1 fails=1" "$WORK/out" && grep -qx "put system user_rotation 1" "$STUB_DEVICE/ops" \
  && ok "a rotation that the display does not reach fails once" || bad "the failed rotation: $(cat "$WORK/out")"
reset_device
STUB_ROTATION=0 seg cross rotate 0 && grep -qx "put system user_rotation 0" "$STUB_DEVICE/ops" \
  && ok "a rotation that the display reaches passes" || bad "the rotation: $(cat "$WORK/out")"
# X16 after an earlier repeat that left its store copy pending: the case helpers are stubs, the fixture of the new
# repeat is a different store, and the damage step ends the repeat after its save.
reset_device
( source "$HERE/lib_p2.sh"; source "$HERE/p2/cross.sh"; R007_LOG_OUT="$WORK/p2.log"
  case_start() { :; }
  case_prepare() { echo "prepare" >> "$STUB_DEVICE/ops"; echo "<map />" > "$STUB_DEVICE/$STORE"; }
  r007_store_damage() { return 1; }
  R007_STORE_SAVED="$(r007_store_save)" || exit 9; r007_store_tamper >/dev/null
  x16 2; echo "rc=$? saved=$R007_STORE_SAVED marker=$(cat "$STUB_DEVICE/files/r007/pending/store")"
  echo "new=$(sha256sum < "$STUB_DEVICE/$STORE" | cut -d' ' -f1)" ) > "$WORK/out" 2>&1
saved="$(sed -n 's/^rc=1 saved=\([0-9a-f]*\) .*/\1/p' "$WORK/out")"
copy="$(line_of "copy files/r007/lockout.orig shared_prefs/applock_lockout.xml")"; prepare="$(line_of prepare)"
[ -n "$saved" ] && grep -qx "rc=1 saved=$saved marker=$saved" "$WORK/out" && grep -qx "new=$saved" "$WORK/out" \
  && [ -n "$copy" ] && [ -n "$prepare" ] && [ "$copy" -lt "$prepare" ] \
  && ok "X16 writes a pending store copy back before its fixture, then saves and marks its own store" \
  || bad "X16 with a pending copy: $(tr '\n' ';' < "$WORK/out") ops $(tr '\n' ';' < "$STUB_DEVICE/ops")"
# Stubs of the case helpers of p2_device.sh, for a case function without the device flow: the gate of process 4242
# opens at once, a submission sees STUB_SUBMIT_STATE, and an inspection reads STUB_CASE_PAIR. Call it after the
# libraries are sourced, because it replaces r007_submit.
case_stubs() {
  yn() { if eval "$1"; then printf yes; else printf no; fi; }
  case_start() { R007_CASE_LABEL="$1"; }; case_prepare() { :; }; case_open() { R007_CASE_PID=4242; }
  reopen() { :; }; screen_cycle() { :; }; gate_now() { r007_gate_state "$(ui_xml)"; }
  case_write_held() { :; }; case_write_returned() { :; }; case_write_done() { :; }
  case_kill_inspect() { R007_PAIR="$STUB_CASE_PAIR"; R007_KILL_WALL="${STUB_CASE_KILL_WALL:-}"; }
  gate_after_restart() { printf open; }
  r007_submit() { R007_SUBMIT_STATE="${STUB_SUBMIT_STATE:-open}"; r007_count_submission; }
}
# Runs CODE in a subshell with lib_p2.sh, the segment SEGMENT, the case stubs, and an evidence file ($WORK/p2.log).
case_run() { # segment code
  rm -f "$WORK/p2.log"; : > "$WORK/p2.log"
  ( source "$HERE/lib_p2.sh"; source "$HERE/p2/$1.sh"; case_stubs; R007_LOG_OUT="$WORK/p2.log"; eval "$2" ) \
    > "$WORK/out" 2>&1
}
H06_LOG="1.0 4242 4243 I R007Fault: pid=4242 thread=lockout-io op=WRITE index=0 phase=BEGIN script=HoldBeforeCommit"
H06_LOG+=" count=1 until=0 wall=1 elapsed=1"
reset_device; printf '%s\n' "$H06_LOG" > "$STUB_DEVICE/logcat"
STUB_DUMP_FAIL=1 STUB_CASE_PAIR=1,0 case_run healthy 'h06 S 1 1'
grep -q "ui_dumps=[0-9]* gate_samples=0 .*predicted=no objective=na$" "$WORK/p2.log" \
  && ok "H06 without a UI dump that shows a gate is not as predicted, and its objective is na" \
  || bad "H06 without samples: $(grep '^## CASE' "$WORK/p2.log")"
reset_device; printf '%s\n' "$H06_LOG" > "$STUB_DEVICE/logcat"
STUB_UI='<node text="Incorrect PIN — try again" />' STUB_CASE_PAIR=1,0 case_run healthy 'h06 S 1 1'
grep -q "gate_samples=[1-9][0-9]* .*predicted=yes objective=yes$" "$WORK/p2.log" \
  && ok "H06 with gate samples and no ANR is as predicted, and its objective is met" \
  || bad "H06 with samples: $(grep '^## CASE' "$WORK/p2.log")"
# R1.2a over L8: READS failed reads of process 4242, STEP ms apart. The log clear and the 10 s wait are stubbed, so
# the capture holds all the reads.
r12a_log() { # reads step-ms
  local read
  for (( read=0; read<$1; read++ )); do
    printf '1.0 4242 4243 I R007Fault: pid=4242 thread=lockout-io op=READ index=%d phase=THREW script=Throw' "$read"
    printf ' wall=1 elapsed=%d\n' $(( 1000 + read * $2 ))
  done
}
r12a_is() { # label reads step-ms marker-pattern failures
  reset_device; r12a_log "$2" "$3" > "$STUB_DEVICE/logcat"
  STUB_CASE_PAIR=8,5 case_run cold_read 'r007_clear_log() { :; }; sleep() { :; }; R007_GATE=open; R007_FIXTURE_PAIR=8,5
    r12 S 1 a L8; echo "fails=$FAIL_COUNT"'
  grep -q "$4" "$WORK/p2.log" && grep -qx "fails=$5" "$WORK/out" && ok "$1" \
    || bad "$1: $(grep '^## CASE' "$WORK/p2.log") $(tail -1 "$WORK/out")"
}
r12a_is "R1.2a: 41 reads over 10 s are as predicted, with the rate in the marker" 41 250 \
  "failed_reads=41 read_span_ms=10000 reads_per_s=4.00 gate_after_fault=none .*predicted=yes objective=no$" 0
r12a_is "R1.2a: 53 reads at the 250 ms poll over a window of 13 s are as predicted" 53 250 \
  "failed_reads=53 read_span_ms=13000 reads_per_s=4.00 .*predicted=yes objective=no$" 0
r12a_is "R1.2a: 53 reads over 10 s are not as predicted and fail once, with the objective na" 53 192 \
  "failed_reads=53 read_span_ms=9984 reads_per_s=5.21 .*predicted=no objective=na$" 1
# The case selection of the cold-read segment, with the real caller_on and case_on of p2_device.sh and one repeat. Each
# case function prints its call.
P2_SELECTORS="$(sed -n '/^caller_on() /p; /^case_on() /p' "$HERE/p2_device.sh")"
selection_stubs() {
  reps() { printf 1; }
  r11a() { echo "r11a $*"; }; r11b() { echo "r11b $*"; }; r12() { echo "r12 $*"; }; r12e() { echo "r12e $*"; }
  r13() { echo "r13 $*"; }; r14ab() { echo "r14ab $*"; }; r14c() { echo "r14c $*"; }
}
selection_is() { # label expected-calls callers cases
  SELECTED_CALLERS="$3" SELECTED_CASES="$4" case_run cold_read 'eval "$P2_SELECTORS"; selection_stubs
    R007_CALLERS="$SELECTED_CALLERS"; R007_CASES="$SELECTED_CASES"; segment_run'
  [ "$(tr '\n' ';' < "$WORK/out")" = "$2" ] && ok "$1" || bad "$1: $(tr '\n' ';' < "$WORK/out")"
}
[ "$(grep -c '()' <<< "$P2_SELECTORS")" = 2 ] && ok "the selection test reads caller_on and case_on of p2_device.sh" \
  || bad "the selectors of p2_device.sh: $P2_SELECTORS"
selection_is "the case selection R1.2a runs only R1.2a, over both fixtures, for each caller" \
  "r12 S 1 a L8;r12 S 1 a Z;r12 L 1 a L8;r12 L 1 a Z;" "S L" R1.2a
selection_is "the case selection R1.4b runs the cold-start hold only for caller L" "r14ab L 1;" "S L" R1.4b
selection_is "the case selection R1.1a with caller S runs no case" "" S R1.1a
SELECTED_CALLERS="S L" SELECTED_CASES="" case_run cold_read 'eval "$P2_SELECTORS"; selection_stubs
  R007_CALLERS="$SELECTED_CALLERS"; R007_CASES="$SELECTED_CASES"; segment_run'
[ "$(wc -l < "$WORK/out")" = 33 ] && [ "$(head -1 "$WORK/out")" = "r11a 1" ] \
  && [ "$(tail -1 "$WORK/out")" = "r14c L 1" ] \
  && ok "without a case selection, the cold-read segment runs all 33 case calls" \
  || bad "the cold-read segment without a selection: $(tr '\n' ';' < "$WORK/out")"
# R2.1: the write ended 30 s before the stub device time, so the kill is due at once; the kill time is the stub.
R21_LOG="1.0 4242 4243 I R007Fault: pid=4242 thread=lockout-io op=WRITE index=0 phase=RETURNED"
R21_LOG+=" script=ReturnFalseBeforeCommit result=false wall=1789999970000 elapsed=1"
for kill_wall in 1789999996000 1790000010000; do
  reset_device; printf '%s\n' "$R21_LOG" > "$STUB_DEVICE/logcat"
  STUB_UI='<node text="Try again in 0:05" />' STUB_CASE_PAIR=0,0 STUB_CASE_KILL_WALL="$kill_wall" \
    case_run degraded 'r21 S 1 ReturnFalseBeforeCommit 25; echo "fails=$FAIL_COUNT"'
  case "$kill_wall" in
    1789999996000) grep -q "lost_enforcement_ms=4000 .*predicted=yes objective=no$" "$WORK/p2.log" \
      && grep -qx "fails=0" "$WORK/out" \
      && ok "R2.1 with a kill 4 s before the end of the degraded lock shows the loss" \
      || bad "R2.1 in time: $(grep '^## CASE' "$WORK/p2.log") $(tail -1 "$WORK/out")" ;;
    *) grep -q "lost_enforcement_ms=-10000 .*objective=na$" "$WORK/p2.log" && grep -qx "fails=1" "$WORK/out" \
      && grep -q "10000 ms after the end of the degraded lock" "$WORK/out" \
      && ok "R2.1 with a kill after the end of the degraded lock fails once, with the objective na" \
      || bad "R2.1 late: $(grep '^## CASE' "$WORK/p2.log") $(tail -1 "$WORK/out")" ;;
  esac
done
# R2.4 kill at 25 s, with the same write end: 26.5 s after it is in time, 31 s after it is after the end of the lock.
for kill_wall in 1789999996500 1790000001000; do
  reset_device; printf '%s\n' "$R21_LOG" > "$STUB_DEVICE/logcat"
  STUB_CASE_PAIR=0,0 STUB_CASE_KILL_WALL="$kill_wall" case_run degraded 'r24_deadline S 1 25; echo "fails=$FAIL_COUNT"'
  case "$kill_wall" in
    1789999996500) grep -q "kill_after_return_ms=26500 .*predicted=yes objective=no$" "$WORK/p2.log" \
      && grep -qx "fails=0" "$WORK/out" \
      && ok "an R2.4 kill at 25 s that lands before the end of the lock shows the loss" \
      || bad "R2.4 in time: $(grep '^## CASE' "$WORK/p2.log") $(tail -1 "$WORK/out")" ;;
    *) grep -q "kill_after_return_ms=31000 .*objective=na$" "$WORK/p2.log" && grep -qx "fails=1" "$WORK/out" \
      && grep -q "1000 ms after the end of the lock" "$WORK/out" \
      && ok "an R2.4 kill at 25 s that lands after the end of the lock fails once, with the objective na" \
      || bad "R2.4 late: $(grep '^## CASE' "$WORK/p2.log") $(tail -1 "$WORK/out")" ;;
  esac
done
# R1.4a with the read held from elapsed 100 ms to 40100 ms. Each sleep moves the clock 15 s on instead of waiting, so
# the hold loop makes 2 dumps. Without a dump that returns a screen, the ANR record is unknown; with one, it is none.
R14_LINE="I R007Fault: pid=4242 thread=main op=READ index=0 phase"
R14_LOG="$(printf '%s\n' "1.0 4242 4242 $R14_LINE=BEGIN script=HoldThenRead wall=1 elapsed=100" \
  "1.1 4242 4242 $R14_LINE=HELD script=HoldThenRead" \
  "1.2 4242 4242 $R14_LINE=RETURNED count=5 until=1 wall=2 elapsed=40100")"
R14_STUBS='r007_stop_app() { :; }; r007_fixture() { R007_FIXTURE_PAIR=5,1; }; r007_set_faults() { :; }
  r007_proc_state() { printf present; }; sleep() { SECONDS=$((SECONDS + 15)); }; r14ab S 1'
# The events buffer of the working case has an am_anr line of the case process 40 s after the HELD line (epoch 1.1),
# and an am_kill line of another process.
R14_EVENTS="$(printf '%s\n' "--------- beginning of events" \
  "          41.100  1871  2950 I am_anr  : [0,4242,com.applock,952745540,executing service com.applock/.Detector]" \
  "          41.500  1871  2950 I am_kill : [0,14242,com.applock,0,bg anr,121920]")"
for dumps in failing working; do
  reset_device; printf '%s\n' "$R14_LOG" > "$STUB_DEVICE/logcat"
  if [ "$dumps" = failing ]; then
    STUB_DUMP_FAIL=1 STUB_CASE_PAIR=5,1 case_run cold_read "$R14_STUBS"
    grep -q "main_thread_read_ms=40000 anr_dialog_after_s=unknown ui_dumps=2 usable_dumps=0 " "$WORK/p2.log" \
      && ok "R1.4 with no dump that returns a screen records the ANR dialog as unknown" \
      || bad "R1.4 failing dumps: $(grep '^## CASE' "$WORK/p2.log")"
    grep -q "usable_dumps=0 am_anr=none am_kill=none gate_after=" "$WORK/p2.log" \
      && ok "R1.4 with no events of the case process records am_anr and am_kill as none" \
      || bad "R1.4 without events: $(grep '^## CASE' "$WORK/p2.log")"
  else
    printf '%s\n' "$R14_EVENTS" > "$STUB_DEVICE/events"
    STUB_UI='<node text="Enter your PIN" />' STUB_CASE_PAIR=5,1 case_run cold_read "$R14_STUBS"
    grep -q "anr_dialog_after_s=none ui_dumps=2 usable_dumps=2 .*predicted=yes objective=no$" "$WORK/p2.log" \
      && ok "R1.4 with dumps that show no ANR dialog records none" \
      || bad "R1.4 working dumps: $(grep '^## CASE' "$WORK/p2.log")"
    grep -q " am_anr=40s:executing_service_com.applock/.Detector am_kill=none " "$WORK/p2.log" \
      && ok "R1.4 records the am_anr event of the case process with its time after the hold began" \
      || bad "R1.4 with events: $(grep '^## CASE' "$WORK/p2.log")"
  fi
done
# R1.4 with a death during the hold (the HELD line at epoch 1.1). For L, the death is as predicted only with an am_anr
# event of the case process at the start of the hold or later; for S, a death is not as predicted.
R14_DEATH_STUBS='r007_stop_app() { :; }; r007_fixture() { R007_FIXTURE_PAIR=5,1; }; r007_set_faults() { :; }
  r007_grant_write() { :; }; r007_revoke_detector() { :; }; r007_proc_state() { printf absent; }
  r007_inspect_checked() { printf "r007_count=5\nr007_lockout_until=1\n"; }; sleep() { SECONDS=$((SECONDS + 15)); }'
anr_kill_events() { # epoch
  printf '%s\n' "          $1  1871  2950 I am_anr  : [0,4242,com.applock,952745540,executing service com.applock/.D]" \
    "          $1  1871  2950 I am_kill : [0,4242,com.applock,0,bg anr,121920]"
}
while IFS='|' read -r caller anr_epoch expected label; do
  reset_device; printf '%s\n' "$R14_LOG" > "$STUB_DEVICE/logcat"
  [ "$anr_epoch" = none ] || anr_kill_events "$anr_epoch" > "$STUB_DEVICE/events"
  STUB_UI='<node text="Enter your PIN" />' case_run cold_read "$R14_DEATH_STUBS; r14ab $caller 1" </dev/null
  grep -qE "process_died_after_s=[0-9]+ .*predicted=$expected objective=no$" "$WORK/p2.log" && ok "$label" \
    || bad "$label: $(grep '^## CASE' "$WORK/p2.log")"
done <<'VARIANTS'
L|41.100|yes|R1.4b with a death and an am_anr event of the case process 40 s into the hold is as predicted
L|none|no|R1.4b with a death and no am_anr event of the case process is not as predicted
L|0.000|no|R1.4b with an am_anr event of the pid 1 s before the hold began is not as predicted
S|41.100|no|R1.4a with a death is not as predicted, also with an am_anr event of the case process
VARIANTS
# r007_proc_events selects the lines of one pid (not of a pid that ends with the same digits), keeps the commas of an
# ANR subject, and drops the size field of am_kill.
reset_device
printf '%s\n' "--------- beginning of events" \
  "          40.600  1871  2950 I am_anr  : [0,4242,com.applock,952745540,Input dispatching timed out (a, b)]" \
  "          41.200  1871  2950 I am_kill : [0,4242,com.applock,0,bg anr,121920]" \
  "          41.300  1871  2950 I am_kill : [0,14242,com.applock,0,stop com.applock due to finished inst,1]" \
  "          41.400  1871  2950 I am_proc_died: [0,4242,com.applock,0,2]" > "$STUB_DEVICE/events"
seg cold_read r007_proc_events 4242
[ "$(cat "$WORK/out")" = "am_anr 40.600 Input dispatching timed out (a, b)
am_kill 41.200 bg anr" ] && ok "r007_proc_events gives the am_anr and am_kill events of one process" \
  || bad "r007_proc_events: $(tr '\n' ';' < "$WORK/out")"
seg cold_read r14_events 4242 ""
grep -qx "am_anr=unknown:Input_dispatching_timed_out_(a,_b) am_kill=unknown:bg_anr" "$WORK/out" \
  && ok "r14_events without the time of the HELD line records the event times as unknown" \
  || bad "r14_events without a HELD time: $(cat "$WORK/out")"
STUB_LOGCAT_FAIL=1 seg cold_read r14_events 4242 1.1
grep -qx "am_anr=unknown am_kill=unknown" "$WORK/out" \
  && ok "r14_events records am_anr and am_kill as unknown when logcat cannot be read" \
  || bad "r14_events with a failed logcat: $(cat "$WORK/out")"
# R3.3 with a release that always fails: each submission moves the clock 50 s on, so the release is due at once.
reset_device
STUB_UI='<node text="Enter your PIN" />' STUB_RELEASE_FAIL=1 STUB_SUBMIT_STATE=blocked STUB_CASE_PAIR=0,0 \
  R33_RELEASE_WAIT=2 case_run death 'r007_submit() { R007_SUBMIT_STATE=blocked; SECONDS=$((SECONDS + 50)); }
    start_s=$SECONDS; begin=$(date +%s); r33 S 1 40s; echo "rc=$? fails=$FAIL_COUNT took=$(( $(date +%s) - begin ))"'
took="$(sed -n 's/^rc=1 fails=1 took=//p' "$WORK/out")"
[ -n "$took" ] && [ "$took" -le 10 ] && grep -q "could not be released ([0-9]* tries)" "$WORK/out" \
  && ok "R3.3 stops a release that keeps failing after its wait, with one counted failure" \
  || bad "R3.3 release retries: $(tail -3 "$WORK/out" | tr '\n' ';')"
reset_device
STUB_UI='<node text="Use PIN" bounds="[100,200][300,400]" />' seg biometric tap_use_pin \
  && grep -qx "tap 200 300" "$STUB_DEVICE/ops" && ok "the Use PIN button is tapped at the middle of its text" \
  || bad "the Use PIN tap: $(cat "$WORK/out") ops $(tr '\n' ';' < "$STUB_DEVICE/ops")"

echo "UI dump and unlock wait"
reset_device
{ echo '<node content-desc="Open vault" />'
  for (( i=0; i<6000; i++ )); do echo "<node text=\"filler $i\" bounds=\"[0,0][1,1]\" />"; done; } > "$WORK/big.xml"
STUB_UI_FILE="$WORK/big.xml" expect_ok "a match at the top of a UI dump of about 290 KB is found" \
  ui_has 'content-desc="Open vault"'
STUB_UI_FILE="$WORK/big.xml" expect_fail "a text that a UI dump of about 290 KB lacks is not found" \
  ui_has 'content-desc="Settings"'
reset_device
( source "$HERE/lib_r007.sh"; STUB_UI='<node text="Enter your PIN" />' ui_xml > /dev/null
  STUB_DUMP_FAIL=1 ui_xml ) > "$WORK/out" 2>&1
[ ! -s "$WORK/out" ] && ok "a failed UI dump gives no output, not the previous dump" \
  || bad "the failed dump: $(cat "$WORK/out")"
reset_device
expect2_ok "an unlocked device needs no operator" r007_wait_unlock
! grep -q "ACTION" "$WORK/out" && ok "no unlock request is printed for an unlocked device" \
  || bad "the unlock request: $(cat "$WORK/out")"
reset_device
STUB_ACTIVITIES="    mKeyguardShowing=true" R007_UNLOCK_WAIT=2 \
  expect2_fail "a device that stays locked fails the wait" r007_wait_unlock
grep -q "ACTION: unlock the device now" "$WORK/out" && ok "the wait for a locked device asks the operator to unlock" \
  || bad "the unlock request: $(cat "$WORK/out")"

echo "device clock"
reset_device
v_case "a device time of 13 digits is read" 1790000000000 r007_device_wall
STUB_NOW_MS="" expect2_fail "an empty device time is refused" r007_device_wall
STUB_NOW_MS=1790000000 expect2_fail "a device time in seconds is refused" r007_device_wall
STUB_NOW_MS="1790000000%3N" expect2_fail "a device time without ms support is refused" r007_device_wall
expect2_fail "a countdown check without the device time after the dump fails" r007_countdown_ok 36 100000 60000 ""

echo "wrapper phase lines"
PLOG="1.0 60 61 I R007Fault: pid=60 thread=main op=READ index=0 phase=BEGIN script=Normal wall=1 elapsed=100
1.1 60 61 I R007Fault: pid=60 thread=main op=READ index=0 phase=RETURNED count=0 until=0 wall=2 elapsed=158
1.2 60 62 I R007Fault: pid=60 thread=lockout-io op=READ index=1 phase=THREW script=Throw wall=3 elapsed=200
1.3 60 62 I R007Fault: pid=60 thread=lockout-io op=WRITE index=0 phase=REAL_RESULT result=true wall=4 elapsed=210
1.4 61 63 I R007Fault: pid=61 thread=main op=READ index=0 phase=BEGIN script=Normal wall=5 elapsed=300"
v_case "the READ BEGIN lines of one process are counted" 1 r007_phase_count "$PLOG" 60 READ BEGIN
v_case "a phase pattern counts each matching phase" 2 r007_phase_count "$PLOG" 60 READ "(BEGIN|RETURNED)"
v_case "a WRITE line of any phase is counted" 1 r007_phase_count "$PLOG" 60 WRITE "[A-Z_]+"
v_case "the elapsed time of a phase line is read" 158 r007_phase_elapsed "$PLOG" 60 READ 0 RETURNED
v_case "a line of another thread is not read" "" r007_phase_elapsed "$PLOG" 60 READ 1 THREW main
v_case "a line of the named thread is read" 200 r007_phase_elapsed "$PLOG" 60 READ 1 THREW lockout-io
v_case "the span runs from the first to the last READ line of one process" 100 r007_phase_span "$PLOG" 60 READ "[A-Z_]+"
v_case "the span uses only the lines of the matching phases" 58 r007_phase_span "$PLOG" 60 READ "(BEGIN|RETURNED)"
v_case "a single matching line gives no span" "" r007_phase_span "$PLOG" 61 READ BEGIN

echo "H05 reboot verdict"
h05_is() { # expectation(ok|fail) label args...
  local expect="$1" label="$2"; shift 2
  if seg reboot h05_reboot_ok "$@"; then [ "$expect" = ok ] && ok "$label" || bad "$label"
  else [ "$expect" = fail ] && ok "$label" || bad "$label"; fi
}
h05_is ok "both callers with fitting gates over the stored pair match" 8,5 8,5 yes yes "S L"
h05_is fail "a self-gate that does not fit does not match" 8,5 8,5 no yes "S L"
h05_is fail "an empty result of a selected caller does not match" 8,5 8,5 "" yes "S L"
h05_is ok "a caller that the run does not select needs no result" 8,5 8,5 "" yes L
h05_is ok "a fitting self-gate alone matches" 8,5 8,5 yes "" S
h05_is fail "another stored pair does not match" 8,5 8,6 yes yes "S L"
h05_is fail "a legacy gate that does not fit does not match" 8,5 8,5 yes no "S L"

echo "reloaded gate against the stored deadline"
# Dumps from 60.000 s to 64.000 s, as in the countdown check.
reload_is() { # expectation(ok|fail) label state countdown deadline [t0 t1]
  local expect="$1" label="$2"; shift 2
  if lib2 r007_reload_ok "$1" "$2" "$3" "${4:-60000}" "${5:-64000}"; then
    [ "$expect" = ok ] && ok "$label" || bad "$label"
  else [ "$expect" = fail ] && ok "$label" || bad "$label"; fi
}
reload_is ok "a blocked gate with a fitting countdown fits a deadline ahead" blocked 36 100000
reload_is fail "a countdown of 5999 s does not fit 36 s left" blocked 5999 100000
reload_is fail "a blocked gate without a countdown does not fit" blocked "" 100000
reload_is fail "an open gate does not fit a deadline ahead" open "" 100000
reload_is ok "an open gate fits a deadline before the dump" open "" 50000
reload_is fail "a blocked gate does not fit a deadline before the dump" blocked 1 50000
reload_is ok "an incorrect-PIN gate fits a pair without a deadline" incorrect "" 0
reload_is ok "either state fits a deadline inside the dump" open "" 62000
if lib2 r007_reload_ok blocked 36 100000 60000 ""; then bad "a dump without a device time after it does not fit"
else ok "a dump without a device time after it does not fit"; fi
# reboot_gate on the self-gate: 600 s left at the dump (the stub device time is fixed).
MAIN_TOP="    topResumedActivity=ActivityRecord{1 u0 com.applock/.presentation.applist.MainActivity t9}"
for shown in "10:00" "99:59"; do
  reset_device
  STUB_ACTIVITIES="$MAIN_TOP" STUB_UI="<node text=\"Try again in $shown\" />" \
    seg reboot eval 'reboot_gate S 1790000600000; echo "gate=$REBOOT_GATE countdown=$REBOOT_COUNTDOWN ok=$REBOOT_OK"'
  case "$shown" in
    10:00) grep -qx "gate=blocked countdown=600 ok=yes" "$WORK/out" \
      && ok "a reloaded self-gate that shows 10:00 with 600 s left fits" || bad "10:00: $(cat "$WORK/out")" ;;
    *) grep -qx "gate=blocked countdown=5999 ok=no" "$WORK/out" \
      && ok "a reloaded self-gate that shows 99:59 with 600 s left does not fit" || bad "99:59: $(cat "$WORK/out")" ;;
  esac
done

echo "P2 exit cleanup"
INSPECT0="${INSPECT4//r007_count=4/r007_count=0}"
MARKERS0="${MARKERS4//r007_count=4/r007_count=0}"
cleanup_run() { # extra-setup
  reset_device; printf '%s\n' "$FIXTURE_MARKERS" "$MARKERS0" > "$STUB_DEVICE/logcat"
  ( source "$HERE/lib_p2.sh"; R007_LOG_OUT="$WORK/p2.log"
    R007_STORE_SAVED="$(r007_store_save)" || exit 9; r007_store_tamper >/dev/null
    export STUB_FIXTURE_OUT="$(fixture_out 0 0 true)" STUB_INSTRUMENT="$INSPECT0"; eval "$1"
    r007_p2_cleanup; echo "rc=$? saved=${R007_STORE_SAVED:-none}" ) > "$WORK/out" 2>&1
}
cleanup_run ""
copy="$(line_of "copy files/r007/lockout.orig shared_prefs/applock_lockout.xml")"
fixture="$(line_of "instrument fixture")"
grep -qx "rc=0 saved=none" "$WORK/out" && [ -n "$copy" ] && [ -n "$fixture" ] && [ "$copy" -lt "$fixture" ] \
  && [ ! -e "$STUB_DEVICE/files/r007" ] \
  && ok "the exit cleanup writes a pending store copy back before the fixture Z and removes the control directory" \
  || bad "the exit cleanup order: $(tr '\n' ';' < "$STUB_DEVICE/ops") $(plain_out | tail -3 | tr '\n' ';')"
cleanup_run "export STUB_COPY_FAIL=1"
grep -qx "rc=1 saved=[0-9a-f]*" "$WORK/out" && ! grep -q "^instrument fixture" "$STUB_DEVICE/ops" \
  && [ -e "$STUB_DEVICE/files/r007/lockout.orig" ] && [ -e "$STUB_DEVICE/files/r007/pending/store" ] \
  && ok "a failed restore of the store copy keeps the copy and its marker, and writes no fixture" \
  || bad "the failed restore at exit: $(plain_out | tail -3 | tr '\n' ';')"

printf '\n%d tests, %d failed\n' "$TESTS" "$BAD"
[ "$BAD" -eq 0 ]
