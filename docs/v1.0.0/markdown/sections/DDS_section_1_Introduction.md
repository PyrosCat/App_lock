# Database Design Specification

## Version 1.0.0

## 1. Introduction

### 1.1 Purpose

This document defines what version 1.0.0 stores, where each kind of information is authoritative, how sensitive information is protected, how persisted state changes, how supported earlier schemas are migrated, and how storage failure is represented to the user.

The design supports a small local-only product. Persistent information is limited to ordinary settings, protected authentication and encryption state, and the set of package identifiers selected for App Lock protection.

### 1.2 Scope

The database design covers:

- private application preferences;
- Keystore-backed protected preferences;
- one encrypted embedded relational database;
- memory-only session and runtime state;
- data classification and ownership;
- protected-package constraints and transactions;
- database opening and version validation;
- migration from explicitly supported earlier schemas;
- lifecycle, deletion, reset, and Keystore-loss behavior;
- Android backup exclusion;
- corruption and storage-failure behavior; and
- storage verification for Android phones on API levels 30–35.

### 1.3 Explicit exclusions

Version 1.0.0 does not store or support:

- vault items, files, attachments, categories, tags, indexes, or vault keys;
- backup packages, restore staging, manifests, recovery passwords, or new-device transfer;
- profiles, application groups, schedules, automation rules, conditions, actions, or histories;
- intruder photographs, camera metadata, or event media;
- notification queues, notification histories, delivery receipts, or archives;
- security-event history, authentication-attempt history, diagnostic records, crash packages, or support exports;
- telemetry, performance metrics, usage history, trend data, or analytics;
- remote accounts, cloud state, server tokens, synchronization state, or enterprise policy;
- installed-application labels, icons, versions, categories, or a persistent search catalog;
- biometric templates, biometric identifiers, or detailed enrollment information;
- active unlock sessions, current foreground identity, active lock requests, or persisted health history;
- device-location, network, Bluetooth, contacts, or general-storage information; or
- any information obtained from an application Accessibility service, because version 1.0.0 does not provide one.

Inactive schema objects left from earlier work do not become supported data merely because they remain physically present. Their removal is not required solely for version 1.0.0 if they are unreachable, receive no writes, create no externally visible behavior, and do not weaken confidentiality or migration safety.

### 1.4 Objectives

The storage design shall:

- preserve the confidentiality of credentials, encryption material, and protected package choices;
- retain only information necessary for the included behavior;
- make each data element authoritative in one location;
- preserve PIN lockout and protected selections across process death and reboot;
- keep unlock sessions and current usage observations out of persistent storage;
- use atomic changes for security-relevant state;
- prevent a database failure from appearing as an empty unprotected configuration;
- support deterministic and testable migration;
- exclude application-controlled data from Android backup; and
- recover honestly when local protected material cannot be opened.

### 1.5 Intended use

This specification is the authoritative description of the supported persistent data contract. It does not prescribe source-code names or require a separate software object for every logical record. Physical implementation may reuse the existing local storage facilities where they satisfy the required boundaries.

The Software Design Specification defines how application responsibilities use these stores. The security specification defines credential strength and cryptographic requirements. The user-experience specification defines how storage and recovery states are explained.
