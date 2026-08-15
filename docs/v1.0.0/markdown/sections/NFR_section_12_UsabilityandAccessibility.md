# Non-Functional Requirements

## Version 1.0.0

## 12. Usability and Accessibility

### NFR-UX-001 - User Interface Consistency

The application shall use consistent navigation, terminology, iconography, actions, and state behavior across all retained screens.

Acceptance criteria:

- Equivalent actions and protection states use the same label and presentation.
- Onboarding, protection status, authentication, settings, and recovery do not contradict one another.

Verification: Design review and usability test.

### NFR-UX-002 - Learnability

A first-time user shall be able to complete the primary setup and protection workflow without extensive external instructions.

Acceptance criteria:

- A representative user can create a PIN, understand optional biometrics, grant Usage Access, complete required setup, select an application, and verify protection.
- Explanations communicate limitations without internal technical terminology.

Verification: Usability test.

### NFR-UX-003 - User Interaction Efficiency

Common retained tasks shall avoid unnecessary navigation and repeated input.

Acceptance criteria:

- Protecting or unprotecting one application is available directly from the application list after required authentication.
- Permission recovery returns to the interrupted task and rechecks automatically.
- PIN is not requested again for the same protected application while its valid session applies, except for a sensitive settings change that explicitly requires it.

Verification: Workflow inspection and usability test.

### NFR-UX-004 - Visual Consistency

The application shall present a polished, cohesive phone interface using the approved visual system.

Acceptance criteria:

- Typography, spacing, color roles, icons, surfaces, and component states are applied consistently.
- Protection and error severity are visually clear without appearing alarming during normal use.
- A theme selector or multiple custom themes are not required.

Verification: Visual review.

### NFR-UX-005 - Error Prevention

The interface shall reduce avoidable mistakes through clear state, confirmation, and constrained input.

Acceptance criteria:

- PIN creation requires confirmation.
- Protection-reducing and destructive actions explain their effect before completion.
- Unsupported settings and incomplete required states cannot be saved as valid.

Verification: Usability and negative testing.

### NFR-UX-006 - Error Recovery Support

Error and Degraded-state messages shall explain the effect and the next safe action.

Acceptance criteria:

- Messages avoid internal implementation language.
- Permission loss, interrupted protection, failed local-data recovery, and forgotten PIN each have distinct guidance.
- A retry action is offered only when retry can change the result.

Verification: Content review and usability test.

### NFR-UX-007 - Accessibility Standards

The application shall conform to applicable Android accessibility guidance for the retained phone interface.

Acceptance criteria:

- No Critical accessibility defect remains in a primary workflow.
- Accessibility does not weaken PIN privacy or protection behavior.

Verification: Automated and manual accessibility assessment.

### NFR-UX-008 - Screen Reader Compatibility

Primary workflows shall be operable with a supported Android screen reader.

Acceptance criteria:

- Interactive controls expose meaningful name, role, state, and action.
- Focus follows task order and does not move behind authentication, dialogs, or sheets.
- Protection state and application protection selection are announced without relying on color or icon alone.

Verification: Manual screen-reader test.

### NFR-UX-009 - Visual Accessibility

Text and interface elements shall remain perceivable across supported visual-accessibility settings.

Acceptance criteria:

- Text and essential controls meet the approved contrast ratios.
- Status is never conveyed by color alone.
- Content remains understandable at the supported maximum font and display scaling without clipping or loss of action.

Verification: Measurement and visual test.

### NFR-UX-010 - Touch Accessibility

Interactive controls shall provide suitable touch targets and separation for reliable phone use.

Acceptance criteria:

- Touch targets comply with current Android guidance.
- PIN controls, application-selection controls, permission actions, and destructive confirmation are not crowded or ambiguous.

Verification: Measurement and usability test.

### NFR-UX-011 - Adaptive User Interface

The interface shall adapt to conventional phone windows, portrait, functional secure landscape, supported text scaling, display scaling, and phone multi-window states.

Acceptance criteria:

- Portrait is the primary optimized presentation.
- Landscape remains complete, secure, and operable.
- Split-screen and picture-in-picture fail safely where a reliable protection presentation cannot be provided.
- No tablet, foldable, large-screen, desktop, or multi-pane layout is required.

Verification: Phone configuration test.

### NFR-UX-012 - Localization Readiness

User-visible content shall be prepared for later localization without requiring version 1.0.0 to ship multiple languages.

Acceptance criteria:

- User-visible strings are externalized from application logic.
- Layout accommodates reasonable text expansion and does not embed meaning in an image alone.
- Version 1.0.0 may ship one approved language.

Verification: Design and source inspection.

### NFR-UX-014 - Accessibility Verification

Accessibility shall be verified across every primary retained workflow.

Acceptance criteria:

- Verification covers onboarding, PIN, biometric fallback, protected-application selection, permission guidance, protection status, settings, destructive reset, and error recovery.
- Automated checks are supplemented by manual screen-reader, text-scaling, focus, contrast, and touch review.

Verification: Accessibility assessment.
