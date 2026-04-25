# Web Wallet UI Guidelines

This document serves as a strict guide for contributors regarding the approved UI architecture and component selection for the web-based Wallet interface.

Our frontend stack utilizes **Kotlin/JS**, **React**, and **Tailwind CSS**.

## Opinionated Component & Styling Selection

Because Tailwind is a utility-first CSS framework rather than a pre-built component library, consistency relies on adhering to specific class patterns and accessible base components.

When building new views or refactoring code, contributors must adhere to the following guidelines:

### 1. Layout & Scaffolding
* **Containers:** Use standard `div` elements with Tailwind container classes (e.g., `container mx-auto max-w-4xl px-4`) to enforce rigid, responsive constraints.
* **Flexbox & Grid:** Avoid writing custom CSS. Use Tailwind's `flex`, `flex-col`, `gap-*`, and `grid` utilities to manage spatial arrangements.
* **Navigation:** The top-level navigation and wallet sync status should be housed in a consistent sticky header (`sticky top-0 bg-white border-b border-slate-200 z-50`).

### 2. Credential Management & Display
* **Cards:** Any discrete credential or identity document must be rendered as an isolated card. Standardize around a clean, high-trust aesthetic: `bg-white rounded-xl shadow-sm border border-slate-200 p-6`.
* **Status Badges:** Use rounded inline elements for status indicators (e.g., `Verified`, `Revoked`, `Expired`). Standard pattern: `inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium` combined with semantic colors (e.g., `bg-green-100 text-green-800`).

### 3. User Consent & Trust Interactions
* **Modals & Dialogs:** Reserved for critical user actions (e.g., confirming deletion, signing a payload, modifying settings). Because Tailwind does not provide behavior, use accessible headless components (like Headless UI) styled with Tailwind. Modals must include a fixed backdrop (`fixed inset-0 bg-slate-900/50`) and a centered dialog panel (`bg-white rounded-xl shadow-xl`).
* **Toast / Snackbars:** Use fixed-position alerts for transient messages ("Credential successfully deleted"). Standardize on bottom-right placement.

### 4. Typography & Data Presentation
* **Typography:** Use semantic HTML (`h1`, `h2`, `h3`, `p`) heavily styled with Tailwind's typography utilities to maintain a professional scale (e.g., `text-slate-900 text-xl font-semibold`).
* **Data Lists:** For displaying granular attributes within a credential (e.g., First Name, Issue Date), use definition lists (`dl`, `dt`, `dd`) or standard unordered lists styled as key-value grids (`grid grid-cols-3 gap-4 border-b border-slate-100 py-3`).

### 5. Icons
We rely on **Heroicons** (specifically the 24x24 Outline and Solid sets) to convey security and management concepts, as they pair perfectly with Tailwind. Approved conceptual icons include:
* `ShieldCheck` / `LockClosed` (For cryptographic trust validation and security settings)
* `Identification` / `CreditCard` (For wallet metaphors and credential items)
* `Trash` / `ExclamationTriangle` (For destructive management actions)
* `InformationCircle` / `Cog8Tooth` (For metadata and configuration)
