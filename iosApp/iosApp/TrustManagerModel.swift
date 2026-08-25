import Foundation
import SwiftUI
import Multipaz

struct TrustEntryInfo: Identifiable {
    let entry: TrustEntry
    let signedVical: SignedVical?
    let signedRical: SignedRical?
    
    var id: String { entry.identifier }
    
    var displayName: String {
        if let metaName = entry.metadata.displayName, !metaName.trimmingCharacters(in: .whitespaces).isEmpty {
            return metaName
        }
        return fallbackName
    }
    
    var fallbackName: String {
        if let certEntry = entry as? TrustEntryX509Cert {
            let cert = certEntry.certificate
            if let cn = cert.subject.components["2.5.4.3"]?.value {
                return cn
            }
            return cert.subject.name
        } else if let vical = signedVical?.vical {
            return vical.vicalProvider
        } else if let rical = signedRical?.rical {
            return rical.provider
        }
        return entry.identifier
    }
    
    var details: String {
        if entry is TrustEntryX509Cert {
            return "1 certificate"
        } else if let vical = signedVical?.vical {
            let count = vical.certificateInfos.count
            return "VICAL with \(count) certificate\(count == 1 ? "" : "s")"
        } else if let rical = signedRical?.rical {
            let count = rical.certificateInfos.count
            return "RICAL with \(count) certificate\(count == 1 ? "" : "s")"
        }
        return ""
    }
    
    var isTestOnly: Bool {
        entry.metadata.testOnly
    }
}

@MainActor
@Observable
class TrustManagerModel {
    let trustManager: TrustEntryBasedTrustManager
    var entries: [TrustEntryInfo]? = nil
    var isLoading: Bool = true
    
    init(trustManager: TrustEntryBasedTrustManager) {
        self.trustManager = trustManager
    }
    
    func refresh() async {
        isLoading = true
        do {
            let rawEntries = try await trustManager.getEntries()
            var list: [TrustEntryInfo] = []
            for entry in rawEntries {
                var signedVical: SignedVical? = nil
                var signedRical: SignedRical? = nil
                if let vicalEntry = entry as? TrustEntryVical {
                    let bytes = vicalEntry.encodedSignedVical.toByteArray(startIndex: 0, endIndex: vicalEntry.encodedSignedVical.size)
                    signedVical = try? await SignedVical.companion.parse(
                        encodedSignedVical: bytes,
                        disableSignatureVerification: true
                    )
                } else if let ricalEntry = entry as? TrustEntryRical {
                    let bytes = ricalEntry.encodedSignedRical.toByteArray(startIndex: 0, endIndex: ricalEntry.encodedSignedRical.size)
                    signedRical = try? await SignedRical.companion.parse(
                        encodedSignedRical: bytes,
                        disableSignatureVerification: true
                    )
                }
                list.append(TrustEntryInfo(entry: entry, signedVical: signedVical, signedRical: signedRical))
            }
            self.entries = list
        } catch {
            print("Error loading entries for trust manager \(trustManager.identifier): \(error)")
            self.entries = []
        }
        isLoading = false
    }
    
    func addX509Cert(certificate: X509Cert, metadata: TrustMetadata) async throws -> TrustEntry {
        guard let mutableTm = trustManager as? TrustManager else {
            throw NSError(domain: "TrustManagerModel", code: 1, userInfo: [NSLocalizedDescriptionKey: "Trust manager is read-only"])
        }
        let entry = try await mutableTm.addX509Cert(certificate: certificate, metadata: metadata)
        await refresh()
        return entry
    }
    
    func addVical(
        encodedSignedVical: Data,
        metadata: TrustMetadata,
        disableSignatureVerification: Bool = false
    ) async throws -> TrustEntry {
        guard let mutableTm = trustManager as? TrustManager else {
            throw NSError(domain: "TrustManagerModel", code: 1, userInfo: [NSLocalizedDescriptionKey: "Trust manager is read-only"])
        }
        let byteString = encodedSignedVical.toByteString()
        if !disableSignatureVerification {
            let byteArray = byteString.toByteArray(startIndex: 0, endIndex: Int32(encodedSignedVical.count))
            try await TrustManagerExtKt.validateSignedVical(
                encodedSignedVical: byteArray
            )
        }
        let entry = try await mutableTm.addVical(
            encodedSignedVical: byteString,
            metadata: metadata
        )
        await refresh()
        return entry
    }
    
    func addRical(
        encodedSignedRical: Data,
        metadata: TrustMetadata,
        disableSignatureVerification: Bool = false
    ) async throws -> TrustEntry {
        guard let mutableTm = trustManager as? TrustManager else {
            throw NSError(domain: "TrustManagerModel", code: 1, userInfo: [NSLocalizedDescriptionKey: "Trust manager is read-only"])
        }
        let byteString = encodedSignedRical.toByteString()
        if !disableSignatureVerification {
            let byteArray = byteString.toByteArray(startIndex: 0, endIndex: Int32(encodedSignedRical.count))
            try await TrustManagerExtKt.validateSignedRical(
                encodedSignedRical: byteArray
            )
        }
        let entry = try await mutableTm.addRical(
            encodedSignedRical: byteString,
            metadata: metadata
        )
        await refresh()
        return entry
    }
    
    func updateMetadata(entry: TrustEntry, metadata: TrustMetadata) async throws {
        guard let mutableTm = trustManager as? TrustManager else {
            throw NSError(domain: "TrustManagerModel", code: 1, userInfo: [NSLocalizedDescriptionKey: "Trust manager is read-only"])
        }
        _ = try await mutableTm.updateMetadata(entry: entry, metadata: metadata)
        await refresh()
    }
    
    func deleteEntry(entry: TrustEntry) async throws {
        guard let mutableTm = trustManager as? TrustManager else {
            throw NSError(domain: "TrustManagerModel", code: 1, userInfo: [NSLocalizedDescriptionKey: "Trust manager is read-only"])
        }
        _ = try await mutableTm.deleteEntry(entry: entry)
        await refresh()
    }
    
    func updateTrustEntry(entry: TrustEntry) async throws -> TrustEntryUpdateResult {
        guard let mutableTm = trustManager as? TrustManager else {
            throw NSError(domain: "TrustManagerModel", code: 1, userInfo: [NSLocalizedDescriptionKey: "Trust manager is read-only"])
        }
        
        if let vicalEntry = entry as? TrustEntryVical {
            let currentBytes = vicalEntry.encodedSignedVical.toByteArray(startIndex: 0, endIndex: vicalEntry.encodedSignedVical.size)
            let currentSignedVical = try await SignedVical.companion.parse(
                encodedSignedVical: currentBytes,
                disableSignatureVerification: true
            )
            guard let urlString = currentSignedVical.vical.vicalUrl,
                  !urlString.trimmingCharacters(in: .whitespaces).isEmpty,
                  let url = URL(string: urlString) else {
                return TrustEntryUpdateResultNoUpdateUrl.shared
            }
            
            var request = URLRequest(url: url)
            request.httpMethod = "GET"
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse,
                  (200...299).contains(httpResponse.statusCode) else {
                let statusCode = (response as? HTTPURLResponse)?.statusCode ?? -1
                throw NSError(domain: "TrustManagerModel", code: 2, userInfo: [NSLocalizedDescriptionKey: "HTTP \(statusCode)"])
            }
            
            let downloadedBytes = data.toByteString().toByteArray(startIndex: 0, endIndex: Int32(data.count))
            
            // Validate signature or verify curve support
            do {
                try await TrustManagerExtKt.validateSignedVical(encodedSignedVical: downloadedBytes)
            } catch {
                if let curve = Self.extractSigningCurve(fromCoseSign1Data: data),
                   !Crypto.shared.supportedCurves.contains(curve) {
                    // Curve is unsupported on this platform (e.g. Brainpool), allow update
                } else {
                    throw error
                }
            }
            
            let downloadedSignedVical = try await SignedVical.companion.parse(
                encodedSignedVical: downloadedBytes,
                disableSignatureVerification: true
            )
            
            let currentIssueId = currentSignedVical.vical.vicalIssueID?.int64Value
            let downloadedIssueId = downloadedSignedVical.vical.vicalIssueID?.int64Value
            
            if let curr = currentIssueId, let down = downloadedIssueId, down <= curr {
                return TrustEntryUpdateResultAlreadyUpToDate(listType: "VICAL")
            } else if currentIssueId == nil && downloadedIssueId == nil && data.toByteString() == vicalEntry.encodedSignedVical {
                return TrustEntryUpdateResultAlreadyUpToDate(listType: "VICAL")
            } else {
                _ = try await mutableTm.updateVical(
                    entry: vicalEntry,
                    encodedSignedVical: data.toByteString()
                )
                await refresh()
                return TrustEntryUpdateResultUpdated(
                    listType: "VICAL",
                    issueId: downloadedSignedVical.vical.vicalIssueID,
                    previousIssueId: currentSignedVical.vical.vicalIssueID,
                    certificateCount: Int32(downloadedSignedVical.vical.certificateInfos.count)
                )
            }
        } else if let ricalEntry = entry as? TrustEntryRical {
            let currentBytes = ricalEntry.encodedSignedRical.toByteArray(startIndex: 0, endIndex: ricalEntry.encodedSignedRical.size)
            let currentSignedRical = try await SignedRical.companion.parse(
                encodedSignedRical: currentBytes,
                disableSignatureVerification: true
            )
            guard let urlString = currentSignedRical.rical.latestRicalUrl,
                  !urlString.trimmingCharacters(in: .whitespaces).isEmpty,
                  let url = URL(string: urlString) else {
                return TrustEntryUpdateResultNoUpdateUrl.shared
            }
            
            var request = URLRequest(url: url)
            request.httpMethod = "GET"
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse,
                  (200...299).contains(httpResponse.statusCode) else {
                let statusCode = (response as? HTTPURLResponse)?.statusCode ?? -1
                throw NSError(domain: "TrustManagerModel", code: 2, userInfo: [NSLocalizedDescriptionKey: "HTTP \(statusCode)"])
            }
            
            let downloadedBytes = data.toByteString().toByteArray(startIndex: 0, endIndex: Int32(data.count))
            
            // Validate signature or verify curve support
            do {
                try await TrustManagerExtKt.validateSignedRical(encodedSignedRical: downloadedBytes)
            } catch {
                if let curve = Self.extractSigningCurve(fromCoseSign1Data: data),
                   !Crypto.shared.supportedCurves.contains(curve) {
                    // Curve is unsupported on this platform (e.g. Brainpool), allow update
                } else {
                    throw error
                }
            }
            
            let downloadedSignedRical = try await SignedRical.companion.parse(
                encodedSignedRical: downloadedBytes,
                disableSignatureVerification: true
            )
            
            let currentId = currentSignedRical.rical.id?.int64Value
            let downloadedId = downloadedSignedRical.rical.id?.int64Value
            
            if let curr = currentId, let down = downloadedId, down <= curr {
                return TrustEntryUpdateResultAlreadyUpToDate(listType: "RICAL")
            } else if currentId == nil && downloadedId == nil && data.toByteString() == ricalEntry.encodedSignedRical {
                return TrustEntryUpdateResultAlreadyUpToDate(listType: "RICAL")
            } else {
                _ = try await mutableTm.updateRical(
                    entry: ricalEntry,
                    encodedSignedRical: data.toByteString()
                )
                await refresh()
                return TrustEntryUpdateResultUpdated(
                    listType: "RICAL",
                    issueId: downloadedSignedRical.rical.id,
                    previousIssueId: currentSignedRical.rical.id,
                    certificateCount: Int32(downloadedSignedRical.rical.certificateInfos.count)
                )
            }
        }
        
        return TrustEntryUpdateResultNoUpdateUrl.shared
    }
    
    func updateAllEntries() async -> Int {
        var updatedCount = 0
        guard let entries = try? await trustManager.getEntries() else { return 0 }
        for entry in entries {
            if let result = try? await updateTrustEntry(entry: entry),
               result is TrustEntryUpdateResultUpdated {
                updatedCount += 1
            }
        }
        return updatedCount
    }

    static func extractSigningCurve(fromCoseSign1Data data: Data) -> EcCurve? {
        let byteString = data.toByteString()
        let byteArray = byteString.toByteArray(startIndex: 0, endIndex: Int32(data.count))
        guard let cbor = try? Cbor.shared.decode(encodedCbor: byteArray) else {
            return nil
        }
        let coseSign1 = CoseSign1.companion.fromDataItem(dataItem: cbor)
        
        // Check leaf certificate in x5chain (unprotected in VICAL, protected in RICAL)
        let x5chainItem = coseSign1.unprotectedHeaders[CoseNumberLabel(number: Cose.shared.COSE_LABEL_X5CHAIN)]
            ?? coseSign1.protectedHeaders[CoseNumberLabel(number: Cose.shared.COSE_LABEL_X5CHAIN)]
        if let certChain = x5chainItem?.asX509CertChain,
           let firstCert = certChain.certificates.first {
            return firstCert.ecPublicKey.curve
        }
        
        // Fallback to algorithm in protected header
        if let algItem = coseSign1.protectedHeaders[CoseNumberLabel(number: Cose.shared.COSE_LABEL_ALG)] {
            let alg = Algorithm.companion.fromCoseAlgorithmIdentifier(coseAlgorithmIdentifier: Int32(algItem.asNumber))
            return alg.curve
        }
        
        return nil
    }

    static func curveDisplayName(_ curve: EcCurve) -> String {
        return curve.jwkName
    }
}
