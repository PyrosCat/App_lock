# M7 F2 hardening: time estimate for phases P0 to P5

**Date:** 2026-10-01

**Status:** Estimate for planning. It sets no dates and changes no decision.

**Basis:** The commit dates of the F2 hardening work, and the device timestamps of the Moto G smoke run of
2026-10-01 (all segments except `reboot` and `biometric`, each case once).

Each value has one of two labels:

- **Measured:** taken from the commit dates or from the device timestamps of the smoke logs.
- **Estimated:** scaled from the measured values. Development estimates use the pace of P1 and P2, including the
  review rounds.

## 1. Summary

| Phase | Status | Time |
|---|---|---|
| P0: freeze questions and policy | Done | about 1 day (measured) |
| P1: validate the harness | Done | about 5.5 days (measured) |
| P2: characterize the baseline | In progress | about 3.5 days so far (measured), about 2 to 2.5 weeks remain (estimated) |
| P3: compare candidates | Not started | about 5 to 7 weeks (estimated) |
| P4: adversarial and fleet confirmation | Not started | about 2 to 3 weeks (estimated) |
| P5: recommend and disposition | Not started | about 1 week, then 1 to 2 weeks to the exit gate (estimated) |

The remaining work takes about 11 to 15.5 weeks if the working weeks follow each other. The P5 exit then falls
between mid-December 2026 and mid-January 2027.

## 2. Completed work (measured)

| Phase | Dates | Elapsed | Work |
|---|---|---|---|
| P0 | 2026-09-22 to 2026-09-23 (`f46d04c`) | about 1 day | Options proposal, test plan, P0 decisions |
| P1 | 2026-09-23 to 2026-09-28 (`bfbd8f7`) | about 5.5 days | JVM harness and Android fault harness (2026-09-23). Moto G lane with a report, a preconditions fix, a rerun, and a settings fix (2026-09-26 and 2026-09-27). NucBox plan and report (2026-09-27 and 2026-09-28). M7/M8 dependency and crypto work ran in the same period |
| P2 to date | 2026-09-28 to 2026-10-01 | about 3.5 days | JVM lane (2026-09-28). Device plan (2026-09-29). Device harness with four or more review rounds (`f756287`, 2026-10-01). Moto G smoke run (2026-10-01) |

## 3. P2: remaining work (about 2 to 2.5 weeks)

### 3.1 Moto G device time

The smoke run took 2.4 h of device time. The full recorded run is scaled from the mean time of each case type in
the smoke logs.

| Segment | Smoke run (measured) | Full recorded run (estimated) | Operator |
|---|---|---|---|
| `healthy` | 26 min | about 1 h 20 min | unlock at the start |
| `cold-read` | 29 min | about 2 h | unlock at the start |
| `degraded` | 20 min | about 2 h | unlock at the start |
| `death` | 36 min | about 3 h 20 min (the R3.1c sweep takes about 75 min of it) | unlock at the start |
| `reset` | 19 min | about 1 h 50 min | unlock at the start |
| `cross` | 11 min | about 30 min | unlock at the start |
| `reboot` | not run | about 1 h (11 reboots of about 5 min) | lead present |
| `biometric` | not run | about 30 min (12 cases) | lead present |
| **Total** | **2.4 h** | **about 12.5 h (11 h unattended, 1.5 h attended)** | |

Assumptions and limits of the scaling:

- A repeat at full depth takes the mean time of its case type in the smoke run.
- The cases with 21 processes (R1.2e, R2.4, R4.4) ran one process in the smoke run. The estimate uses 35 to 40 s
  for each restart cycle. Together they add about 2.5 h. This is the least certain part of the estimate.
- The R3.1c sweep stops after 60 trials when at least 10 trials end inside the platform write. If fewer trials end
  there, the sweep runs up to 150 trials, which adds up to about 4 h.
- Each segment needs an unlocked phone at its start. A segment cannot start right after the previous one: the
  cleanup turns the stay-awake setting off again, and the phone locks before the next preflight. The recorded run
  therefore needs 8 unlocks.

### 3.2 Steps

| Step | Estimate |
|---|---|
| Harness-fix commit. A rerun of the `cold-read` smoke segment (30 min of device time). The `reboot` and `biometric` smoke segments with the lead present (45 min). The reboot wait has not run on a device yet, so one more fix round is likely | 1 to 1.5 days |
| Moto G recorded run of all segments at one clean revision, with reruns of failed repeats (20 % added) | 2 to 3 days |
| Moto G report and review | 1 day |
| NucBox operator plan: an API 30 AVD, a check of the R3.1a kill window, a tap gap of 1.3 s | 1 day |
| NucBox smoke and recorded runs at API 36 and API 30, about 13 to 15 h of device time for each API level (the 1.3 s tap gap on API 30 adds 15 to 20 %). No segment needs an operator | 3 to 4 days, depending on NucBox availability |
| NucBox report | 1 day |
| P2 exit: a clean JVM run, the list of proposed skips for the lead, the exit record | 0.5 day |

Before P3, the lead decides the storage policy (fail-open or fail-closed), the numeric limit of extra guesses for
each restart, and the Option A policies. These decisions can overlap the NucBox runs.

## 4. P3: candidate comparison (about 5 to 7 weeks)

| Work | Estimate |
|---|---|
| Transition tables and specifications for B1, B2, B3, C1, and the design of Option A | 2 to 3 days |
| Experimental implementations with JVM tests and review: B1 about 2 days, B2 about 3 days (its freshness rules are the most complex), B3 about 2 days, C1 about 2 days, the JVM model of Option A about 3 days, combinations about 2 days | about 3 weeks |
| A latency harness that measures the time from a submission to a visible unlock (no such harness exists) | 2 to 3 days |
| Latency arms: 30 warm-up and 200 measured samples of about 10 s each, for each caller, outcome, and configuration. This is about 2.6 h for each configuration on each host. Seven configurations on two or three hosts need 36 to 54 h of device time | 1 to 1.5 weeks |
| Device fault cases for each candidate, limited to the residuals that the candidate changes, with healthy controls (about 3.5 h for each candidate on each host) | runs in parallel with the latency arms |
| Reports for each host, and the comparison | 2 to 3 days |

## 5. P4: adversarial and fleet confirmation (about 2 to 3 weeks)

| Work | Estimate |
|---|---|
| The full critical crash-cut matrix for the selected candidate: about 12.5 h on the Moto G, about 14 h for each NucBox API level | 3 to 4 days |
| The NucBox lane at API 26, 29, 30, 33, 35, and 36: H01 to H05, one representative case for each residual, a reboot deadline case, 3 repeats. About 2 to 3 h for each API level. API 26 needs a manual AVD | 3 to 4 days |
| Persistent stalls, restart attacks, clock, sleep, and reboot cases, and load and low-free-space stress on the Moto G (a small harness addition) | 3 to 4 days |
| Reports for each host | 2 days |

## 6. P5: recommendation and disposition (about 1 week, then 1 to 2 weeks to the exit gate)

| Work | Estimate |
|---|---|
| The recommendation record, in the template of the test plan | 2 to 3 days |
| A risk-register disposition for each residual, and the lead review | 1 to 2 days |
| F2 hardening exit: the selected mechanism as production code, FR-174 re-verification, the local gate, and a fleet-gate report | 1 to 2 weeks |

## 7. Main drivers

- In P2, device time is not the limit: the device lanes need about 40 h, and most of it runs unattended. The time
  goes to fix rounds, reports, and lead availability for unlocks and attended segments.
- P3 takes the most calendar time: the candidate implementations and the latency arms.

## 8. Time savers that add no test cases

| Option | Saving |
|---|---|
| Narrow the P3 candidates at the P2 exit, from the P2 results | about 1 week for each candidate removed |
| Run the latency arms for the baseline and the finalists only, on the Moto G and one NucBox lane | about 1 week of device time |
| In P3, run only the residual cases that each candidate changes, not the full matrix | several days of device time for each candidate |
| Record the screen-off steps of `healthy` and `death` (which need `-o`) as a gap | about 4.5 h of lead attendance |
