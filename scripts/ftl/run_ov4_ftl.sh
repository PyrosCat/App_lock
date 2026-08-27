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
DEVICES_SWEEP=(
  "model=a51,version=30,locale=en,orientation=portrait"          # Samsung Galaxy A51 (One UI)
  "model=redfin,version=30,locale=en,orientation=portrait"        # Pixel 5 (stock)
  "model=oriole,version=33,locale=en,orientation=portrait"        # Pixel 6
  "model=b0q,version=33,locale=en,orientation=portrait"           # Samsung Galaxy S22 Ultra
  "model=shiba,version=34,locale=en,orientation=portrait"         # Pixel 8
  "model=akita,version=35,locale=en,orientation=portrait"         # Pixel 8a
)
DEVICES_QUICK=( "model=redfin,version=30,locale=en,orientation=portrait" )

if [[ "$PROFILE" == "quick" ]]; then DEVICES=("${DEVICES_QUICK[@]}"); else DEVICES=("${DEVICES_SWEEP[@]}"); fi

# --- Preflight -------------------------------------------------------------------------------
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
