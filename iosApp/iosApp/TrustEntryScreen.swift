import SwiftUI
import Multipaz

struct TrustEntryScreen: View {
    @Environment(ViewModel.self) private var viewModel
    @Environment(\.dismiss) private var dismiss
    
    let trustManagerId: String
    let trustEntryId: String
    let justImported: Bool
    
    @State private var showDeleteConfirmation = false
    @State private var isCheckingForUpdate = false
    @State private var updateAlertTitle: String? = nil
    @State private var updateAlertMessage: String? = nil
    @State private var showUpdateAlert = false
    
    private var model: TrustManagerModel? {
        viewModel.getTrustManagerModel(identifier: trustManagerId)
    }
    
    private var entryInfo: TrustEntryInfo? {
        model?.entries?.first(where: { $0.id == trustEntryId })
    }
    
    private var isMutable: Bool {
        model?.trustManager is TrustManager
    }
    
    private var screenTitle: String {
        guard let entry = entryInfo?.entry else { return "Trust Entry" }
        if entry is TrustEntryX509Cert {
            return "Certificate Details"
        } else if entry is TrustEntryVical {
            return "VICAL Details"
        } else if entry is TrustEntryRical {
            return "RICAL Details"
        }
        return "Trust Entry"
    }
    
    private var updateUrl: String? {
        if let vical = entryInfo?.signedVical?.vical {
            return vical.vicalUrl
        } else if let rical = entryInfo?.signedRical?.rical {
            return rical.latestRicalUrl
        }
        return nil
    }
    
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                if let info = entryInfo {
                    if justImported {
                        Text("This trust entry was imported by you. Make sure you trust the source where you obtained it from.")
                            .font(.footnote)
                            .foregroundColor(.secondary)
                    }
                    
                    VStack(alignment: .center, spacing: 12) {
                        TrustEntryIconView(entryInfo: info, size: 80)
                            .frame(maxWidth: .infinity)
                        
                        Text(info.displayName)
                            .font(.title2)
                            .bold()
                            .multilineTextAlignment(.center)
                    }
                    .padding(.vertical, 8)
                    
                    SegmentedItemList {
                        SegmentedItemHeadingAndText(
                            heading: "Test only",
                            text: info.isTestOnly ? "Yes" : "No"
                        )
                    }
                    
                    if let certEntry = info.entry as? TrustEntryX509Cert {
                        X509CertViewer(certificate: certEntry.certificate)
                    } else if let vical = info.signedVical?.vical {
                        vicalDetailsSection(vical: vical, providerChain: info.signedVical!.vicalProviderCertificateChain)
                        vicalCertificatesSection(vical: vical)
                    } else if let rical = info.signedRical?.rical {
                        ricalDetailsSection(rical: rical, providerChain: info.signedRical!.ricalProviderCertificateChain)
                        ricalCertificatesSection(rical: rical)
                    }
                } else {
                    Text("Trust entry details not found")
                        .foregroundColor(.secondary)
                        .padding()
                }
            }
            .padding()
        }
        .background(Color(UIColor.systemGroupedBackground))
        .navigationTitle(screenTitle)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if isMutable, let info = entryInfo {
                ToolbarItemGroup(placement: .navigationBarTrailing) {
                    if let url = updateUrl, !url.trimmingCharacters(in: .whitespaces).isEmpty {
                        if isCheckingForUpdate {
                            ProgressView()
                                .frame(width: 24, height: 24)
                        } else {
                            Button {
                                checkForUpdate(entry: info.entry)
                            } label: {
                                Image(systemName: "arrow.clockwise")
                            }
                            .disabled(isCheckingForUpdate)
                        }
                    }
                    
                    Button {
                        viewModel.push(.trustEntryEdit(trustManagerId: trustManagerId, trustEntryId: trustEntryId))
                    } label: {
                        Image(systemName: "pencil")
                    }
                    
                    Button(role: .destructive) {
                        showDeleteConfirmation = true
                    } label: {
                        Image(systemName: "trash")
                    }
                }
            }
        }
        .alert("Delete trust entry?", isPresented: $showDeleteConfirmation) {
            Button("Delete", role: .destructive) {
                deleteCurrentEntry()
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Are you sure you want to delete this trust entry? This action cannot be undone.")
        }
        .alert(updateAlertTitle ?? "Update", isPresented: $showUpdateAlert) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(updateAlertMessage ?? "")
        }
    }
    
    @ViewBuilder
    private func vicalDetailsSection(vical: Vical, providerChain: X509CertChain) -> some View {
        SegmentedItemList(title: "VICAL data") {
            SegmentedItemHeadingAndText(heading: "Version", text: vical.version)
            SegmentedItemHeadingAndText(heading: "Provider", text: vical.vicalProvider)
            if let issueId = vical.vicalIssueID {
                SegmentedItemHeadingAndText(heading: "Issue ID", text: "\(issueId)")
            }
            SegmentedItemHeadingAndDate(
                heading: "Issued at",
                date: Date(timeIntervalSince1970: Double(vical.date.epochSeconds))
            )
            if let nextUpdate = vical.nextUpdate {
                SegmentedItemHeadingAndDate(
                    heading: "Next update",
                    date: Date(timeIntervalSince1970: Double(nextUpdate.epochSeconds))
                )
            }
            if let validUntil = vical.notAfter {
                SegmentedItemHeadingAndDate(
                    heading: "Valid until",
                    date: Date(timeIntervalSince1970: Double(validUntil.epochSeconds))
                )
            }
            if let url = vical.vicalUrl {
                SegmentedItemHeadingAndText(heading: "Update URL", text: url)
            }
            SegmentedItemHeadingAndText(
                heading: "Signer",
                text: "Click to view certificate chain",
                showChevron: true
            ).onTapGesture {
                viewModel.push(.certificateViewer(certChain: providerChain))
            }
            if !vical.extensions.isEmpty {
                let extensionsStr = vical.extensions.map { key, value in
                    "\(key): \(Cbor.shared.toDiagnostics(item: value, options: [.prettyPrint]))"
                }.joined(separator: "\n")
                SegmentedItemHeadingAndText(heading: "Extensions", text: extensionsStr)
            }
        }
    }
    
    @ViewBuilder
    private func vicalCertificatesSection(vical: Vical) -> some View {
        SegmentedItemList(title: "Certificates") {
            ForEach(0..<vical.certificateInfos.count, id: \.self) { index in
                let certInfo = vical.certificateInfos[index]
                let name = certInfo.certificate.subject.components["2.5.4.3"]?.value ?? certInfo.certificate.subject.name
                SegmentedItemText(
                    text: name,
                    showChevron: true,
                    secondary: "Certificate",
                    image: { DeterministicAvatarView(name: name, size: 36) }
                ).onTapGesture {
                    viewModel.push(.trustEntryVicalEntry(
                        trustManagerId: trustManagerId,
                        vicalTrustEntryId: trustEntryId,
                        certNum: index
                    ))
                }
            }
        }
    }
    
    @ViewBuilder
    private func ricalDetailsSection(rical: Rical, providerChain: X509CertChain) -> some View {
        SegmentedItemList(title: "RICAL data") {
            SegmentedItemHeadingAndText(heading: "Type", text: rical.type)
            SegmentedItemHeadingAndText(heading: "Version", text: rical.version)
            SegmentedItemHeadingAndText(heading: "Provider", text: rical.provider)
            if let id = rical.id {
                SegmentedItemHeadingAndText(heading: "ID", text: "\(id)")
            }
            SegmentedItemHeadingAndDate(
                heading: "Issued at",
                date: Date(timeIntervalSince1970: Double(rical.date.epochSeconds))
            )
            if let nextUpdate = rical.nextUpdate {
                SegmentedItemHeadingAndDate(
                    heading: "Next update",
                    date: Date(timeIntervalSince1970: Double(nextUpdate.epochSeconds))
                )
            }
            if let validUntil = rical.notAfter {
                SegmentedItemHeadingAndDate(
                    heading: "Valid until",
                    date: Date(timeIntervalSince1970: Double(validUntil.epochSeconds))
                )
            }
            if let url = rical.latestRicalUrl {
                SegmentedItemHeadingAndText(heading: "Update URL", text: url)
            }
            SegmentedItemHeadingAndText(
                heading: "Signer",
                text: "Click to view certificate chain",
                showChevron: true
            ).onTapGesture {
                viewModel.push(.certificateViewer(certChain: providerChain))
            }
            if !rical.extensions.isEmpty {
                let extensionsStr = rical.extensions.map { key, value in
                    "\(key): \(Cbor.shared.toDiagnostics(item: value, options: [.prettyPrint]))"
                }.joined(separator: "\n")
                SegmentedItemHeadingAndText(heading: "Extensions", text: extensionsStr)
            }
        }
    }
    
    @ViewBuilder
    private func ricalCertificatesSection(rical: Rical) -> some View {
        SegmentedItemList(title: "Certificates") {
            ForEach(0..<rical.certificateInfos.count, id: \.self) { index in
                let certInfo = rical.certificateInfos[index]
                let name = certInfo.certificate.subject.components["2.5.4.3"]?.value ?? certInfo.certificate.subject.name
                SegmentedItemText(
                    text: name,
                    showChevron: true,
                    secondary: "Certificate",
                    image: { DeterministicAvatarView(name: name, size: 36) }
                ).onTapGesture {
                    viewModel.push(.trustEntryRicalEntry(
                        trustManagerId: trustManagerId,
                        ricalTrustEntryId: trustEntryId,
                        certNum: index
                    ))
                }
            }
        }
    }
    
    private func checkForUpdate(entry: TrustEntry) {
        guard let model = model else { return }
        isCheckingForUpdate = true
        Task {
            do {
                let result = try await model.updateTrustEntry(entry: entry)
                await MainActor.run {
                    self.isCheckingForUpdate = false
                    if let updated = result as? TrustEntryUpdateResultUpdated {
                        self.updateAlertTitle = "Trust list updated"
                        if let issueId = updated.issueId {
                            self.updateAlertMessage = "Successfully updated \(updated.listType) to issue ID \(issueId) with \(updated.certificateCount) certificates."
                        } else {
                            self.updateAlertMessage = "Successfully updated \(updated.listType) with \(updated.certificateCount) certificates."
                        }
                        self.showUpdateAlert = true
                    } else if let alreadyLatest = result as? TrustEntryUpdateResultAlreadyUpToDate {
                        self.updateAlertTitle = "Trust list is up to date"
                        self.updateAlertMessage = "The \(alreadyLatest.listType) is already at the latest version."
                        self.showUpdateAlert = true
                    }
                }
            } catch {
                await MainActor.run {
                    self.isCheckingForUpdate = false
                    self.updateAlertTitle = "Update failed"
                    self.updateAlertMessage = error.localizedDescription
                    self.showUpdateAlert = true
                }
            }
        }
    }
    
    private func deleteCurrentEntry() {
        guard let model = model, let info = entryInfo else { return }
        Task {
            do {
                try await model.deleteEntry(entry: info.entry)
                await MainActor.run {
                    dismiss()
                }
            } catch {
                print("Failed to delete entry: \(error)")
            }
        }
    }
}
