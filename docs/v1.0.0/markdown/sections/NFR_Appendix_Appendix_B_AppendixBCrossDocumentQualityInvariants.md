# Non-Functional Requirements

## Version 1.0.0

## Appendix B - Cross-Document Quality Invariants

The complete version 1.0.0 document set shall preserve these invariants:

1. PIN is mandatory; eligible biometrics are optional and always fall back to PIN.
2. Usage Access is the single application-detection baseline; an App Lock Accessibility service is absent.
3. Each protected application has its own volatile authorization session, governed by one global relock policy.
4. A failed required capability cannot be shown as protected.
5. Forgotten PIN provides destructive reset only and preserves no local configuration.
6. App Lock emits only its own essential masked notifications and does not access protected-application notifications.
7. All retained user and security data remains local, and no routine application network traffic is produced.
8. Diagnostics are current, local, privacy safe, bounded, and not exportable.
9. Compatibility is limited to conventional phones on API levels 30 through 35 and the declared evidence set.
10. A single polished accessible phone visual system that follows system light and dark appearance is required; a theme selector, custom themes, and large-screen layouts are not.
