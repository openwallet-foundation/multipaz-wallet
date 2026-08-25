import ExtensionKit
import IdentityDocumentServices
import IdentityDocumentServicesUI
import SwiftUI
@preconcurrency import Multipaz
import Multipaz

fileprivate let TAG = "DocumentProviderExtension"

private class ExtensionLogPrinter: NSObject, LoggerLogPrinter {
    private let maxChunkLength = 800

    func print(level: LoggerLogPrinterLevel, tag: String, msg: String, throwable: KotlinThrowable?) {
        let levelStr: String
        switch level {
        case .debug: levelStr = "DEBUG"
        case .info: levelStr = "INFO"
        case .warning: levelStr = "WARNING"
        case .error: levelStr = "ERROR"
        default: levelStr = level.name
        }
        
        var fullMsg = msg
        if let throwable = throwable {
            fullMsg += "\nEXCEPTION: \(throwable)"
        }
        
        let lines = fullMsg.components(separatedBy: "\n")
        for line in lines {
            if line.count <= maxChunkLength {
                NSLog("Multipaz: [%@] [%@] %@", levelStr, tag, line)
            } else {
                var remaining = line[...]
                while !remaining.isEmpty {
                    let chunk = remaining.prefix(maxChunkLength)
                    NSLog("Multipaz: [%@] [%@] %@", levelStr, tag, String(chunk))
                    remaining = remaining.dropFirst(maxChunkLength)
                }
            }
        }
    }
}

private var isLoggingSetup = false

private func initializeLogging() {
    guard !isLoggingSetup else { return }
    isLoggingSetup = true
    // Logger.shared.isDebugEnabled = true
    Logger.shared.logPrinter = ExtensionLogPrinter()
    Logger.shared.i(tag: TAG, msg: "Initialized logging")
}


func getPresentmentSource() async -> PresentmentSource {
    let storage = IosStorage(
        storageFileUrl: FileManager.default.containerURL(
            forSecurityApplicationGroupIdentifier: Bundle.main.object(forInfoDictionaryKey: "AppGroupID") as! String)!
            .appendingPathComponent("storage.db"),
        excludeFromBackup: true
    )
    let secureArea = try! await Platform.shared.getSecureArea(storage: storage)
    let softwareSecureArea = try! await SoftwareSecureArea.companion.create(storage: storage)
    let secureAreaRepository = SecureAreaRepository.Builder()
        .add(secureArea: secureArea)
        .add(secureArea: softwareSecureArea)
        .build()
    let documentTypeRepository = DocumentTypeRepository()
    documentTypeRepository.addKnownTypes(locale: LocalizedStrings.shared.getCurrentLocale())
    let documentStore = DocumentStore.Builder(
        storage: storage,
        secureAreaRepository: secureAreaRepository
    ).build()
    
    let walletClient = try! await WalletClient.companion.create(
        clientType: ClientType.ios,
        url: BuildConfig.shared.BACKEND_URL,
        secret: BuildConfig.shared.BACKEND_SECRET,
        storage: storage,
        secureArea: secureArea,
        httpClientEngineFactory: Darwin(),
        numReaderKeys: 0
    )
    
    let userReaderTrustManager = TrustManager(
        storage: storage,
        identifier: "userReaderTrustManager",
        partitionId: "default_userReaderTrustManager"
    )
    
    let readerTrustManager = CompositeTrustManager(
        trustManagers: [walletClient.readerTrustManager, userReaderTrustManager],
        identifier: "readerTrustManager"
    )

    let zkSystemRepository = ZkSystemRepository()
    // Note: the RAM limit for IdentityDocumentProvider is 120 MB as of iOS 26 and
    //   Longfellow v0.9 uses around ~200MB. So until Apple increases the RAM limit
    //   for this extension ZKP will likely not work.
    //
    let longfellow = LongfellowZkSystem()
    longfellow.addDefaultCircuits()
    zkSystemRepository.add(zkSystem: longfellow)
    return SimplePresentmentSource.companion.create(
        documentStore: documentStore,
        documentTypeRepository: documentTypeRepository,
        zkSystemRepository: zkSystemRepository,
        resolveTrustFn: { requester in
            for requesterIdentity in requester.requesterIdentities {
                let certChain = requesterIdentity.certChain
                let result = try! await readerTrustManager.verify(
                    chain: certChain.certificates,
                    atTime: KotlinClockCompanion().getSystem().now(),
                    validateCaValidity: true
                )
                if result.isTrusted && result.trustPoints.first != nil {
                    return TrustedRequesterIdentity(
                        identity: requesterIdentity,
                        trustMetadata: result.trustPoints.first!.metadata
                    )
                }
            }
            return nil
        },
        showConsentPromptFn: { requester, trustedRequesterIdentity, consentData, preselectedDocuments, onDocumentsInFocus in
            return try! await promptModelSilentConsent(
                requester: requester,
                trustedRequesterIdentity: trustedRequesterIdentity,
                consentData: consentData,
                preselectedDocuments: preselectedDocuments,
                onDocumentsInFocus: { documents in onDocumentsInFocus(documents) }
            )
        },
        preferSignatureToKeyAgreement: true,
        domainsMdocSignature: [ Domains.shared.DOMAIN_MDOC_USER_AUTH, Domains.shared.DOMAIN_MDOC_SOFTWARE ],
        domainsMdocKeyAgreement: [],
        domainsKeylessSdJwt: [ Domains.shared.DOMAIN_SDJWT_KEYLESS, Domains.shared.DOMAIN_SDJWT_SOFTWARE ],
        domainsKeyBoundSdJwt: [ Domains.shared.DOMAIN_SDJWT_USER_AUTH, Domains.shared.DOMAIN_SDJWT_SOFTWARE ]
    )
}

@main
struct DocumentProviderExtension: IdentityDocumentProvider {

    var body: some IdentityDocumentRequestScene {
        ISO18013MobileDocumentRequestScene { context in
            RequestAuthorizationView(
                requestContext: context,
                getPresentmentSource: {
                    initializeLogging()
                    return await getPresentmentSource()
                }
            )
        }
    }

    func performRegistrationUpdates() async {
        initializeLogging()
        Logger.shared.i(tag: TAG, msg: "Handling performRegistrationUpdates")
        let source = await getPresentmentSource()
        let dcApi = try! await DigitalCredentialsCompanion.shared.getDefault()
        if dcApi.registerAvailable {
            do {
                try await dcApi.register(
                    documentStore: source.documentStore,
                    documentTypeRepository: source.documentTypeRepository,
                    selectedProtocols: dcApi.supportedProtocols,
                    forceRegistration: true
                )
            } catch {
                Logger.shared.e(tag: TAG, msg: "Error registering with DigitalCredentials API: \(error)")
            }
        }
    }
}

