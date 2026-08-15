# UI/UX Specification

> Version 1.0.0

## 9. Protected-Application Management

#### 9.1 Application List

The Apps destination shall show eligible installed applications by localized name, icon, and current protection state. The list shall support name search and stable localized-name sorting. It shall show distinct states for loading, no eligible apps, no protected apps, no search results, refresh failure, removed applications, and an unavailable inventory.

During refresh, the last committed list may remain visible with a Checking label. Stale rows shall not be editable until identity is reconciled. Search shall not expose technical identifiers in the primary experience.

#### 9.2 Adding and Removing Protection

Adding protection may use the global relock default and takes effect only after secure persistence succeeds. Removing protection shall name the selected application and state that it will open without App Lock authentication. The change requires App Lock authentication when the current session is not sufficient for a protection-reducing action.

Failed changes shall restore the previous committed state and explain that no change was applied. The interface shall not leave an optimistic switch position after persistence fails.

#### 9.3 Application Detail

The detail view shall identify the application, whether it is installed and eligible, whether protection is enabled, the global relock behavior, and any current protection-health limitation affecting it. The only editable application-specific setting is protection enabled or disabled. There are no app-specific credentials, schedules, timeouts, notification rules, or profiles.

#### 9.4 Installation Changes

When a protected application is removed, App Lock shall remove or mark obsolete its local selection and update the visible count. Reinstallation shall not silently inherit protection unless Android identity continuity is securely established. Newly installed applications shall not trigger a recommendation or notification in Version 1.0.0.
