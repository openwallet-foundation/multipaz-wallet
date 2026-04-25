# Web Wallet UI Guidelines

This document serves as a strict guide for contributors and AI coding assistants (LLMs) regarding the approved UI architecture and component selection for the web-based Wallet interface.

Our frontend stack utilizes **Kotlin/JS**, **React**, and **MUI (Material-UI)** via JetBrains' `kotlin-mui` wrappers.

## Opinionated Component Selection

To maintain a clean, secure, and consistent interface focused on credential management and viewing, we limit our UI to a specific subset of MUI components.

When building new views, refactoring code, or prompting an LLM for UI generation, you must adhere to the following component guidelines:

### 1. Layout & Scaffolding
* **`Container` & `Grid` / `Stack`:** Use for responsive, rigid layouts. Avoid writing custom flexbox CSS; use the `Stack` component to achieve spatial arrangements instead.
* **`AppBar`:** Used for top-level navigation, branding, and displaying the wallet's connection/sync status.

### 2. Credential Management & Display
*(Note: The web interface is designed for credential management and inspection, not for active credential presentment.)*
* **`Card`, `CardContent`, & `CardActionArea`:** The fundamental containers for viewing digital identity documents. Any discrete credential must be rendered within a structured `Card` to signify an isolated data object.
* **`Chip`:** Used exclusively for credential status indicators (e.g., `Verified`, `Revoked`, `Expired`).

### 3. User Consent & Trust Interactions
* **`Dialog`:** Reserved for critical user actions. Whenever the wallet requests the user to sign a payload, confirm a deletion, or modify sensitive settings, it must be presented in a modal `Dialog` that interrupts the flow and clearly states the action.
* **`Snackbar`:** For transient success/error messages (e.g., "Credential successfully deleted" or "Sync complete").

### 4. Typography & Data Presentation
* **`Typography`:** All text must use the MUI `Typography` component to adhere to the application's type scale. Do not use raw HTML header tags (`<h1>`, `<p>`, `<span>`).
* **`List` & `ListItem`:** The standard approach for displaying the granular attributes within a credential (e.g., First Name, Date of Birth, Issue Date).

### 5. Icons (`kotlin-mui-icons`)
Rely on standard Material Icons to convey security and management concepts. Approved icons include:
* `VerifiedUser` / `Shield` (For cryptographic trust validation and security settings)
* `CreditCard` / `Badge` (For wallet metaphors and credential items)
* `Delete` / `Warning` (For destructive management actions)
* `Info` / `Settings` (For metadata and configuration)