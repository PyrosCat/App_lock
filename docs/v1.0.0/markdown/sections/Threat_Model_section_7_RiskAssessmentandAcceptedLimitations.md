# Threat Model

## Version 1.0.0

## 7. Risk Assessment and Accepted Limitations

### 7.1 Risk Treatment

Critical and High risks within the supported application boundary require a preventive control, a detection and recovery control, or both. A threat shall not be described as mitigated where the application only displays a warning. Medium risks require proportionate controls and verification. Low risks may be accepted where their effect is bounded and does not undermine the core authorization promise.

Root/system compromise, force-stop, application uninstall, application-data clearing, platform Keystore failure, and Android-controlled execution limits cannot be eliminated by an ordinary App Lock application. They are accepted boundaries only when stated accurately and when the application does not create an additional bypass inside the boundary it does control.

### 7.2 Residual-Limitation Register

| Limitation | Residual consequence | Required representation |
|---|---|---|
| Foreground detection timing | Android Usage Access may not report a transition instantly; limited content may appear before lock presentation | Do not promise zero-latency interception; verify and describe measured behavior for supported phones |
| Lock-presentation timing | Android controls whether and when a background application may present its interface | Report Protection interrupted or Unknown or not verified when presentation cannot be established |
| Permission revocation | The user or Android can remove required capabilities | Detect when possible, guide recovery, and never claim healthy protection after known loss |
| Force-stop | Android can prevent all App Lock execution | State that protection cannot continue while force-stopped |
| Manufacturer power restrictions | A manufacturer may delay or terminate background work | Use Degraded, Protection interrupted, or Unknown or not verified status as supported by actual evidence; do not claim universal behavior |
| Reboot/startup ordering | App Lock may not run before another application is usable | Clear sessions and restore protection at the earliest Android-permitted opportunity; retain the exposure as High risk |
| Uninstall or application-data clearing | Local credentials and selections are removed, ending App Lock protection | Treat as a device-user action outside continuing protection |
| Destructive forgotten-PIN reset | Any person able to reach and confirm reset may erase App Lock configuration; no protected data is recovered | Explain the loss and never present reset as authentication |
| No backup | Corruption or Keystore loss may make local configuration irrecoverable | Offer full reset only; do not imply data can be restored |
| Root/system compromise | A privileged attacker may alter memory, files, UI, permissions, and Keystore behavior | Make no security guarantee in this condition |
| Hostile privileged UI service | A user-authorized overlay or Accessibility service may observe or inject UI actions | Apply best-effort screen and input defenses and disclose the limit |
| Physical observation | Another person or external camera may observe PIN entry | Mask entry and minimize exposure; do not claim complete shoulder-surfing prevention |
| Platform biometric accuracy | Match accuracy and enrollment protection are controlled by Android and device hardware | Accept only platform-approved eligibility and preserve PIN fallback |
| Phone manufacturer variation | Detection, notification, permission, and background behavior may vary | Limit claims to the declared supported range and available compatibility evidence |
| Unsupported device or profile class | Protection has not been specified or verified for the excluded environment | Do not imply support or provide partial claims |

### 7.3 Risk Conclusions

The highest residual risks are enforcement interruption caused by force-stop, permission removal, manufacturer restrictions, startup timing, or Android presentation limits. The application shall reduce these risks through conservative session handling, per-transition evaluation, health checking, accurate status, and recovery guidance. Those measures do not convert Android-controlled limitations into guaranteed prevention.

The highest confidentiality boundary is Android Keystore and the application sandbox. Loss of either boundary may expose local security information. Version 1.0.0 does not add root detection, anti-tamper services, remote revocation, or backup recovery to address conditions outside the stated phone security model.

The destructive forgotten-PIN path accepts loss of local App Lock configuration in exchange for avoiding a recovery secret, account system, backup path, or weak credential bypass. It shall never be counted as successful authentication.

---
