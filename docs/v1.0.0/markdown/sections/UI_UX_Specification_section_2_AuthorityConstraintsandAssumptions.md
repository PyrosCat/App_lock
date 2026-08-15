# UI/UX Specification

> Version 1.0.0

## 2. Authority, Constraints, and Assumptions

#### 2.1 Relationship to Companion Specifications

<!-- table-widths: 1.7, 4.8 -->
| Specification | Relationship |
| --- | --- |
| Software Requirements | Defines required capability and business behavior. This document defines its user-visible realization. |
| Non-Functional Requirements | Defines measurable quality, security, privacy, accessibility, performance, and compatibility outcomes. This document defines their presentation and interaction consequences. |
| Software Design | Defines responsibilities and dependency direction. This document relies only on constraints visible to a user. |
| Database Design | Defines protected local information and lifecycle behavior. This document defines when that information is shown, changed, or removed. |
| Threat Model | Defines security boundaries, foreseeable misuse, and accepted platform limitations. This document turns those boundaries into safe interaction and truthful language. |

If two statements appear inconsistent, the interpretation that prevents unauthorized access, avoids a false protection claim, and stays inside the Version 1.0.0 boundary shall be used until the documents are corrected.

#### 2.2 Supported Devices and Versions

The supported form factor is a conventional Android phone running Android 11, 12, 13, 14, or 15, corresponding to API levels 30 through 35. Portrait is the primary layout. Landscape shall remain fully usable and shall not expose protected content, hide the authentication exit, or make a required recovery action unreachable.

The design shall target compact phone windows. It does not require navigation rails, persistent drawers, two-pane layouts, tabletop postures, external displays, keyboard-first desktop operation, or large-screen optimization. Split-screen and picture-in-picture need safe behavior, not a separately optimized design.

#### 2.3 Android Constraints

- Foreground detection requires Android Usage Access. The setup shall name it as required, explain its purpose, and verify its actual state after every settings handoff.
- App Lock shall not request or depend on its own Accessibility service in Version 1.0.0.
- Lock presentation may require an Android-managed capability whose label and route vary by device. The experience shall provide a verified device-appropriate handoff and shall not claim success merely because settings were opened.
- Android may restrict background execution, delay foreground information, revoke access, stop the application, clear its data, or remove it. The interface shall report the state it can verify and shall not promise continuous protection when Android prevents operation.
- Biometric enrollment and the biometric prompt are controlled by Android. App Lock shall not draw a look-alike prompt or claim a biometric capability that Android reports as ineligible.
- Android settings, permission wording, notification behavior, and vendor battery controls can vary. Guidance shall prefer the Android-visible setting name and a text path rather than screenshots that may become inaccurate.

#### 2.4 Assumptions

<!-- table-widths: 2.35, 4.15 -->
| Assumption | Required treatment when false |
| --- | --- |
| The phone is within the declared Android range. | Explain that protection has not been verified for the device and do not show a healthy status. |
| A supported lock-presentation capability is available. | Show Protection interrupted or Action required and provide the narrowest verified recovery route. |
| The person can grant required Android access. | Preserve setup progress, identify the consequence, and allow safe exit without claiming protection. |
| At least one eligible application is installed. | Show a distinct empty state and keep setup incomplete. |
| A PIN remains available before biometrics are enabled. | Never offer biometric-only configuration or recovery. |
| Sensitive processing remains local. | Do not introduce consent for transfer, accounts, or network status into Version 1.0.0 screens. |

#### 2.5 Open Product Choices

The final expressive palette, illustration style, and exact motion curves may be refined during visual design. Those choices may not change semantic color roles, contrast, task hierarchy, state wording, security behavior, or the release boundary. The shipped locale set may be chosen separately; English is the reference copy, and the layout shall remain localizable.
