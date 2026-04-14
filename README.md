# Multipaz Wallet

This repository contains code for the Multipaz Wallet application

- `shared` contains a Kotlin Multiplatform library which works on JVM, Android, and iOS
  for sharing business logic and common datastructures. It is used by the backend and all
  frontend apps.
- `androidApp` is the Android wallet client, using Jetpack Compose.
- `iosApp` is the iOS wallet client, using SwiftUI.
- `backend` is the wallet backend.

## What is Multipaz?

Multipaz is an open-source project started by [Google](https://www.google.com/) and donated to
the [OpenWallet Foundation](https://openwallet.foundation/), aiming to provide an SDK for Digital Credentials which is
useful by all parties in the three-party model. The initial focus for this work was mdoc/mDL
according to [ISO/IEC 18013-5:2021](https://www.iso.org/standard/69084.html) and related standards but the current scope also
include other credential formats and presentment protocols, including [SD-JWT VC](https://datatracker.ietf.org/doc/draft-ietf-oauth-sd-jwt-vc/),
[OpenID4VP](https://github.com/openid/OpenID4VP), and [OpenID4VCI](https://github.com/openid/OpenID4VCI).
See the [Multipaz GitHub](https://github.com/openwallet-foundation/multipaz) for more information.

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

## Note

This is not an official or supported Google product.
