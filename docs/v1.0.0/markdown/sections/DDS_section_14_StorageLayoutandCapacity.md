# Database Design Specification

## Version 1.0.0

## 14. Storage Layout and Capacity

### 14.1 Layout

The version 1.0.0 local layout consists of:

- one ordinary settings area;
- one or more logically separated protected preference records;
- one encrypted protected-app database and framework-required companion files; and
- no required persistent cache or user-content directory.

### 14.2 Expected growth

Ordinary and protected preferences have bounded size. The relational database grows only with the number of selected applications and schema overhead. No retained data type grows continuously with elapsed time or application use.

### 14.3 Storage exhaustion

If storage exhaustion prevents a protection-reducing write, the previous committed protected state remains active. If it prevents adding protection, the new application is not reported as protected. If it prevents credential setup or replacement, the prior complete credential state remains authoritative.

The user receives a concise instruction to free device storage and retry. The application does not delete protected configuration, credential state, or encryption material to reclaim space.

### 14.4 Temporary storage

The application avoids persistent temporary data. When a supported migration requires temporary state, its maximum lifetime is the migration attempt plus controlled recovery. It is private, excluded from backup, and contains no raw PIN.

### 14.5 Cache and cleanup

There is no scheduled cache cleanup, diagnostic pruning, event retention, metric rollup, or storage forecasting. Normal application-data clearing and uninstall are the complete removal mechanisms.
