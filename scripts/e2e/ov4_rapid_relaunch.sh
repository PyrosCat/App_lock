#!/usr/bin/env bash
# OV-4 — rapid-relaunch overlay race (R-002 / the F4 fast-relaunch bypass).
# WP1: a thin wrapper over the WP0 durable instrumentation test `OverlayRaceUiTest`
# (via `am instrument`) — the single race truth. The test self-grants appops + arranges the
# spike FGS in @Before, fires K rapid `am start` bursts at a NORMAL target app, samples
# `dumpsys window` z-order, and scores each burst TOP/BEHIND/ABSENT (ABSENT=0 hard,
# BEHIND<=2%, §11). WP2 repoints the test to production; this wrapper is unchanged.
#
# Counts (override via env): OV4_BURSTS(25) OV4_RELAUNCHES(20) OV4_REPEAT(1)
#   OV4_T_APPEAR_MS(unset -> test default 1500). Slow software-GPU NucBox lanes straddle
#   1500 ms (WP0 finding), so raise OV4_T_APPEAR_MS there (diagnostic, non-gate); the clean
#   ABSENT=0 gate pass is real hardware (Moto G).
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; source "$HERE/lib.sh"

OV4_BURSTS="${OV4_BURSTS:-25}"
OV4_RELAUNCHES="${OV4_RELAUNCHES:-20}"
OV4_REPEAT="${OV4_REPEAT:-1}"
OV4_T_APPEAR_MS="${OV4_T_APPEAR_MS:-}"     # empty -> the test's built-in 1500 ms default
TEST_CLASS="${TEST_CLASS:-com.applock.e2e.OverlayRaceUiTest}"
INSTRUMENTATION="${INSTRUMENTATION:-${APP_ID}.test/androidx.test.runner.AndroidJUnitRunner}"
ANDROIDTEST_APK="${ANDROIDTEST_APK:-$HERE/../../app/build/outputs/apk/androidTest/prod/debug/app-prod-debug-androidTest.apk}"

# CR-001: OverlayRaceUiTest's driver is spike-hardcoded (launcher / FGS / STOP+DISMISS actions /
# POLL_SERVICE / OVERLAY_TITLE). A production OV-4 scenario is a WP2 deliverable (repoint the test
# + build the prod driver). Until then refuse non-spike so we never emit a production PASS from the
# spike test.
if [ "${LOCK_ENGINE:-spike}" != spike ]; then
  fail "OV-4: LOCK_ENGINE=${LOCK_ENGINE:-spike} rejected — the OV-4 test is spike-hardcoded; the production OV-4 scenario lands at WP2."
  summary "OV-4"; exit 1
fi

# CR-003: fail-closed on non-positive / non-integer counts (a vacuous run must not PASS).
for v in OV4_BURSTS OV4_RELAUNCHES OV4_REPEAT; do
  is_pos_int "${!v}" || { fail "OV-4: $v='${!v}' must be a positive integer"; summary "OV-4"; exit 1; }
done
[ -n "$OV4_T_APPEAR_MS" ] && { is_pos_int "$OV4_T_APPEAR_MS" || { fail "OV-4: OV4_T_APPEAR_MS='$OV4_T_APPEAR_MS' must be a positive integer"; summary "OV-4"; exit 1; }; }

require_device || exit 2

# CR-005 + reinstall-every-run: install a FRESH androidTest APK each run and record its hash, so a
# stale on-device test build can never be cited as evidence (the conditional-install version could
# keep a pre-registered stale APK). USE_PREINSTALLED=1 keeps whatever is registered (diagnostic;
# provenance NOT established).
if [ "${USE_PREINSTALLED:-}" = 1 ]; then
  sh_ pm list instrumentation | grep -q "${APP_ID}.test/" || { fail "OV-4: USE_PREINSTALLED=1 but ${APP_ID}.test is not registered"; summary "OV-4"; exit 1; }
  info "USE_PREINSTALLED=1 — using the already-registered instrumentation (provenance NOT established)"
elif [ -f "$ANDROIDTEST_APK" ]; then
  hash="$(sha256sum "$ANDROIDTEST_APK" 2>/dev/null | cut -c1-16)"
  info "installing androidTest APK $(basename "$ANDROIDTEST_APK") (sha256 ${hash:-unknown}…)"
  out="$(adbx install -r -g "$(host_path "$ANDROIDTEST_APK")" 2>&1)"
  printf '%s' "$out" | grep -q 'Success' || { fail "OV-4: androidTest install did not report Success: $(printf '%s' "$out" | tr '\n' ' ' | tail -c 200)"; summary "OV-4"; exit 1; }
else
  fail "OV-4: androidTest APK not found at $ANDROIDTEST_APK (build :app:assembleProdDebugAndroidTest, set ANDROIDTEST_APK, or USE_PREINSTALLED=1)"; summary "OV-4"; exit 1
fi
sh_ pm list instrumentation | grep -q "${APP_ID}.test/" || { fail "OV-4: instrumentation ${APP_ID}.test not registered after install"; summary "OV-4"; exit 1; }

args=(-e class "$TEST_CLASS" -e ov4_bursts "$OV4_BURSTS" -e ov4_relaunches "$OV4_RELAUNCHES" -e ov4_repeat "$OV4_REPEAT")
[ -n "$OV4_T_APPEAR_MS" ] && args+=(-e ov4_t_appear_ms "$OV4_T_APPEAR_MS")

step "OV-4 overlay race (instrumentation): ${OV4_BURSTS}×${OV4_RELAUNCHES}×${OV4_REPEAT}${OV4_T_APPEAR_MS:+, T_appear=${OV4_T_APPEAR_MS}ms}"
boost_logcat; sh_ logcat -c    # fresh buffer so we read THIS run's count line (M7SpikeTest)
out="$(adbx shell am instrument -w "${args[@]}" "$INSTRUMENTATION" 2>&1 | tr -d '\r')"
counts="$(sh_ logcat -d -s M7SpikeTest | grep -F 'OV-4 overlay race:' | tail -1)"

# assumeTrue-skip (no normal target app) is a MISCONFIG, not a pass.
if printf '%s' "$out" | grep -qiE 'no suitable target app|AssumptionViolated|OK \(0 tests\)'; then
  fail "OV-4: SKIPPED — no normal target app (use an aosp/default image, not aosp-atd). $counts"
  summary "OV-4"; exit 1
fi

# CR-007: require a retained count line and validate its arithmetic before trusting the verdict.
if [ -z "$counts" ]; then
  fail "OV-4: no 'OV-4 overlay race:' count line in logcat — cannot retain/validate evidence"
  printf '%s\n' "$out" | tail -6 | sed 's/^/    /'; summary "OV-4"; exit 1
fi
top=$(printf '%s' "$counts" | grep -oE 'TOP=[0-9]+' | grep -oE '[0-9]+$')
behind=$(printf '%s' "$counts" | grep -oE 'BEHIND=[0-9]+' | grep -oE '[0-9]+$')
absent=$(printf '%s' "$counts" | grep -oE 'ABSENT=[0-9]+' | grep -oE '[0-9]+$')
of=$(printf '%s' "$counts" | grep -oE 'of [0-9]+' | grep -oE '[0-9]+$')
expected=$(( OV4_BURSTS * OV4_REPEAT ))
if [ -z "$top" ] || [ -z "$behind" ] || [ -z "$absent" ] || [ -z "$of" ]; then
  fail "OV-4: count line unparseable: $counts"; summary "OV-4"; exit 1
elif [ "$(( top + behind + absent ))" -ne "$of" ] || [ "$of" -ne "$expected" ]; then
  fail "OV-4: count arithmetic mismatch — sum=$(( top + behind + absent )), of=$of, expected bursts*repeat=$expected. $counts"
  summary "OV-4"; exit 1
fi

# Instrumentation verdict (arithmetic already validated above).
if printf '%s' "$out" | grep -qiE 'ABSENT must be|BEHIND over|FAILURES'; then
  fail "OV-4: $counts"
  printf '%s\n' "$out" | grep -iE 'ABSENT must be|BEHIND over|^FAILURES' | head -4 | sed 's/^/    /'
elif printf '%s' "$out" | grep -q 'OK (1 test)'; then
  pass "OV-4: $counts${OV4_T_APPEAR_MS:+  [diagnostic: scaled T_appear=${OV4_T_APPEAR_MS}ms — non-gate]}"
else
  fail "OV-4: instrumentation gave no clear result (device booted? test APK matches the app build?)"
  printf '%s\n' "$out" | tail -6 | sed 's/^/    /'
fi

summary "OV-4"
