# UI/UX Specification

> Version 1.0.0

## 1. Introduction and Scope

#### 1.1 Purpose

This specification defines how a person sets up App Lock, protects selected applications, unlocks them, understands protection health, restores required Android access, changes core settings, and safely resets local configuration. It also defines the visual, accessibility, privacy, phone-layout, error, and recovery standards applied to those experiences.

#### 1.2 Intended Readers

- People approving the finished product experience and wording.
- Interaction, visual, content, accessibility, and localization designers.
- People implementing and evaluating the specified screens and behavior.
- Security and privacy reviewers assessing user-visible controls and claims.

#### 1.3 Included Experience

Version 1.0.0 includes:

- initial setup with a numeric PIN;
- optional eligible Android biometrics with PIN fallback;
- required Android Usage Access and the supported lock-presentation capability;
- selection and basic search of applications to protect;
- protected-application lock presentation, cancellation, retry delay, lockout, session reuse, and relock;
- a protection summary, detailed health view, and guided recovery from required-access loss;
- essential, privacy-masked App Lock notifications;
- basic authentication, privacy, notification, access, diagnostic, help, and reset settings;
- polished light and dark presentation that follows the system theme; and
- portrait-first phone layouts with secure, usable landscape behavior.

#### 1.4 Release Boundary

The following are not part of Version 1.0.0 and shall not appear as controls, setup steps, promises, empty states, permissions, or hidden dependencies:

- Vault storage, file import, preview, export, backup, restore, or device migration;
- recovery passwords or preservation of local configuration after a forgotten PIN;
- profiles, schedules, location or network triggers, automation, recommendations, or manual overrides;
- intruder photographs, event media, security-event history, or advanced reports;
- access to, masking of, or history for notifications produced by other applications;
- bulk protection changes, per-application credentials, or per-application relock policies;
- newly installed application recommendations or review prompts;
- concealment, disguises, fake screens, decoy credentials, or secret launch gestures;
- device administration, uninstall resistance, work profiles, cloned applications, or secondary users;
- an App Lock Accessibility service;
- accounts, cloud services, remote commands, diagnostic export, or routine network communication;
- tablets, foldables, Chromebooks, desktop modes, televisions, vehicles, or wearables; and
- Android versions earlier than Android 11 or later than Android 15 unless separately validated.

#### 1.5 Document Boundary

This document specifies journeys, navigation, screens, transient surfaces, wording, states, visual rules, accessibility, privacy presentation, phone responsiveness, and observable acceptance outcomes. It does not define internal modules, methods, variables, database tables, cryptographic algorithms, development sequencing, or status reporting.

#### 1.6 Completion Principle

A visible capability is complete only when its applicable normal, loading, empty, disabled, authentication, Android-access, degraded, error, interruption, cancellation, and restoration states meet this specification. A successful path alone is insufficient.
