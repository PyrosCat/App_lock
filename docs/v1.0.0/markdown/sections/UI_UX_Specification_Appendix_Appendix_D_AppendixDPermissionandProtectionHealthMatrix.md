# UI/UX Specification

> Version 1.0.0

## Appendix D — Permission and Protection-Health Matrix

Usage Access and the "Display over other apps" (system overlay) permission are the two unconditional protection capabilities: Usage Access supplies foreground detection and the overlay permission enables the lock presentation. Both must be granted before a protected state is claimed; loss of either yields Action required or Protection interrupted, never a false protected state. Notifications are conditional by Android version and selected essential alerts. Biometrics are optional. Battery/background settings are situational. Absence of every other permission named in the release boundary is expected and shall not lower protection health.
