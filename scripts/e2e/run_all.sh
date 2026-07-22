#!/usr/bin/env bash
# run_all — the WP2 regression gate. Runs the four checks N times (default 2, for
# the "2/2 consecutive" exit criterion) and prints a markdown summary block ready
# to paste into a dated report under docs/reports/campaigns/.
#
#   ./run_all.sh [-s SERIAL] [-n RUNS] [--skip-setup]
#
# Run this BEFORE and AFTER any change to the lock engine / session manager /
# self-gate (M1 WP5 Hilt, WP6 package moves). Any FAIL = stop and investigate.
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
RUNS=2; SKIP_SETUP=""; SERIAL_ARG=""
while [ $# -gt 0 ]; do case "$1" in
  -s) SERIAL_ARG="$2"; shift 2;;
  -n) RUNS="$2"; shift 2;;
  --skip-setup) SKIP_SETUP=1; shift;;
  *) echo "unknown arg: $1"; exit 2;;
esac; done
[ -n "$SERIAL_ARG" ] && export SERIAL="$SERIAL_ARG"

CHECKS=(smoke_core ov3_fast_switch ov4_rapid_relaunch f3_self_gate)
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
echo "| Check | $(for r in $(seq 1 "$RUNS"); do printf 'Run %s | ' "$r"; done)"
echo "|---|$(for r in $(seq 1 "$RUNS"); do printf -- '---|'; done)"
for chk in "${CHECKS[@]}"; do
  row="| \`$chk\` |"
  for r in $(seq 1 "$RUNS"); do row+=" ${RESULT[$chk,$r]:-?} |"; done
  echo "$row"
done
echo
[ "$overall" -eq 0 ] && echo "VERDICT: PASS ($RUNS/$RUNS all green)" || echo "VERDICT: FAIL (see above)"
exit "$overall"
