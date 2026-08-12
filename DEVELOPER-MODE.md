# Developer Mode in Multipaz Wallet

Multipaz Wallet includes a Developer Mode that provides diagnostic tools, low-level inspection options, and testing features.

## Enabling Developer Mode

To enable Developer Mode:
1. Open the main wallet screen.
2. Tap the title bar ("Multipaz Wallet") 5 times in rapid succession.
3. A confirmation message will appear indicating that Developer Mode is enabled.

Once enabled, a **Developer Settings** item will appear under **Settings**, and screen title clicks on specific pages will navigate to low-level developer views.

## Developer Settings

The **Developer Settings** screen (accessible via **Settings** → **Developer Settings**) provides the following options:

- **Enable debug logging**: Toggles verbose debug logs useful for troubleshooting.
- **Use NFCv2 for presentment**: Toggles whether NFCv2 is used during presentment scanning (defaults to on).
- **Corrupt encryption key in Google Drive**: Intentionally corrupts stored key data to test encryption key recovery and error handling flows.
- **Revoke Google Drive access**: Revokes OAuth authorization for Google Drive app data storage to test sign-in and consent prompts.
- **Clear explicitly signed out flag**: Resets the signed-out state to prompt sign-in on next application launch.
- **Set wallet backend**: Configures a custom server URL for the wallet backend or resets to the default backend.
- **Run first-time setup**: Resets the first-time setup flag to re-trigger onboarding without deleting existing wallet data.
- **Delete app data**: Clears all local application data, credentials, and settings to simulate a clean install.
- **Refresh reader keys**: Clears cached reader authentication keys and fetches new ones from the backend server.
- **Clear revocation cache**: Removes all cached revocation lists (CRLs) and status data.
- **Run periodic refresh**: Manually triggers background maintenance tasks (updating public data, credentials, and reader keys).
- **Developer documentation**: Displays this in-app documentation.
- **Exit developer mode**: Disables Developer Mode and hides developer-specific items.

## Screen Title Developer Extras

When Developer Mode is enabled, tapping the top bar title on specific screens opens detailed developer diagnostic views:

- **Document Info Screen**: Tapping the title on a document's details page opens **Document Info Extras**, displaying low-level credential details, domain groupings, certification states, validity periods, usage counts, raw claims, and manual credential refresh controls.
- **Verification Response Screen**: Tapping the title on a verification response page opens **Verification Developer Extras**, displaying raw presentment records, CBOR request/response structures, session transcripts, and trust metadata.

## Additional Developer Features

- **Custom OpenID4VCI Issuer URL**: In the **Add to Wallet** screen, an **Enter Issuer URL** option appears to manually specify custom credential issuers.
- **User Defined Verification Query**: In the verifier's **Select Verification Type** screen, a **User Defined Query** option enables testing arbitrary or custom verification requests.
- **NFC-Only Presentment Scan**: Long-pressing the NFC button in the verifier screen initiates an NFC-only presentment scan mode.
