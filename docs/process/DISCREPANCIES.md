# Discrepancies — noticed doc/SSOT drift awaiting reconciliation

**Class:** living (GOVERNANCE §5.1). Append-only. When an instance notices a value that has drifted
from its authoritative source and may not fix it in place (GOVERNANCE §2.7), it adds a row here and
moves on; the user reconciles. Newest on top.

| Date | File / section | Mismatch | Authoritative source | Status |
|---|---|---|---|---|
| 2026-08-24 | ADR-014 "Verification fleet" + 2026-08-06 note | lists `full` = 26/29/33/35 | `app/build.gradle.kts` `full` = 26/29/30/33/35 (API 30 added in WP8) | Reconciled 2026-08-24 by the ADR-014 §2.7 note; build is authoritative |
