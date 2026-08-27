import Foundation
import UIKit
import Multipaz
import Observation
import SwiftUI

@MainActor
@Observable
class ViewModel {

    var path: [Destination] = []
    
    let settings = SettingsModel()

    var isLoading: Bool = true

    var storage: Storage!
    var secureArea: SecureArea!
    var softwareSecureArea: SoftwareSecureArea!
    var secureAreaRepository: SecureAreaRepository!
    var documentStore: DocumentStore!
    var documentTypeRepository: DocumentTypeRepository!
    var documentModel: DocumentModel!
    var userIssuerTrustManager: TrustManager!
    var userReaderTrustManager: TrustManager!
    var issuerTrustManager: CompositeTrustManager!
    var readerTrustManager: CompositeTrustManager!
    var userIssuerTrustManagerModel: TrustManagerModel!
    var backendIssuerTrustManagerModel: TrustManagerModel!
    var userReaderTrustManagerModel: TrustManagerModel!
    var backendReaderTrustManagerModel: TrustManagerModel!
    var provisioningModel: ProvisioningModel!
    //var provisioningSupport: ProvisioningSupport!

    var walletClient: WalletClient!
    var signedInUser: WalletClientSignedInUser? = nil

    let promptModel = Platform.shared.promptModel
    
    let proximityPresentmentModel = ProximityPresentmentModel()
    let verticalCardListState = VerticalCardListState()
    var proximityPresentmentDocumentId: String? = nil

    func showProximityPresentment(documentId: String) {
        withAnimation(.easeInOut(duration: 0.3)) {
            proximityPresentmentDocumentId = documentId
        }
    }

    func dismissProximityPresentment() {
        withAnimation(.easeInOut(duration: 0.3)) {
            proximityPresentmentDocumentId = nil
        }
    }
    
    func push(_ destination: Destination) {
        if case .proximityPresentment(let docId) = destination {
            showProximityPresentment(documentId: docId)
            return
        }
        if path.last != destination {
            if case .walletScreen = destination {
                var transaction = Transaction()
                transaction.disablesAnimations = true
                withTransaction(transaction) {
                    path.append(destination)
                }
            } else {
                path.append(destination)
            }
        }
    }

    func popWithoutAnimation() {
        if proximityPresentmentDocumentId != nil {
            dismissProximityPresentment()
        }
        if !path.isEmpty {
            var transaction = Transaction()
            transaction.disablesAnimations = true
            withTransaction(transaction) {
                path.removeLast()
            }
        }
    }

    func popToRootWithoutAnimation() {
        if !path.isEmpty {
            var transaction = Transaction()
            transaction.disablesAnimations = true
            withTransaction(transaction) {
                path.removeAll()
            }
        }
    }

    func load() async {
        Logger.shared.isDebugEnabled = true  // TODO: read this from settings
        
        PromptModel.Companion.shared.setGlobal(promptModel: promptModel)
        
        storage = IosStorage(
            storageFileUrl: FileManager.default.containerURL(
                forSecurityApplicationGroupIdentifier: Bundle.main.object(forInfoDictionaryKey: "AppGroupID") as! String)!
                .appendingPathComponent("storage.db"),
            excludeFromBackup: true
        )
        secureArea = try! await Platform.shared.getSecureArea(storage: storage)
        
        walletClient =  try! await WalletClient.companion.create(
            clientType: ClientType.ios,
            //url: "http://10.122.146.254:8010/rpc",
            url: BuildConfig.shared.BACKEND_URL,
            secret: BuildConfig.shared.BACKEND_SECRET,
            storage: storage,
            secureArea: secureArea,
            httpClientEngineFactory: Darwin(),
            numReaderKeys: 10,
            clientDevice: getIosClientDevice(),
            clientPlatform: getIosClientPlatform()
        )
        
        softwareSecureArea = try! await SoftwareSecureArea.companion.create(storage: storage)
        secureAreaRepository = SecureAreaRepository.Builder()
            .add(secureArea: secureArea)
            .add(secureArea: softwareSecureArea)
            .build()
        documentTypeRepository = DocumentTypeRepository()
        documentTypeRepository.addKnownTypes(locale: currentLocale)
        documentTypeRepository.addUtopiaTypes(locale: currentLocale)
        documentStore = DocumentStore.Builder(
            storage: storage,
            secureAreaRepository: secureAreaRepository
        ).build()
        // Keep in sync with iosApp/iosApp/iosApp.entitlements
        try! await documentStore.setIosMdocDoctypes(value: [
            "eu.europa.ec.av.1",
            "eu.europa.ec.eudi.pid.1",
            "org.iso.18013.5.1.mDL",
            "org.iso.23220.photoid.1",
        ])
        userIssuerTrustManager = TrustManager(
            storage: storage,
            identifier: "userIssuerTrustManager",
            partitionId: "default_userIssuerTrustManager"
        )
        issuerTrustManager = CompositeTrustManager(
            trustManagers: [walletClient.issuerTrustManager, userIssuerTrustManager],
            identifier: "issuerTrustManager"
        )
        
        userReaderTrustManager = TrustManager(
            storage: storage,
            identifier: "userReaderTrustManager",
            partitionId: "default_userReaderTrustManager"
        )
        readerTrustManager = CompositeTrustManager(
            trustManagers: [walletClient.readerTrustManager, userReaderTrustManager],
            identifier: "readerTrustManager"
        )
        userIssuerTrustManagerModel = TrustManagerModel(trustManager: userIssuerTrustManager)
        backendIssuerTrustManagerModel = TrustManagerModel(trustManager: walletClient.issuerTrustManager)
        userReaderTrustManagerModel = TrustManagerModel(trustManager: userReaderTrustManager)
        backendReaderTrustManagerModel = TrustManagerModel(trustManager: walletClient.readerTrustManager)
        
        Task {
            await self.userIssuerTrustManagerModel.refresh()
            await self.backendIssuerTrustManagerModel.refresh()
            await self.userReaderTrustManagerModel.refresh()
            await self.backendReaderTrustManagerModel.refresh()
        }

        self.provisioningModel = ProvisioningModel(
            documentProvisioningHandler: DocumentProvisioningHandler.companion.create(
                secureArea: secureArea,
                documentStore: documentStore,
                metadataHandler: nil,
                defaultDocumentProvisioningSettings: DocumentProvisioningSettings(
                    minValidTime: Duration.days(5).halfNanoseconds,
                    keyBoundCredentialMaxUses: 1,
                    keyBoundCredentialNumPerDomain: 5,
                    keylessCredentialMaxUses: Int32.max,
                    keylessCredentialNumPerDomain: 1,
                    userAuthTimeout: Duration.seconds(0).halfNanoseconds,
                    requestUserAuth: true,
                    requestNoUserAuth: true,
                    mdocUserAuthDomain: Domains.shared.DOMAIN_MDOC_USER_AUTH,
                    mdocNoUserAuthDomain: Domains.shared.DOMAIN_MDOC_NO_USER_AUTH,
                    sdJwtUserAuthDomain: Domains.shared.DOMAIN_SDJWT_USER_AUTH,
                    sdJwtNoUserAuthDomain: Domains.shared.DOMAIN_SDJWT_NO_USER_AUTH,
                    sdJwtKeylessDomain: Domains.shared.DOMAIN_SDJWT_KEYLESS
                ),
                selectSecureAreaFn: nil  // TODO
            ),
            httpClient: HttpClient(engineFactory: Darwin()) { config in
                config.followRedirects = false
            },
            promptModel: promptModel,
            authorizationSecureArea: secureArea,
            eventLogger: nil // TODO
        )
        //self.provisioningSupport = ProvisioningSupport(
        //    storage: storage,
        //    secureArea: secureArea
        //)
        //await self.provisioningSupport.initialize()
        
        let dcApi = try! await DigitalCredentialsCompanion.shared.getDefault()
        if dcApi.registerAvailable {
            do {
                try await dcApi.register(
                    documentStore: documentStore,
                    documentTypeRepository: documentTypeRepository,
                    selectedProtocols: dcApi.supportedProtocols,
                    forceRegistration: false
                )
            } catch {
                print("Error registering with DigitalCredentials API: \(error)")
            }
            Task {
                for await _ in documentStore.eventFlow {
                    Task {
                        do {
                            try await dcApi.register(
                                documentStore: documentStore,
                                documentTypeRepository: documentTypeRepository,
                                selectedProtocols: dcApi.supportedProtocols,
                                forceRegistration: false
                            )
                        } catch {
                            print("Error re-registering with DigitalCredentials API on eventFlow: \(error)")
                        }
                    }
                }
            }
        }
        
        documentModel = try! await DocumentModel(
            documentStore: documentStore,
            documentTypeRepository: documentTypeRepository,
            badgeFunction: { document in
                var badges: [DocumentBadge] = []
                if document.provisionedDocumentSetupNeeded {
                    badges.append(
                        DocumentBadge(
                            text: "Setup Required",
                            color: DocumentBadgeColor(red: 0, green: 0, blue: 0)
                        )
                    )
                }
                return badges
            }
        )
        
        Task {
            await self.syncAtStartup()
        }
        
        Task {
            var previousUser: WalletClientSignedInUser? = walletClient.signedInUser.value
            self.signedInUser = previousUser
            for await signedInUser in walletClient.signedInUser {
                let userJustSignedOut = previousUser != nil && signedInUser == nil
                let userJustSignedIn = previousUser == nil && signedInUser != nil
                previousUser = signedInUser
                self.signedInUser = signedInUser
                
                if userJustSignedOut {
                    do {
                        let documents = try await documentStore.listDocuments(sort: false)
                        for doc in documents {
                            try await documentStore.deleteDocument(identifier: doc.identifier)
                        }
                    } catch {
                        print("Error clearing documents on sign-out: \(error)")
                    }
                } else if userJustSignedIn {
                    Task {
                        await self.syncSharedData()
                    }
                }
            }
        }

        isLoading = false
    }
    
    func syncAtStartup() async {
        if walletClient.signedInUser.value != nil {
            await self.syncSharedData()
        }
        do {
            let _ = try await walletClient.refreshPublicData()
            await backendIssuerTrustManagerModel.refresh()
            await backendReaderTrustManagerModel.refresh()
        } catch {
            print("Error refreshing public data at startup: \(error)")
        }
    }
    
    private var isSyncingSharedData = false

    func syncSharedData() async {
        if isSyncingSharedData { return }
        isSyncingSharedData = true
        defer { isSyncingSharedData = false }

        if walletClient.signedInUser.value == nil {
            return
        }

        do {
            try await walletClient.refreshSharedData()
        } catch {
            print("Failed to refreshSharedData before sync: \(error)")
        }

        guard let sharedData = walletClient.sharedData.value else { return }
        do {
            try await documentStore.syncWithSharedData(
                sharedData: sharedData,
                mpzPassIsoMdocDomain: Domains.shared.DOMAIN_MDOC_SOFTWARE,
                mpzPassSdJwtVcDomain: Domains.shared.DOMAIN_SDJWT_SOFTWARE,
                mpzPassKeylessSdJwtVcDomain: Domains.shared.DOMAIN_SDJWT_KEYLESS,
                walletClient: walletClient,
                initialPreconsentSetting: DocumentPreconsentSetting.NeverRequireConsent.shared
            )
        } catch {
            print("Failed to syncWithSharedData: \(error)")
        }
    }

    func refreshWallet() async {
        do {
            let _ = try await walletClient.refreshPublicData()
            if walletClient.signedInUser.value != nil {
                await syncSharedData()
            }
            try await walletClient.refreshReaderKeys()
            
            let _ = await userIssuerTrustManagerModel.updateAllEntries()
            let _ = await userReaderTrustManagerModel.updateAllEntries()
            
            await backendIssuerTrustManagerModel.refresh()
            await backendReaderTrustManagerModel.refresh()
        } catch {
            print("Error refreshing wallet: \(error)")
        }
    }
    
    func getTrustManagerModel(identifier: String) -> TrustManagerModel? {
        switch identifier {
        case "backendIssuerTrustManager":
            return backendIssuerTrustManagerModel
        case "userIssuerTrustManager":
            return userIssuerTrustManagerModel
        case "backendReaderTrustManager":
            return backendReaderTrustManagerModel
        case "userReaderTrustManager":
            return userReaderTrustManagerModel
        default:
            return nil
        }
    }

    func importMpzPass(data: Data) async throws -> Document {
        let byteString = data.toByteString()
        let byteArray = byteString.toByteArray(startIndex: 0, endIndex: Int32(data.count))
        let dataItem = try Cbor.shared.decode(encodedCbor: byteArray)
        let pass = try await MpzPass.companion.fromDataItem(dataItem: dataItem, disableSignatureVerification: false)
        
        let existingDocs = try await documentStore.listDocuments(sort: false)
        if let existingDoc = existingDocs.first(where: { $0.mpzPassId == pass.uniqueId }) {
            if let existingVersion = existingDoc.mpzPassVersion?.int64Value, existingVersion >= pass.version {
                throw NSError(domain: "Multipaz", code: 2, userInfo: [NSLocalizedDescriptionKey: "The pass is already in your wallet."])
            }
        }
        
        if signedInUser != nil {
            let _ = try await walletClient.refreshSharedData()
            if let currentSharedData = walletClient.sharedData.value {
                let updatedSharedData = try await currentSharedData.removeMpzPass(pass: pass).addMpzPass(pass: pass)
                let _ = try await walletClient.setSharedData(sharedData: updatedSharedData, suppressSpinner: true)
            }
        }
        
        let document = try await documentStore.importMpzPass(
            mpzPass: pass,
            isoMdocDomain: Domains.shared.DOMAIN_MDOC_SOFTWARE,
            sdJwtVcDomain: Domains.shared.DOMAIN_SDJWT_SOFTWARE,
            keylessSdJwtVcDomain: Domains.shared.DOMAIN_SDJWT_KEYLESS
        )
        try await document.setMpzPassData(data: byteString)
        try await document.setPreconsentSetting(value: DocumentPreconsentSetting.NeverRequireConsent.shared)
        
        return document
    }

    func importAndShowMpzPass(data: Data) async throws {
        let document = try await importMpzPass(data: data)
        
        // Wait for DocumentModel to have the document in its list so focusedDocument is found immediately
        while !documentModel.documentInfos.contains(where: { $0.document.identifier == document.identifier }) {
            try? await Task.sleep(nanoseconds: 10_000_000)
        }
        
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        await MainActor.run {
            verticalCardListState.internalFocusedCardIdentifier = document.identifier
            verticalCardListState.lastFocusedCardIdentifier = document.identifier
            verticalCardListState.model.lastFocusedCardIdentifier = document.identifier
            verticalCardListState.scrollOffset = 0
            verticalCardListState.initialContentOffset = 0
            verticalCardListState.isScrollOffsetInitialized = false
            
            var transaction = Transaction()
            transaction.disablesAnimations = true
            withTransaction(transaction) {
                path = [
                    .walletScreen(
                        documentId: document.identifier,
                        justAddedAtMillis: nowMillis,
                        animateListTransitions: true
                    )
                ]
            }
        }
    }
    
    private var presentmentSource: PresentmentSource? = nil

    func getSource() -> PresentmentSource {
        if let presentmentSource = self.presentmentSource {
            return presentmentSource
        }
        let zkSystemRepository = ZkSystemRepository()
        let longfellow = LongfellowZkSystem()
        longfellow.addDefaultCircuits()
        zkSystemRepository.add(zkSystem: longfellow)
        let source = SimplePresentmentSource.companion.create(
            documentStore: documentStore,
            documentTypeRepository: documentTypeRepository,
            zkSystemRepository: zkSystemRepository,
            resolveTrustFn: { requester in
                for identity in requester.requesterIdentities {
                    let certChain = identity.certChain
                    let result = try! await self.readerTrustManager.verify(
                        chain: certChain.certificates,
                        atTime: KotlinClockCompanion().getSystem().now(),
                        validateCaValidity: true
                    )
                    if result.isTrusted {
                        if let trustPoint = result.trustPoints.first {
                            return TrustedRequesterIdentity(
                                identity: identity,
                                trustMetadata: trustPoint.metadata
                            )
                        }
                    }
                }
                return nil
            },
            showConsentPromptFn: { requester, trustedRequesterIdentity, consentData, preselectedDocuments, onDocumentsInFocus in
                return try! await promptModelRequestConsent(
                    requester: requester,
                    trustedRequesterIdentity: trustedRequesterIdentity,
                    consentData: consentData,
                    preselectedDocuments: preselectedDocuments,
                    onDocumentsInFocus: { documents in
                        onDocumentsInFocus(documents)
                    }
                )
            },
            preferSignatureToKeyAgreement: true,
            domainsMdocSignature: [ Domains.shared.DOMAIN_MDOC_USER_AUTH, Domains.shared.DOMAIN_MDOC_SOFTWARE ],
            domainsMdocKeyAgreement: [],
            domainsKeylessSdJwt: [ Domains.shared.DOMAIN_SDJWT_KEYLESS, Domains.shared.DOMAIN_SDJWT_SOFTWARE ],
            domainsKeyBoundSdJwt: [ Domains.shared.DOMAIN_SDJWT_USER_AUTH, Domains.shared.DOMAIN_SDJWT_SOFTWARE ]
        )
        self.presentmentSource = source
        return source
    }
}

private func getIosClientDevice() -> String {
    var systemInfo = utsname()
    uname(&systemInfo)
    let machine = withUnsafePointer(to: &systemInfo.machine) { ptr in
        String(cString: UnsafeRawPointer(ptr).assumingMemoryBound(to: CChar.self))
    }
    if machine == "x86_64" || machine == "arm64" {
        return "\(UIDevice.current.model) Simulator"
    } else {
        return mapIosModelCode(machine) ?? UIDevice.current.model
    }
}

private func getIosClientPlatform() -> String {
    let systemName = UIDevice.current.systemName
    let systemVersion = UIDevice.current.systemVersion
    return "\(systemName) \(systemVersion)"
}

private func mapIosModelCode(_ code: String) -> String? {
    switch code {
    case "iPhone14,2": return "iPhone 13 Pro"
    case "iPhone14,3": return "iPhone 13 Pro Max"
    case "iPhone14,4": return "iPhone 13 mini"
    case "iPhone14,5": return "iPhone 13"
    case "iPhone14,7": return "iPhone 14"
    case "iPhone14,8": return "iPhone 14 Plus"
    case "iPhone15,2": return "iPhone 14 Pro"
    case "iPhone15,3": return "iPhone 14 Pro Max"
    case "iPhone15,4": return "iPhone 15"
    case "iPhone15,5": return "iPhone 15 Plus"
    case "iPhone16,1": return "iPhone 15 Pro"
    case "iPhone16,2": return "iPhone 15 Pro Max"
    case "iPhone17,1": return "iPhone 16 Pro"
    case "iPhone17,2": return "iPhone 16 Pro Max"
    case "iPhone17,3": return "iPhone 16"
    case "iPhone17,4": return "iPhone 16 Plus"
    default: return nil
    }
}

