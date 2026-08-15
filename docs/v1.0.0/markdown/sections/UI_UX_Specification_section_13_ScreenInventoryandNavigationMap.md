# UI/UX Specification

> Version 1.0.0

## 13. Screen Inventory and Navigation Map

#### 13.1 Controlled Screens

<!-- table-widths: 1.0, 2.45, 1.4, 1.65 -->
| ID | Screen | Sensitivity | Primary parent |
| --- | --- | --- | --- |
| SCR-001 | Welcome and Scope | Public | Launch before setup. |
| SCR-002 | Create PIN | Secret entry | Setup. |
| SCR-003 | Privacy Explanation | Public | Setup. |
| SCR-004 | Protection Access Setup | Configuration | Setup. |
| SCR-005 | Select Protected Apps | Configuration | Setup or Apps. |
| SCR-006 | Protection Verification | Configuration | Setup or Protection. |
| SCR-007 | Setup Outcome | Configuration | Setup. |
| SCR-008 | Setup Status and Resume | Configuration | Authenticated launch. |
| SCR-010 | Application Gate | Secret entry | Any protected App Lock destination. |
| SCR-011 | Protected-App Lock | Secret entry | Protected target. |
| SCR-012 | Biometric and Fallback | Secret entry | Authentication host. |
| SCR-013 | Lockout | Secret/security | Authentication host. |
| SCR-014 | Step-Up Authentication | Secret/security | Protection-reducing action. |
| SCR-015 | Forgotten PIN and Reset Information | Public/security | Gate help. |
| SCR-020 | Protection Dashboard | Configuration | Top level. |
| SCR-021 | Protection Health | Configuration | Protection. |
| SCR-022 | Protected Apps | Configuration | Top level. |
| SCR-023 | App Details | Configuration | Apps. |
| SCR-050 | Settings | Configuration | Top level. |
| SCR-051 | Authentication Settings | Security | Settings. |
| SCR-052 | Privacy Settings | Configuration | Settings. |
| SCR-053 | Notification Settings | Configuration | Settings. |
| SCR-054 | Protection Access | Configuration | Settings or Health. |
| SCR-055 | Diagnostics and Protection Check | Security | Settings or Health. |
| SCR-056 | About and Support | Public/configuration | Settings. |

#### 13.2 Transient and External Surfaces

<!-- table-widths: 1.2, 2.2, 3.1 -->
| Range | Surface | Included purpose |
| --- | --- | --- |
| DLG-001, DLG-006–009 | Dialogs | Remove app protection, complete reset, end sessions, reset preferences, remove all protection. |
| SHT-002 | Bottom sheet | Choose one global relock behavior. |
| OVL-001–003 | Secure overlays | Protected-target cover, session-expiry cover, protection-check cover. |
| MSG-001–005 | In-app messages | Setting changed, change failed, access unavailable, partial diagnostic result, no search results. |
| NTF-001–003 | Android notifications | Protection active where required, protection interrupted, action required. |
| SYS-001, SYS-002, SYS-004, SYS-005, SYS-007, SYS-009 | Android handoffs | Usage Access, lock presentation, notifications, biometric enrollment, battery/application restrictions, biometric prompt. |

#### 13.3 Navigation Map

Before configuration, launch enters SCR-001 or SCR-008. After a PIN exists, protected App Lock destinations pass through SCR-010. Successful setup enters SCR-020. Bottom navigation connects SCR-020, SCR-022, and SCR-050. Protection links to SCR-021 and SCR-055; Apps links to SCR-023 and SCR-005; Settings links to SCR-051–056. Protected target detection enters SCR-011 independently of the management navigation.
