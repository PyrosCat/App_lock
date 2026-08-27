# Firebase Test Lab — OV-4 OEM/OS residual sweep

Runs the durable `OverlayRaceUiTest` (the OV-4 overlay-race check) on FTL's **physical
multi-OEM / multi-API** catalog. This is the M7_PLAN.md §10 **FTL** lane / **canonical R-002
standard rule #3**: the coverage the single Moto G 2025 (budget, one OEM/OS) cannot give.

- **Decisive** R-002 proof is the emulator A/B (NucBox) — not this.
- This sweep **closes the OEM/OS overlay-handling residual**, or (rule #4) if FTL isn't
  provisioned, R-002 stays Open at a reduced rating under a TM §14.10 compensating treatment.
- Same APK pair runs against the WP0 **spike** today and the **production** engine after WP2
  repoints `POLL_SERVICE` / `OVERLAY_TITLE` in the test — no script change.

## Prerequisites (one-time, run by you — account + billing + ToS)

These touch your Google Cloud account and billing, so do them yourself; the script never
creates an account, accepts terms, or enables billing.

1. **Install the gcloud CLI** (not present on this machine): https://cloud.google.com/sdk/docs/install
   — on Windows use the installer, then a fresh shell so `gcloud` is on `PATH`.
2. **Create / pick a Google Cloud project** and note its **project id**.
3. **Enable billing (Blaze / pay-as-you-go).** FTL physical devices need it — the Spark free
   tier only allows a small number of physical-device runs/day. Physical-device minutes are
   billed; budget accordingly (a `sweep` over ~6 devices is minutes each).
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
```

Runs under Git Bash on Windows (matches `scripts/e2e/`). Build variant is **prodDebug** (the
spike lives in the main source set, so prod includes it). Read the per-device result table in
the console; each device's logcat carries the `M7SpikeTest` line
`OV-4 overlay race: TOP=.. BEHIND=.. ABSENT=..`. Record the numbers in a dated
`docs/reports/campaigns/` report per §11 and the canonical R-002 standard (never full-Close
R-002 on single-OEM evidence).

## Victim app (resolved per device) + one thing to sanity-check

`OverlayRaceUiTest` races the overlay against a "victim" app it **resolves per device** — the
first installed candidate with a launcher activity from `VICTIM_CANDIDATES` (Google Maps →
Google clock → AOSP clock → calculators). Each is a *normal* app, deliberately not Settings
(Android force-hides `TYPE_APPLICATION_OVERLAY` over Settings / permission screens via
`HIDE_NON_SYSTEM_OVERLAY`). The list spans GMS + AOSP so the same artifact runs on OEM devices
(Maps / OEM clock) **and** plain AOSP images (AOSP clock) without skipping — that is what gives
the api × oem coverage this sweep exists for. No per-device victim config is needed.

Sanity-check when reading results: the test `assumeTrue`-skips only if *no* candidate resolves,
and a skip still shows non-red in FTL. Confirm each device actually **ran** — the logcat
`M7SpikeTest` lines (`OV-4 victim: <pkg> …` then `OV-4 overlay race: TOP=.. BEHIND=.. ABSENT=..`)
are emitted only on a real run. A skip is **not** evidence.

## Scheduling note (plan alignment)

The **authoritative** FTL sweep is a **WP6** close-out item, after WP2 repoints the test to the
production overlay. Running it now (against the spike) validates the FTL pipeline + can give an
early OEM signal — useful, but not the gate evidence. Adopting FTL as a standing CI gate is the
D6 decision flagged for the lead.
