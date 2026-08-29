#!/usr/bin/env bash
# M7 WP0/WP6 — Firebase Test Lab runner for the OV-4 overlay-race instrumentation test.
#
# Closes the R-002 OEM/OS residual (M7_PLAN.md §10 "FTL" lane, §11 protocol, canonical
# R-002 standard rule #3): runs the SAME durable artifact —
# app/src/androidTest/.../e2e/OverlayRaceUiTest.kt — on FTL's PHYSICAL multi-OEM / multi-API
# catalog, the coverage the single Moto G 2025 (budget, one OEM/OS) cannot give.
#
# It is black-box (drives via `am`/`appops` shell through UiAutomation, observes `dumpsys
# window`), so the same APK pair runs on the WP0 spike now and the production engine after WP2
# repoints POLL_SERVICE/OVERLAY_TITLE — no script change.
#
# Prereqs + cost + the target-package caveat: scripts/ftl/README.md. This script assembles
# nothing external and creates no account; it only builds the two APKs and submits the run to
# the project your active `gcloud` config points at.
#
# Usage:
#   scripts/ftl/run_ov4_ftl.sh                 # build APKs, run the default OEM matrix
#   FTL_PROFILE=quick scripts/ftl/run_ov4_ftl.sh   # 1 device, light counts (pipeline smoke)
#   FTL_PROFILE=sweep scripts/ftl/run_ov4_ftl.sh   # full OEM matrix, §11-scaled counts
#   SKIP_BUILD=1 scripts/ftl/run_ov4_ftl.sh    # reuse existing APKs (no Gradle assemble)
#   FTL_DEVICES="aruba:30,b0q:33" scripts/ftl/run_ov4_ftl.sh   # custom device subset (model:version)
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_ROOT"

PROFILE="${FTL_PROFILE:-sweep}"
APP_APK="app/build/outputs/apk/prod/debug/app-prod-debug.apk"
TEST_APK="app/build/outputs/apk/androidTest/prod/debug/app-prod-debug-androidTest.apk"

# --- §11 numeric protocol, scaled per profile ------------------------------------------------
# The DECISIVE R-002 proof is the emulator A/B (NucBox); FTL closes the OEM/OS residual, so the
# sweep runs a representative-but-lighter burst than the full N=50/K=20/R=5 to stay inside FTL
# per-test timeouts and physical-device quota. Raise these toward §11 once quota/cost is known.
case "$PROFILE" in
  quick) OV4_BURSTS=5;  OV4_RELAUNCHES=10; OV4_REPEAT=1; FTL_TIMEOUT=10m ;;
  sweep) OV4_BURSTS=30; OV4_RELAUNCHES=20; OV4_REPEAT=2; FTL_TIMEOUT=30m ;;
  full)  OV4_BURSTS=50; OV4_RELAUNCHES=20; OV4_REPEAT=5; FTL_TIMEOUT=45m ;;  # §11 verbatim; long
  *) echo "unknown FTL_PROFILE='$PROFILE' (quick|sweep|full)" >&2; exit 2 ;;
esac

# --- Physical device matrix ------------------------------------------------------------------
# OEM + API diversity is the point (different WindowManager / overlay handling). Model IDs and
# API availability CHANGE — verify against the live catalog before a real run:
#   gcloud firebase test android models list --filter="form=PHYSICAL"
# Edit this list to what the catalog currently offers; keep OEMs diverse (Samsung/Xiaomi/Oppo/
# Motorola/Pixel...) and spread API levels (aim for 30/33/34/35 physical; 36 via the emulator
# api36 lane per §10). `quick` uses only the first entry.
# 5 devices = Spark's physical-test/day cap (each --device = 1 test). Reconciled against the live
# FTL catalog (gcloud firebase test android models list --filter="form=PHYSICAL", 2026-08-27):
# 4 distinct WMS families (Motorola / Samsung One UI / OnePlus OxygenOS / stock Pixel) and the full
# API spread 30/33/34/35/36 — including the targetSdk-36 shipping target on REAL hardware (a35x).
# Add a 6th (needs Blaze) for more OEM spread. Re-verify codenames/versions when the catalog drifts.
DEVICES_SWEEP=(
  "model=aruba,version=30,locale=en,orientation=portrait"         # Motorola moto e20
  "model=b0q,version=33,locale=en,orientation=portrait"           # Samsung Galaxy S22 Ultra (One UI)
  "model=OP5552L1,version=34,locale=en,orientation=portrait"      # OnePlus 10T 5G (OxygenOS) -- CPH2449 (OnePlus 11) errored twice in FTL, 2026-08-28
  "model=akita,version=35,locale=en,orientation=portrait"         # Google Pixel 8a (stock)
  "model=a35x,version=36,locale=en,orientation=portrait"          # Samsung Galaxy A35 5G (One UI, API 36)
)
DEVICES_QUICK=( "model=akita,version=35,locale=en,orientation=portrait" )  # Google Pixel 8a (stock)

if [[ "$PROFILE" == "quick" ]]; then DEVICES=("${DEVICES_QUICK[@]}"); else DEVICES=("${DEVICES_SWEEP[@]}"); fi

# FTL_DEVICES override: a comma-separated model:version shorthand replaces the profile's device
# list (the profile still sets the burst counts). Handy for fitting Spark's 5-physical-tests/day
# cap — e.g. drop a device already covered by an earlier run:
#   FTL_DEVICES="aruba:30,b0q:33,CPH2449:34,a35x:36" FTL_PROFILE=sweep scripts/ftl/run_ov4_ftl.sh
if [[ -n "${FTL_DEVICES:-}" ]]; then
  DEVICES=()
  IFS=',' read -ra _pairs <<< "$FTL_DEVICES"
  for _p in "${_pairs[@]}"; do
    _p="${_p//[[:space:]]/}"; [[ -z "$_p" ]] && continue
    [[ "$_p" == *:* ]] || { echo "bad FTL_DEVICES entry '$_p' — want model:version (e.g. akita:35)" >&2; exit 2; }
    _m="${_p%%:*}"; _v="${_p##*:}"
    [[ -n "$_m" && -n "$_v" ]] || { echo "bad FTL_DEVICES entry '$_p' — want model:version (e.g. akita:35)" >&2; exit 2; }
    DEVICES+=("model=$_m,version=$_v,locale=en,orientation=portrait")
  done
  [[ ${#DEVICES[@]} -gt 0 ]] || { echo "FTL_DEVICES set but parsed to no devices" >&2; exit 2; }
  echo "   (device list overridden by FTL_DEVICES)"
fi

# --- Preflight -------------------------------------------------------------------------------
# Self-heal PATH: a fresh gcloud install doesn't reach already-open shells until restart.
command -v gcloud >/dev/null 2>&1 || . "$(dirname "${BASH_SOURCE[0]}")/gcloud-env.sh" || true
command -v gcloud >/dev/null || { echo "gcloud not on PATH — see scripts/ftl/README.md (Prereqs)"; exit 1; }
PROJECT="$(gcloud config get-value project 2>/dev/null || true)"
[[ -n "$PROJECT" && "$PROJECT" != "(unset)" ]] || { echo "no active gcloud project — 'gcloud config set project <id>'"; exit 1; }
echo "== FTL OV-4 sweep =="
echo "   project : $PROJECT"
echo "   profile : $PROFILE  (bursts=$OV4_BURSTS relaunches=$OV4_RELAUNCHES repeat=$OV4_REPEAT, timeout=$FTL_TIMEOUT)"
echo "   devices : ${#DEVICES[@]}"

# --- Build the APK pair ----------------------------------------------------------------------
if [[ "${SKIP_BUILD:-0}" != "1" ]]; then
  echo "== assembling prodDebug app + androidTest APKs =="
  ./gradlew assembleProdDebug assembleProdDebugAndroidTest \
    -PbuildTime="$(date -u +%Y-%m-%dT%H:%M:%SZ)" --stacktrace
fi
[[ -f "$APP_APK"  ]] || { echo "missing app APK:  $APP_APK";  exit 1; }
[[ -f "$TEST_APK" ]] || { echo "missing test APK: $TEST_APK"; exit 1; }

# --- Submit ----------------------------------------------------------------------------------
DEVICE_FLAGS=(); for d in "${DEVICES[@]}"; do DEVICE_FLAGS+=(--device "$d"); done
RESULTS_DIR="ov4/$(date -u +%Y%m%d-%H%M%S)-$PROFILE"

echo "== submitting to Firebase Test Lab =="
set -x
gcloud firebase test android run \
  --type instrumentation \
  --app "$APP_APK" \
  --test "$TEST_APK" \
  --test-targets "class com.applock.e2e.OverlayRaceUiTest" \
  --environment-variables "ov4_bursts=$OV4_BURSTS,ov4_relaunches=$OV4_RELAUNCHES,ov4_repeat=$OV4_REPEAT" \
  --timeout "$FTL_TIMEOUT" \
  --results-dir "$RESULTS_DIR" \
  "${DEVICE_FLAGS[@]}"
set +x

echo
echo "Done. Per-device pass/fail is above; the console link shows logcat (grep 'M7SpikeTest'"
echo "for the 'OV-4 overlay race: TOP=.. BEHIND=.. ABSENT=..' line) + video per device."
echo "Record the numbers in a dated docs/reports/campaigns/ report (M7_PLAN §11 / canonical rule #3)."
