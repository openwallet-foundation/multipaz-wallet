# Multipaz Wallet

This repository contains code for the Multipaz Wallet application

- `shared` contains a Kotlin Multiplatform library which works on JVM, Android, and iOS
  for sharing business logic and common datastructures. It is used by the backend and all
  frontend apps.
- `androidApp` is the Android wallet client, using Jetpack Compose.
- `iosApp` is the iOS wallet client, using SwiftUI.
- `webApp` is the Web wallet client, using Kotlin/JS, React, and Tailwind CSS (see [webApp/README.md](webApp/README.md)).
- `backend` is the wallet backend.

## What is Multipaz?

Multipaz is an open-source project started by [Google](https://www.google.com/) and donated to
the [OpenWallet Foundation](https://openwallet.foundation/), aiming to provide an SDK for Digital Credentials which is
useful by all parties in the three-party model. The initial focus for this work was mdoc/mDL
according to [ISO/IEC 18013-5:2021](https://www.iso.org/standard/69084.html) and related standards but the current scope also
include other credential formats and presentment protocols, including [SD-JWT VC](https://datatracker.ietf.org/doc/draft-ietf-oauth-sd-jwt-vc/),
[OpenID4VP](https://github.com/openid/OpenID4VP), and [OpenID4VCI](https://github.com/openid/OpenID4VCI).
See the [Multipaz GitHub](https://github.com/openwallet-foundation/multipaz) for more information.

## Design Language

Multipaz Wallet prioritizes a clean, native experience across all supported platforms while maintaining consistent structural patterns:

* **Platform-Native UI Guidelines:**
  * **Android:** Strictly adheres to [Material 3 Design Guidelines](https://m3.material.io/) using Jetpack Compose.
  * **iOS:** Adheres to Apple's [Human Interface Guidelines (HIG)](https://developer.apple.com/design/human-interface-guidelines/) using SwiftUI.
  * **Web:** Built with React and Tailwind CSS for responsive web application experiences.

* **List Items & Navigation Conventions:**
  * For structured lists across mobile apps, we use `FloatingItemList` from the Multipaz SDK (available for both Compose and SwiftUI).
  * **Chevrons (`showChevron`):** List items display a trailing chevron (`showChevron = true` / `showChevron: true`) **only** if clicking the item navigates to another screen.
  * Chevrons are omitted (`showChevron = false` or omitted) for items that perform inline actions, toggle controls (such as switches), or open dialogs/confirmation sheets.

* **Terminology (Passes vs. Documents):**
  * We use the word **"pass"** for objects stored in the user's wallet and shown in the UI.
  * We use the word **"document"** when referring to credentials/objects received from other wallets (e.g., during verification), or technical terms like ISO mdoc document types.

## Dev and Prod instances

For development, we host a backend at https://dev.wallet.multipaz.org and this backend is
configured to talk to clients compiled from source and running on devices that are not normally
considered trustworthy e.g. Android devices with an unlocked bootloader. In general, these
wallet instances should not be trusted by participants in the greater Digital Credentials
ecosystem, but it's still very useful for day-to-day development.

For production, we host the backend at https://wallet.multipaz.org along with prebuilt APKs
available at https://apps.multipaz.org. This backend will only accept clients running on
trustworthy devices (i.e. on Android, [verified boot needs to be GREEN](https://source.android.com/docs/security/features/verifiedboot) among other things).
The key material used to sign APKs, attestations, and other things is kept secret meaning that
participants in the Digital Credential ecosystem (credential issuers and relying parties) can
safely trust such wallet instances for e.g. anti-cloning guarantees.

## Running the Wallet Server

### Production-like mode
To run the backend server along with the bundled web application (no hot-reload):
```bash
./gradlew :backend:run
```
The server will be available at `http://localhost:8010`. You can access the web application at `http://localhost:8010/web/`.

### Development mode (with Hot Reload)
For a better development experience with Hot Module Replacement (HMR) for the web application:

1.  **Start the Backend** (for RPC services):
    ```bash
    ./gradlew :backend:run
    ```
2.  **Start the Web Dev Server** (in a separate terminal):
    ```bash
    ./gradlew :webApp:jsBrowserDevelopmentRun --continuous
    ```
The development web application will be available at `http://localhost:8011`. Changes to the Kotlin
code in the `webApp` or `shared` modules will be automatically reflected in the browser. You
can also point the mobile apps to that wallet backend by clicking Avatar → Developer Settings →
Set wallet backend.

## Developer Mode

Multipaz Wallet includes a built-in Developer Mode for testing, debugging, and advanced configuration.
For full details on enabling Developer Mode and all available options, see [DEVELOPER-MODE.md](DEVELOPER-MODE.md).

## Running the Android app

To build the Android app debug APK:
```bash
./gradlew :androidApp:assembleDebug
```
You can also open the repository root in Android Studio, select the `androidApp` run configuration, and run it on an emulator or physical device.

## Running the iOS app

To run the iOS app, first you need to build the XCFramework, like this
```bash
./gradlew shared:assembleXCFramework
```
This framework contains all the Multipaz libraries as well as common code in `shared/` and will
be available as a Swift package via `Package.swift`.

You need to run this step every time code in `shared/` is changed or when updating to a new
Multipaz version. Since building the framework is a time-consuming step (~10 minutes) changes
to `shared/` are normally tested with the Android app, Web App, or unit tests first.

### Developer Configuration

To build the iOS app in Xcode you need local Apple signing settings in
`iosApp/DeveloperConfig.xcconfig`. This file is intentionally gitignored because each
developer uses their own Apple Developer Team, bundle ID, and App Group. The project
includes a template at `iosApp/DeveloperConfig.xcconfig.template`.

```shell
cp iosApp/DeveloperConfig.xcconfig.template iosApp/DeveloperConfig.xcconfig && \
$EDITOR iosApp/DeveloperConfig.xcconfig
```

In the opened editor, set:

```xcconfig
DEVELOPMENT_TEAM = YOUR_APPLE_TEAM_ID
LOCAL_BUNDLE_ID = your.registered.wallet.bundle.id
APP_GROUP_ID = group.your.registered.wallet.app.group
```

`LOCAL_BUNDLE_ID` is used as the `iosApp` bundle identifier. The
`IdentityDocumentProviderExtension` target appends `.IdentityDocumentProviderExtension`
to the same value, so register both bundle identifiers in your Apple Developer account.
Both targets use `APP_GROUP_ID` for their App Groups entitlement and for the shared
iOS storage container, so the App Group must be enabled for both App IDs.

For example, if `LOCAL_BUNDLE_ID` is `org.example.wallet`, the extension bundle
identifier is `org.example.wallet.IdentityDocumentProviderExtension`.

Once configured, open `iosApp/iosApp.xcodeproj` in Xcode, select the `iosApp` scheme
and a target device or simulator, and run the app. You usually need to clean the build
folder after assembling the XCFramework (Product -> Clean Build Folder...) before building.

## Running Tests

To run unit and integration tests across the project modules:

- **Shared Core:** `./gradlew :shared:allTests`
- **Backend:** `./gradlew :backend:test`
- **Android App:** `./gradlew :androidApp:testDebugUnitTest`
- **Web App:** `./gradlew :webApp:jsBrowserTest`
- **iOS App:** `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator test`

## Contributions

Please follow the coding style guidelines in [CODING-STYLE.md](CODING-STYLE.md).

Commit messages must be detailed and to the point. If referring to classes or types in the
project enclose it in backticks, if referring to function or method names use trailing
open and close parenthesis.

First line of the commit message must end in a period and be no longer than 72 characters
and should avoid using type or function names.

Commit messages should use line breaks and lines should not be larger than 80 characters,
with exceptions to avoid breaking hyperlinks.

Commit messages must have a "Test:" stanza detailing how the change was tested.

Commit messages have a Signed-off-by line.

## Note

This is not an official or supported Google product.
