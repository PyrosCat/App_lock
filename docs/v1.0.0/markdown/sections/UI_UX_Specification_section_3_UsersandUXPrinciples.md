# UI/UX Specification

> Version 1.0.0

## 3. Users and UX Principles

#### 3.1 Target Users

The experience is intended for people who want a second local privacy boundary around selected applications on a personal phone. They may be setting up security software for the first time, may not recognize Android access labels, and may use assistive technology or increased text and display size.

The design shall not assume knowledge of foreground detection, background execution, system privileges, sessions, or biometric eligibility. It shall explain the immediate purpose and consequence in ordinary language.

#### 3.2 Usage Contexts

The product will often be used quickly, one-handed, in public, under distraction, or immediately after an unexpected lock appears. Setup and recovery may move between App Lock and Android settings. Authentication may occur repeatedly, while configuration is less frequent and more deliberate.

#### 3.3 Privacy Expectations

- PIN digits, PIN length rules beyond what is needed for entry, biometric results, and protected content shall not be exposed in notifications, logs, accessibility announcements, or recents previews.
- Protected application names shall be omitted from the lock surface and notifications by default when naming the target would reveal sensitive intent.
- Installed-application lists and protection choices are configuration-sensitive and shall not appear before App Lock authentication after setup.
- App Lock shall clearly distinguish local processing from Android-managed access. It shall not imply that Usage Access reads content inside another application.
- No screen shall suggest that App Lock prevents uninstall, force-stop, data clearing, root compromise, or every Android timing gap.

#### 3.4 Accessibility Needs

Primary journeys shall work with TalkBack, switch access, large text, increased display size, reduced motion, high-contrast needs, and one-handed touch. Meaning shall not depend on color, position, sound, or animation alone. Authentication shall reveal entered digit count without revealing digit values.

#### 3.5 Design Principles

1. Lead with the current truth. Show the actual protection state and consequence before promotional or explanatory content.
2. Keep one obvious next action. A screen may contain alternatives, but the safest recommended action shall be visually dominant.
3. Cover first, decide second. Protected content shall be visually covered before authentication or transition begins.
4. Explain Android handoffs before leaving. State why access is needed, what Android may call it, and what denial means.
5. Preserve progress, not secrets. Restore safe setup or filter context; clear PIN input and stale authorization.
6. Make destructive outcomes concrete. Name what will be removed, what remains, and whether the action can be undone.
7. Use calm, direct language. Warnings shall be clear without blame, fear, or exaggerated security claims.
8. Make security visually polished. Consistent hierarchy, alignment, spacing, typography, and component states are part of trustworthiness, not decoration.

#### 3.6 Experience Goals

<!-- table-widths: 2.1, 4.4 -->
| Goal | Observable measure |
| --- | --- |
| Setup clarity | A first-time user can identify the remaining setup step and whether protection is active without interpreting an icon alone. |
| Unlock clarity | PIN, biometric fallback, retry delay, cancellation, and lockout are distinguishable and never expose the protected target after cancellation. |
| Health clarity | Protected, Degraded, Protection interrupted, Action required, and Unknown or not verified use different wording, consequence, and next action. |
| Recovery clarity | Returning from Android settings rechecks the relevant access and explains the actual result without automatic repeat prompting. |
| Visual quality | Representative screens show deliberate hierarchy, balanced spacing, coherent light/dark treatment, and consistent component states at phone widths. |
| Accessibility | Every primary journey remains complete with screen reader and 200 percent Android font scaling. |

## Part II — Experience Architecture
