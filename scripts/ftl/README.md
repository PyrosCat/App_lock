# Firebase Test Lab — OV-4 OEM/OS residual sweep

Runs the durable `OverlayRaceUiTest` (the OV-4 overlay-race check) on FTL's **physical
multi-OEM / multi-API** catalog. This is the M7_PLAN.md §10 **FTL** lane / **canonical R-002
standard rule #3**: the coverage the single Moto G 2025 (budget, one OEM/OS) cannot give.

- **Decisive** R-002 proof is the emulator A/B (NucBox) — not this.
- This sweep **closes the OEM/OS overlay-handling residual**, or (rule #4) if FTL isn't
  provisioned, R-002 stays Open at a reduced rating under a TM §14.10 compensating treatment.
- Same APK pair runs against the WP0 **spike** today and the **production** engine after WP2
  repoints `POLL_SERVICE` / `OVERLAY_TITLE` in the test — no script change.

## Prerequisites (one-time, run by you — account + ToS; billing only for bigger runs)

These touch your Google Cloud account, so do them yourself; the script never creates an
account, accepts terms, or enables billing.

1. **Install the gcloud CLI** (not present on this machine): https://cloud.google.com/sdk/docs/install
   — on Windows use the installer, then a fresh shell so `gcloud` is on `PATH`.
2. **Create / pick a Google Cloud project** and note its **project id**.
3. **Billing — Spark (no-cost) is enough to start; Blaze only for bigger runs.** Physical
   devices ARE in the Spark free tier: **5 physical tests/day, up to 30 no-cost min/day** (each
   `--device` in a run = one physical test). So `FTL_PROFILE=quick` (1 device) runs free, and
   the default `sweep` (**5 devices**) sits exactly at the 5-test/day cap — free on Spark **if**
   the 5 runs together stay under the **30 no-cost min/day** for physical (watch the total: OV-4
   is minutes per device). Exceeding 30 min/day, adding a 6th device, or `full` (§11 counts,
   long) needs **Blaze (pay-as-you-go)** — same 30 free min/day, then **$5/device/hour** for
   physical. (Verify current numbers at https://firebase.google.com/pricing — they change.)
4. **Enable the APIs** (accepts the Test Lab ToS on first console visit):
   ```bash
   gcloud services enable testing.googleapis.com toolresults.googleapis.com
   ```
5. **Authenticate + select the project:**
   ```bash
   gcloud auth login
   gcloud config set project <YOUR_PROJECT_ID>
   ```
6. **Confirm current physical models/APIs** (the catalog changes — the script's list is a
   starting point, not guaranteed live):
   ```bash
   gcloud firebase test android models list --filter="form=PHYSICAL"
   ```

## Run

```bash
FTL_PROFILE=quick scripts/ftl/run_ov4_ftl.sh   # 1 device, light counts — validate the pipeline first
FTL_PROFILE=sweep scripts/ftl/run_ov4_ftl.sh   # OEM matrix, representative counts (default)
FTL_PROFILE=full  scripts/ftl/run_ov4_ftl.sh   # §11 verbatim (N=50/K=20/R=5) — long; watch the timeout
SKIP_BUILD=1 FTL_PROFILE=sweep scripts/ftl/run_ov4_ftl.sh   # reuse already-built APKs
FTL_DEVICES="aruba:30,b0q:33,CPH2449:34,a35x:36" FTL_PROFILE=sweep scripts/ftl/run_ov4_ftl.sh  # custom subset
```

`FTL_DEVICES` (comma-separated `model:version`) overrides the profile's device list — the profile
still sets the burst counts. Use it to fit Spark's **5 physical tests/day** cap: e.g. after a
`quick` run has already covered one device, drop it and run the remaining four so the day's total
stays at 5.

Runs under Git Bash on Windows (matches `scripts/e2e/`). Build variant is **prodDebug** (the
spike lives in the main source set, so prod includes it).

> **PATH note.** A fresh gcloud install doesn't reach already-open shells until they restart.
> `run_ov4_ftl.sh` self-heals by sourcing `scripts/ftl/gcloud-env.sh` when `gcloud` isn't found,
> so you usually don't need a new terminal. To fix PATH for your *own* shell (e.g. to run
> `gcloud` directly), source it once: `. scripts/ftl/gcloud-env.sh`. Read the per-device result table in
the console; each device's logcat carries the `M7SpikeTest` line
`OV-4 overlay race: TOP=.. BEHIND=.. ABSENT=..`. Record the numbers in a dated
`docs/reports/campaigns/` report per §11 and the canonical R-002 standard (never full-Close
R-002 on single-OEM evidence).

## Target app (resolved per device) + one thing to sanity-check

`OverlayRaceUiTest` races the overlay against a target app it **resolves per device** — the
first installed candidate with a launcher activity from `TARGET_APP_CANDIDATES` (Google Maps →
Google clock → AOSP clock → calculators). Each is a *normal* app, deliberately not Settings
(Android force-hides `TYPE_APPLICATION_OVERLAY` over Settings / permission screens via
`HIDE_NON_SYSTEM_OVERLAY`). The list spans GMS + AOSP so the same artifact runs on OEM devices
(Maps / OEM clock) **and** plain AOSP images (AOSP clock) without skipping — that is what gives
the api × oem coverage this sweep exists for. No per-device target-app config is needed.

Sanity-check when reading results: the test `assumeTrue`-skips only if *no* candidate resolves,
and a skip still shows non-red in FTL. Confirm each device actually **ran** — the logcat
`M7SpikeTest` lines (`OV-4 target app: <pkg> …` then `OV-4 overlay race: TOP=.. BEHIND=.. ABSENT=..`)
are emitted only on a real run. A skip is **not** evidence.

## Scheduling note (plan alignment)

The **authoritative** FTL sweep is a **WP6** close-out item, after WP2 repoints the test to the
production overlay. Running it now (against the spike) validates the FTL pipeline + can give an
early OEM signal — useful, but not the gate evidence. Adopting FTL as a standing CI gate is the
D6 decision flagged for the lead.
