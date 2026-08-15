# UI/UX Specification

> Version 1.0.0

## 17. Visual and Component Design System

#### 17.1 Visual Direction and Appeal

The interface shall look deliberate, calm, modern, and cohesive. It shall use strong information hierarchy, generous but efficient spacing, stable alignment, restrained elevation, consistent shapes, and one obvious primary action per decision region. Security states shall feel trustworthy and legible rather than theatrical. Healthy states are reassuring, warnings are direct, and interrupted protection is visually prominent without appearing punitive.

The shipped experience shall not look like an unconsidered assembly of default controls. Representative onboarding, Protection, Protected Apps, lock, Health, and Settings screens shall receive a visual design review in both system light and dark themes. Approval shall consider composition, rhythm, density, typography, icon consistency, state distinction, and polish at realistic content lengths.

#### 17.2 Semantic Color Tokens

<!-- table-widths: 1.9, 2.3, 2.3 -->
| Role | Required use | Flexibility |
| --- | --- | --- |
| Primary | Main action, selected navigation, and controlled emphasis. | Hue and exact value may follow the approved product expression. |
| Surface and on-surface | Screens, cards, dialogs, and readable content. | May use neutral or subtly tinted families in light and dark themes. |
| Outline and secondary content | Boundaries and lower-emphasis text. | May vary in value when hierarchy and contrast remain clear. |
| Critical | Protection interruption, destructive action, and severe error. | Need not be a fixed red, but shall be unmistakable and conventionally understandable. |
| Caution | Degraded or action-required conditions. | May vary by palette while remaining distinct from Critical and Positive. |
| Positive | Freshly verified protection and completion. | May vary by palette and shall never be the only success cue. |
| Focus | Keyboard and switch-access focus. | May follow brand direction when it is highly visible on every surface. |
| Scrim | Modal separation. | Opacity may vary; it is never a substitute for an opaque security cover. |

Exact hexadecimal colors are not mandatory. The palette may evolve with branding, theme, or visual refinement when semantic roles remain consistent, every meaningful pair meets contrast requirements, light and dark presentations remain coherent, and status is recognizable through text and icon as well as color. Components shall consume semantic roles rather than infer meaning from a raw color.

#### 17.3 Typography

Use Android’s system sans-serif or an approved highly readable family. A compact type scale shall distinguish display, screen title, section title, body, supporting text, and control labels. Normal user-visible text shall not be smaller than 12 sp. Screen titles shall remain visually dominant after font scaling. Fixed-height text containers and truncation of status, consequences, errors, or primary actions are prohibited.

#### 17.4 Spacing, Shape, and Layout

- Use a 4 dp base spacing system, with 8 dp for related items, 16 dp for standard content, 24 dp between sections, and 32 dp for major separation.
- Use at least 16 dp compact horizontal content padding and preserve Android system, cutout, gesture, and keyboard insets.
- Touch targets shall be at least 48 by 48 dp, with additional separation around destructive or adjacent keypad controls.
- Use a small consistent corner family for controls, cards, and dialogs. Decorative pill shapes are limited to compact status or selection indicators.
- Prefer tonal surface distinction to heavy shadow. Opaque security covers shall not depend on elevation or scrim opacity.
- Long prose shall use a readable line length even on a phone in landscape.

#### 17.5 Core Components

<!-- table-widths: 1.75, 4.75 -->
| Component | Required behavior |
| --- | --- |
| Primary button | One per decision region; explicit result label; defined pressed, focused, disabled, loading, and error behavior. |
| Secondary/text action | Used for cancellation or lower-emphasis alternatives without competing with the primary result. |
| Switch | Used only for an immediate binary change; a change requiring explanation, authentication, or another screen uses a row action. |
| List row | Clear label, optional supporting text, current state, and one coherent tap model. Navigation and selection remain distinct. |
| Status card | Icon, controlled headline, consequence, freshness when relevant, and one recovery or management action. |
| Text field/PIN entry | Persistent prompt, helper/error, appropriate keyboard, no secret restoration, and clear disabled state. |
| Dialog | Named scope, concise consequence, focus containment, explicit cancellation, and visually separated destructive action. |
| Snackbar/message | Brief non-critical result with at most one action; never the sole representation of protection interruption. |
| Progress | Determinate only from real work units; otherwise indeterminate with explanatory text and a bounded outcome. |

#### 17.6 Authentication Controls

PIN controls shall use large labeled targets, stable placement, masked indicators, Delete, and safe cancellation. Biometric authentication shall use Android’s prompt. Failure, delay, lockout, fallback, cancellation, session expiry, and protection interruption shall each have a distinct visual and textual state.

#### 17.7 Icons, Imagery, Haptics, and Motion

Use one coherent Android icon family and optical weight. Directional icons mirror in right-to-left layouts. Every status icon has text. Decorative art may support Welcome, empty, or healthy completion states but shall not imitate Android permission or biometric surfaces, trivialize protection loss, or compete with recovery.

Haptics may confirm direct keypad input, successful authentication, and a high-impact confirmation only when Android settings permit. Passive status changes, countdowns, and repeated errors shall not vibrate continuously.
