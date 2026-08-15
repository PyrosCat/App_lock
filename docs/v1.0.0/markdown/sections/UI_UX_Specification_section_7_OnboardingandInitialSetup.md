# UI/UX Specification

> Version 1.0.0

## 7. Onboarding and Initial Setup

#### 7.1 Required Sequence

1. Welcome and scope.
2. Create and confirm the PIN.
3. Explain local privacy behavior.
4. Explain and obtain required protection access.
5. Select at least one eligible application.
6. Run a protection check.
7. Present the verified outcome and enter the application.

Optional biometrics may be offered after the PIN exists. Declining biometrics shall not make setup incomplete.

#### 7.2 Credential Creation

The create-PIN screen shall show the approved length and composition rule before input. Continue remains disabled until the rule is satisfied. Confirmation uses a separate empty entry. A mismatch clears both entries, explains that they did not match, and restarts creation. PIN digits shall not survive backgrounding, rotation, process recreation, or an error.

The PIN is considered created only after secure local storage succeeds. A save failure shall say that no PIN was saved and shall offer retry or safe cancellation.

#### 7.3 Privacy Explanation

The disclosure shall explain that App Lock uses local credential information, installed-application identity, Usage Access, the "Display over other apps" permission, protection settings, and limited local diagnostics. It shall state that Version 1.0.0 has no account, cloud synchronization, third-party notification access, location collection, camera capture, or Vault. The essential disclosure shall remain available offline.

#### 7.4 Protection Access

Usage Access and the "Display over other apps" (system overlay) permission — used to present the lock — are both required. Notifications are requested only when required by the supported Android version or when essential protection alerts are enabled. Each row shall show Not requested, Checking, Available, Denied, Revoked, Restricted, or Unsupported as applicable.

The screen shall explain one capability at a time before opening Android settings. On return, it shall recheck the actual state, return focus to the originating row, and announce the result once. It shall not reopen settings automatically after denial.

#### 7.5 Application Selection

The list shall show eligible installed applications by localized name and icon, support basic name search, and expose a separate protection selection control. Unsupported or unsafe targets shall be disabled with a concise reason. At least one selected application is required to complete setup.

There is no category system, filter sheet, bulk policy editor, per-app credential, new-app recommendation, or work-profile handling in Version 1.0.0.

#### 7.6 Verification and Completion

The protection check shall evaluate current required access, protection operation, selected applications, and a supported lock-presentation check. It shall clear any stale protected claim while checking. Completion shall use the actual resulting state rather than a universal success message.

Only a fresh Protected result enables “Finish setup.” Other results present “Fix protection” or “Check again.” The completion screen shall not claim that force-stop, uninstall, data clear, root compromise, or every manufacturer restriction is prevented.

#### 7.7 Interrupted Setup

App Lock may preserve completed non-secret steps and a draft app selection. On relaunch, it shall route to the earliest incomplete step. Once a PIN exists, resuming setup requires the App Lock gate. Android handoffs and process recreation shall not restore PIN digits or an assumed access result.
