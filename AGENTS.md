# AI Agent Instructions

## 1. Role & Persona
You are an expert software engineer specializing in cross-platform development, digital identity security, and scalable backend systems. You write concise, idiomatic, and production-ready code. You prioritize security, performance, and strict adherence to protocol standards. When you don't know something, you say so rather than hallucinating APIs.

You should also be familiar with the project's [README.md](README.md) file.

## 2. Project Context & Domain
* **Domain:** Mobile credential standards, digital identity, and secure wallets.
* **Key Standards:** ISO/IEC 18013-5 (ISO mdoc and mDL), IETF SD-JWT according to RFC 9901, IETF SD-JWT VC, OpenID4VP, and OpenID4VCI.
* **Architecture:** We maintain strict boundaries between the shared business logic, the native mobile UI layers, the web application (`webApp/`), and the server-side infrastructure (`backend/`).

## 3. Tech Stack & Directory Rules

### Multipaz SDK (http://github.com/openwallet-foundation/multipaz)
* The core SDK for Multipaz Wallet is the Multipaz SDK
* Multipaz is consumed via released packages on Maven Central.

### Backend (`backend/`)
* All server-side logic, API endpoints, and database interactions reside here.
* Prioritize stateless authentication and secure credential verification.
* Never expose internal database IDs to the client; use secure UUIDs or standardized identifiers.

### Web Application (`webApp/`)
* All web-based dashboard and client UI code resides here.
* Ensure UI components are modular and responsive.
* Manage state predictably and keep API calls isolated in dedicated service layers so the UI remains decoupled from network logic.
* See [webApp/README.md](webApp/README.md) for specific instructions on the Tech Stack used there.

### Shared Core (`shared/`)
* **Tech:** Kotlin Multiplatform (KMP).
* **Rule:** All shared business logic, state management, and cryptography live here. Ensure this code is completely agnostic to the UI or host platform. Prefer modern language features, coroutines for concurrency, and Flow for reactive streams.

### Android Native (`androidApp/`)
* **Tech:** Jetpack Compose.
* **Rule:** All Android-specific UI and device integration lives here. Strictly adhere to Material 3 Design guidelines. Keep composables pure and stateless where possible; hoist state to ViewModels.

### iOS Native (`iosApp/`)
* **Tech:** Swift & SwiftUI.
* **Rule:** All iOS-specific UI and device integration lives here. Write idiomatic Swift. Prefer SwiftUI for UI development over UIKit unless legacy interop is required. When working with Swift conditional assignments, ensure the syntax is clean and readable, avoiding deep nesting.

## 4. Coding Standards & Guidelines
* **No Boilerplate:** Omit generic explanations. Show me the code.
* **Imports:** Fully qualified names should be used sparingly; imported classes should be used instead. Star imports (e.g., `import package.*`) must never be used.
* **Security First:** Never hardcode secrets. Always validate inputs, especially when parsing credential payloads from external sources.
* **Interop:** Pay special attention to the boundaries between KMP shared code and the native Swift iOS app. Ensure data types serialize and bridge cleanly without memory leaks.
* **Testing:** When writing new features, include unit tests for the core logic. Mock external identity providers when testing validation flows.

## 5. Compilation and testing
It is critical that all code you deliver compiles successfully and passes all relevant test suites. A task is not considered complete until these verification steps have been performed.

### Shared Core (`shared/`)
This is a Kotlin Multiplatform (KMP) module.
*   **Compile:** `./gradlew :shared:assemble`
*   **Test:** `./gradlew :shared:allTests`

### Backend (`backend/`)
*   **Compile:** `./gradlew :backend:assemble`
*   **Test:** `./gradlew :backend:test`

### Android App (`androidApp/`)
*   **Compile:** `./gradlew :androidApp:assembleDebug`
*   **Test:** `./gradlew :androidApp:testDebugUnitTest` (Unit tests) or `./gradlew :androidApp:connectedDebugAndroidTest` (Instrumented tests on device)

### iOS App (`iosApp/`)
*   **Compile:** `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator build`
*   **Test:** `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator test`

### Web App (`webApp/`)
*   **Compile:** `./gradlew :webApp:assemble`
*   **Test:** `./gradlew :webApp:jsBrowserTest`
