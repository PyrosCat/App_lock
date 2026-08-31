#!/usr/bin/env bash
# run_all — the M7 security-regression gate. Runs the checks N times (default 2, for the
# "N/N consecutive" exit criterion) and prints a markdown summary block ready to paste into
# a dated report under docs/reports/campaigns/.
#
#   ./run_all.sh [-s SERIAL] [-n RUNS] [--skip-setup]
#
# Engine-aware (LOCK_ENGINE, default spike):
#   spike (WP1): OV-4 (overlay race) + neg_overlay_grant (missing-grant negative control) —
#                what the WP0 spike can exercise; setup_device.sh smoke-tests detection→overlay.
#                The prod-path checks (PIN/relock/self-gate) are validated at WP2.
#   prod (WP2+): smoke_core + OV-3 + OV-4 + F3 (the full suite, once the real engine exists).
# The §11 GATE profile is OV4 50x20x5 at the fixed 1500 ms T_appear; anything lighter or with a
# scaled T_appear is labelled NON-GATE (diagnostic) so it is never mistaken for gate evidence.
# Run before AND after any change to the lock engine, detector, session manager, or self-gate.
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
source "$HERE/lib.sh"    # is_pos_int
RUNS=2; SKIP_SETUP=""; SERIAL_ARG=""
while [ $# -gt 0 ]; do case "$1" in
  -s) SERIAL_ARG="$2"; shift 2;;
  -n) RUNS="$2"; shift 2;;
  --skip-setup) SKIP_SETUP=1; shift;;
  *) echo "unknown arg: $1"; exit 2;;
esac; done
[ -n "$SERIAL_ARG" ] && export SERIAL="$SERIAL_ARG"

LOCK_ENGINE="${LOCK_ENGINE:-spike}"; export LOCK_ENGINE
# CR-003: fail-closed on an invalid engine or a vacuous run count — never PASS without an
# executed profile (a bare `-n 0` used to print "PASS (0/0)").
case "$LOCK_ENGINE" in spike|prod) ;; *) echo "invalid LOCK_ENGINE='$LOCK_ENGINE' (want spike|prod)"; exit 2;; esac
is_pos_int "$RUNS" || { echo "invalid -n RUNS='$RUNS' (want a positive integer)"; exit 2; }
detect_serial >/dev/null 2>&1 && export SERIAL || true   # so the report block can read OV-4 counts from logcat

if [ "$LOCK_ENGINE" = spike ]; then
  CHECKS=(ov4_rapid_relaunch neg_overlay_grant)
else
  CHECKS=(smoke_core ov3_fast_switch ov4_rapid_relaunch f3_self_gate)
fi

# CR-003: §11 gate profile, or a lighter/diagnostic run?
gate_profile="no"
if [ "$LOCK_ENGINE" = spike ] \
   && [ "${OV4_BURSTS:-25}" = 50 ] && [ "${OV4_RELAUNCHES:-20}" = 20 ] && [ "${OV4_REPEAT:-1}" = 5 ] \
   && [ -z "${OV4_T_APPEAR_MS:-}" ]; then
  gate_profile="yes"
fi

echo "### engine: $LOCK_ENGINE · checks: ${CHECKS[*]} · runs: $RUNS · gate-profile: $gate_profile"
declare -A RESULT

if [ -z "$SKIP_SETUP" ]; then
  echo "### provisioning device"
  bash "$HERE/setup_device.sh" || { echo "setup failed — aborting"; exit 1; }
fi

overall=0
for run in $(seq 1 "$RUNS"); do
  echo; echo "==================== RUN $run/$RUNS ===================="
  for chk in "${CHECKS[@]}"; do
    if bash "$HERE/$chk.sh"; then RESULT["$chk,$run"]=PASS; else RESULT["$chk,$run"]=FAIL; overall=1; fi
  done
done

# ---- markdown summary (paste into docs/reports/campaigns/) ----
echo; echo "----- report block -----"
echo "engine=$LOCK_ENGINE  gate-profile=$gate_profile  OV4=${OV4_BURSTS:-25}x${OV4_RELAUNCHES:-20}x${OV4_REPEAT:-1}${OV4_T_APPEAR_MS:+ T_appear=${OV4_T_APPEAR_MS}ms}"
echo "| Check | $(for r in $(seq 1 "$RUNS"); do printf 'Run %s | ' "$r"; done)"
echo "|---|$(for r in $(seq 1 "$RUNS"); do printf -- '---|'; done)"
for chk in "${CHECKS[@]}"; do
  row="| \`$chk\` |"
  for r in $(seq 1 "$RUNS"); do row+=" ${RESULT[$chk,$r]:-?} |"; done
  echo "$row"
done
# Retain the validated OV-4 counts IN the report block (not just earlier in the run log).
if [ "$LOCK_ENGINE" = spike ]; then
  ov4counts="$(sh_ logcat -d -s M7SpikeTest 2>/dev/null | grep -F 'OV-4 overlay race:' | tail -n "$RUNS")"
  echo
  echo "OV-4 counts (M7SpikeTest logcat, last $RUNS):"
  if [ -n "$ov4counts" ]; then printf '%s\n' "$ov4counts" | sed 's/^/  /'; else echo "  (none captured — see the run output above)"; fi
fi
echo
if [ "$overall" -ne 0 ]; then
  echo "VERDICT: FAIL (see above)"
elif [ "$gate_profile" = yes ]; then
  echo "VERDICT: PASS ($RUNS/$RUNS all green, engine=$LOCK_ENGINE, §11 gate profile)"
else
  echo "VERDICT: PASS ($RUNS/$RUNS all green, engine=$LOCK_ENGINE) — NON-GATE profile (not §11 counts / scaled T_appear); diagnostic only"
fi
exit "$overall"
