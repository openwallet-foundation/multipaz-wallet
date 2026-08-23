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
    var documentTypeRepository: DocumentTypeRepository!
    var documentStore: DocumentStore!
    var documentModel: DocumentModel!
    var readerTrustManager: TrustManager!
    var provisioningModel: ProvisioningModel!
    //var provisioningSupport: ProvisioningSupport!

    var walletClient: WalletClient!
    var signedInUser: WalletClientSignedInUser? = nil

    let promptModel = Platform.shared.promptModel
    
    private let presentmentModel = PresentmentModel()
    let verticalCardListState = VerticalCardListState()
    
    func push(_ destination: Destination) {
        if path.last != destination {
            var transaction = Transaction()
            transaction.disablesAnimations = true
            withTransaction(transaction) {
                path.append(destination)
            }
        }
    }

    func popWithoutAnimation() {
        if !path.isEmpty {
            var transaction = Transaction()
            transaction.disablesAnimations = true
            withTransaction(transaction) {
                path.removeLast()
            }
        }
    }

    func load() async {
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
        // TODO: Utopia docTypes
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
        readerTrustManager = TrustManager(storage: storage, identifier: "default", partitionId: "default_default")
        
        try! await readerTrustManager.deleteAll()
        try! await readerTrustManager.addX509Cert(
            certificate: X509Cert.companion.fromPem(
                pemEncoding: """
                -----BEGIN CERTIFICATE-----
                MIICYTCCAeegAwIBAgIQOSV5JyesOLKHeDc+0qmtuTAKBggqhkjOPQQDAzAzMQswCQYDVQQGDAJV
                UzEkMCIGA1UEAwwbTXVsdGlwYXogSWRlbnRpdHkgUmVhZGVyIENBMB4XDTI1MDcwNTEyMjAyMVoX
                DTMwMDcwNTEyMjAyMVowMzELMAkGA1UEBgwCVVMxJDAiBgNVBAMMG011bHRpcGF6IElkZW50aXR5
                IFJlYWRlciBDQTB2MBAGByqGSM49AgEGBSuBBAAiA2IABD4UX5jabDLuRojEp9rsZkAEbP8Icuj3
                qN4wBUYq6UiOkoULMOLUb+78Ygonm+sJRwqyDJ9mxYTjlqliW8PpDfulQZejZo2QGqpB9JPInkrC
                Bol5T+0TUs0ghkE5ZQBsVKOBvzCBvDAOBgNVHQ8BAf8EBAMCAQYwEgYDVR0TAQH/BAgwBgEB/wIB
                ADBWBgNVHR8ETzBNMEugSaBHhkVodHRwczovL2dpdGh1Yi5jb20vb3BlbndhbGxldC1mb3VuZGF0
                aW9uLWxhYnMvaWRlbnRpdHktY3JlZGVudGlhbC9jcmwwHQYDVR0OBBYEFM+kr4eQcxKWLk16F2Rq
                zBxFcZshMB8GA1UdIwQYMBaAFM+kr4eQcxKWLk16F2RqzBxFcZshMAoGCCqGSM49BAMDA2gAMGUC
                MQCQ+4+BS8yH20KVfSK1TSC/RfRM4M9XNBZ+0n9ePg9ftXUFt5e4lBddK9mL8WznJuoCMFuk8ey4
                lKnb4nubv5iPIzwuC7C0utqj7Fs+qdmcWNrSYSiks2OEnjJiap1cPOPk2g==
                -----END CERTIFICATE-----
                """.trimmingCharacters(in: .whitespacesAndNewlines)
            ),
            metadata: TrustMetadata(
                displayName: "Multipaz Identity Reader",
                displayIcon: nil,
                displayIconUrl: "https://www.multipaz.org/multipaz-logo-200x200.png",
                privacyPolicyUrl: nil,
                disclaimer: nil,
                testOnly: true,
                extensions: [:]
            )
        )
        try! await readerTrustManager.addX509Cert(
            certificate: X509Cert.companion.fromPem(
                pemEncoding: """
                -----BEGIN CERTIFICATE-----
                MIICiTCCAg+gAwIBAgIQQd/7PXEzsmI+U14J2cO1bjAKBggqhkjOPQQDAzBHMQswCQYDVQQGDAJV
                UzE4MDYGA1UEAwwvTXVsdGlwYXogSWRlbnRpdHkgUmVhZGVyIENBIChVbnRydXN0ZWQgRGV2aWNl
                cykwHhcNMjUwNzE5MjMwODE0WhcNMzAwNzE5MjMwODE0WjBHMQswCQYDVQQGDAJVUzE4MDYGA1UE
                AwwvTXVsdGlwYXogSWRlbnRpdHkgUmVhZGVyIENBIChVbnRydXN0ZWQgRGV2aWNlcykwdjAQBgcq
                hkjOPQIBBgUrgQQAIgNiAATqihOe05W3nIdyVf7yE4mHJiz7tsofcmiNTonwYsPKBbJwRTHa7AME
                +ToAfNhPMaEZ83lBUTBggsTUNShVp1L5xzPS+jK0tGJkR2ny9+UygPGtUZxEOulGK5I8ZId+35Gj
                gb8wgbwwDgYDVR0PAQH/BAQDAgEGMBIGA1UdEwEB/wQIMAYBAf8CAQAwVgYDVR0fBE8wTTBLoEmg
                R4ZFaHR0cHM6Ly9naXRodWIuY29tL29wZW53YWxsZXQtZm91bmRhdGlvbi1sYWJzL2lkZW50aXR5
                LWNyZWRlbnRpYWwvY3JsMB0GA1UdDgQWBBSbz9r9IFmXjiGGnH3Siq90geurxTAfBgNVHSMEGDAW
                gBSbz9r9IFmXjiGGnH3Siq90geurxTAKBggqhkjOPQQDAwNoADBlAjEAomqjfJe2k162S5Way3sE
                BTcj7+DPvaLJcsloEsj/HaThIsKWqQlQKxgNu1rE/XryAjB/Gq6UErgWKlspp+KpzuAAWaKk+bMj
                cM4aKOKOU3itmB+9jXTQ290Dc8MnWVwQBs4=
                -----END CERTIFICATE-----
                """.trimmingCharacters(in: .whitespacesAndNewlines)
            ),
            metadata: TrustMetadata(
                displayName: "Multipaz Identity Reader (Untrusted Devices)",
                displayIcon: nil,
                displayIconUrl: "https://www.multipaz.org/multipaz-logo-200x200.png",
                privacyPolicyUrl: nil,
                disclaimer: nil,
                testOnly: true,
                extensions: [:]
            )
        )
        try! await readerTrustManager.addX509Cert(
                certificate: X509Cert.companion.fromPem(
                    pemEncoding: """
                        -----BEGIN CERTIFICATE-----
                        MIICfjCCAgSgAwIBAgIQJcmMK89tPNDdH7WpEBuqQDAKBggqhkjOPQQDAzBAMTEwLwYDVQQDDChW
                        ZXJpZmllciBSb290IGF0IGh0dHBzOi8vd3MuZGF2aWR6MjUubmV0MQswCQYDVQQGDAJVUzAeFw0y
                        NjAxMjgxMzExMDhaFw00MTAxMjQxMzExMDhaMEAxMTAvBgNVBAMMKFZlcmlmaWVyIFJvb3QgYXQg
                        aHR0cHM6Ly93cy5kYXZpZHoyNS5uZXQxCzAJBgNVBAYMAlVTMHYwEAYHKoZIzj0CAQYFK4EEACID
                        YgAEuSk/1XRVNYel5yV3RgxtUNlUE85dLTjyKItqz1RUNyOZ7ZHzH4oadb6WnCcLbl5Px+f6i8yt
                        cyh4diTQWG2gtuSRxo05PfeZR2rBy0ToZvoVgI9j8nDbfyRGEMrSTHf4o4HCMIG/MA4GA1UdDwEB
                        /wQEAwIBBjASBgNVHRMBAf8ECDAGAQH/AgEBMCIGA1UdEgQbMBmGF2h0dHBzOi8vd3MuZGF2aWR6
                        MjUubmV0MDUGA1UdHwQuMCwwKqAooCaGJGh0dHBzOi8vd3MuZGF2aWR6MjUubmV0L2NybC92ZXJp
                        ZmllcjAdBgNVHQ4EFgQU1TlDuv6QRGOCxyVsiV4KfUT0yvMwHwYDVR0jBBgwFoAU1TlDuv6QRGOC
                        xyVsiV4KfUT0yvMwCgYIKoZIzj0EAwMDaAAwZQIwUSENplERttXfOr7yHxbdIhcHdlVEaXLUDbPy
                        XcXW1hbL168wE0ykh6v0grJcD/P1AjEA23KTndS1cXfSi5jLDyB+OZY6O5EpVhxjxwZDwucfo2L1
                        zPTt/emPh8XuL625gPbY
                        -----END CERTIFICATE-----
                        """.trimmingCharacters(in: .whitespacesAndNewlines)
                ),
                metadata: TrustMetadata(
                    displayName: "David's Identity Verifier",
                    displayIcon: nil,
                    displayIconUrl: "https://www.multipaz.org/multipaz-logo-200x200.png",
                    privacyPolicyUrl: "https://apps.multipaz.org",
                    disclaimer: nil,
                    testOnly: true,
                    extensions: [:]
                )
            )
        try! await readerTrustManager.addX509Cert(
                certificate: X509Cert.companion.fromPem(
                    pemEncoding: """
                        -----BEGIN CERTIFICATE-----
                        MIICaTCCAe+gAwIBAgIQtzUvFDCKLUBWQAZ4UnCw5zAKBggqhkjOPQQDAzA3MQswCQYDVQQGDAJV
                        UzEoMCYGA1UEAwwfdmVyaWZpZXIubXVsdGlwYXoub3JnIFJlYWRlciBDQTAeFw0yNTA2MTkyMjE2
                        MzJaFw0zMDA2MTkyMjE2MzJaMDcxCzAJBgNVBAYMAlVTMSgwJgYDVQQDDB92ZXJpZmllci5tdWx0
                        aXBhei5vcmcgUmVhZGVyIENBMHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEa6oCzC8rfHfwVOmQf83W
                        yHEQFE8HrLK+NxsufJDrSFgMXjhRvPt3fIjlMyRAaf94Y25Ux9tXg+28EzzB/xG7q8P/FQ9nOSJk
                        w4cQJVdD/ufN599uVdfp1URdG95Vncuoo4G/MIG8MA4GA1UdDwEB/wQEAwIBBjASBgNVHRMBAf8E
                        CDAGAQH/AgEAMFYGA1UdHwRPME0wS6BJoEeGRWh0dHBzOi8vZ2l0aHViLmNvbS9vcGVud2FsbGV0
                        LWZvdW5kYXRpb24tbGFicy9pZGVudGl0eS1jcmVkZW50aWFsL2NybDAdBgNVHQ4EFgQUsYQ5hS9K
                        buq/6mKtvFHQgfdIhykwHwYDVR0jBBgwFoAUsYQ5hS9Kbuq/6mKtvFHQgfdIhykwCgYIKoZIzj0E
                        AwMDaAAwZQIwKh87sK/cMbzuc9PFvyiSRedr2RoP0fuFK0X8ddOpi6hEMOapHL/Gs/QByROCpDpk
                        AjEA2yLSJDZEu1GI8uChAsDBZwJPtv5KHUjq1Vpok69SNn+zzb1mNpqmiey+tchPBjZm
                        -----END CERTIFICATE-----
                        """.trimmingCharacters(in: .whitespacesAndNewlines)
                ),
                metadata: TrustMetadata(
                    displayName: "Multipaz Verifier",
                    displayIcon: nil,
                    displayIconUrl: "https://www.multipaz.org/multipaz-logo-200x200.png",
                    privacyPolicyUrl: "https://apps.multipaz.org",
                    disclaimer: nil,
                    testOnly: true,
                    extensions: [:]
                )
            )

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
        } catch {
            print("Error refreshing wallet: \(error)")
        }
    }

    func importMpzPass(data: Data) async throws -> Document {
        let byteString = data.toByteString()
        let byteArray = byteString.toByteArray(startIndex: 0, endIndex: Int32(data.count))
        let dataItem = try Cbor.shared.decode(encodedCbor: byteArray)
        let pass = try await MpzPass.companion.fromDataItem(dataItem: dataItem)
        
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
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        path = [
            .walletScreen(
                documentId: document.identifier,
                justAddedAtMillis: nowMillis
            )
        ]
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
                        atTime: KotlinClockCompanion().getSystem().now()
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
                try! await promptModelRequestConsent(
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

