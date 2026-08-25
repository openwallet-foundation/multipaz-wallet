import SwiftUI
import Multipaz
import PhotosUI

struct TrustEntryEditScreen: View {
    @Environment(ViewModel.self) private var viewModel
    @Environment(\.dismiss) private var dismiss
    
    let trustManagerId: String
    let trustEntryId: String
    
    @State private var displayName: String = ""
    @State private var isTestOnly: Bool = false
    @State private var customIconData: Data? = nil
    @State private var selectedPhotoItem: PhotosPickerItem? = nil
    
    @State private var initialDisplayName: String = ""
    @State private var initialTestOnly: Bool = false
    @State private var initialCustomIconData: Data? = nil
    @State private var isInitialized: Bool = false
    
    @State private var showDiscardAlert = false
    @State private var isSaving = false
    
    private var model: TrustManagerModel? {
        viewModel.getTrustManagerModel(identifier: trustManagerId)
    }
    
    private var entryInfo: TrustEntryInfo? {
        model?.entries?.first(where: { $0.id == trustEntryId })
    }
    
    private var hasChanges: Bool {
        displayName != initialDisplayName ||
        isTestOnly != initialTestOnly ||
        customIconData != initialCustomIconData
    }
    
    private var entryTypeDescription: String {
        guard let entry = entryInfo?.entry else { return "entry" }
        if entry is TrustEntryX509Cert {
            return "certificate"
        } else if entry is TrustEntryVical {
            return "VICAL"
        } else if entry is TrustEntryRical {
            return "RICAL"
        }
        return "entry"
    }
    
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                if let info = entryInfo {
                    VStack(alignment: .center, spacing: 12) {
                        if let iconData = customIconData, let uiImage = UIImage(data: iconData) {
                            Image(uiImage: uiImage)
                                .resizable()
                                .scaledToFit()
                                .frame(width: 120, height: 120)
                                .clipShape(RoundedRectangle(cornerRadius: 18))
                        } else {
                            TrustEntryIconView(entryInfo: info, size: 120)
                        }
                        
                        HStack(spacing: 16) {
                            PhotosPicker(selection: $selectedPhotoItem, matching: .images) {
                                Label("Change", systemImage: "pencil")
                            }
                            
                            if customIconData != nil {
                                Button(role: .destructive) {
                                    customIconData = nil
                                } label: {
                                    Label("Remove", systemImage: "trash")
                                }
                            }
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
                    
                    FloatingItemList {
                        FloatingItemContainer {
                            VStack(alignment: .leading, spacing: 6) {
                                Text("Display name")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                                TextField(info.fallbackName, text: $displayName)
                                    .textFieldStyle(.plain)
                            }
                            .padding(.vertical, 4)
                        }
                        
                        FloatingItemContainer {
                            Toggle("Test-only \(entryTypeDescription)", isOn: $isTestOnly)
                        }
                    }
                } else {
                    Text("Trust entry not found")
                        .foregroundColor(.secondary)
                        .padding()
                }
            }
            .padding()
        }
        .navigationTitle("Edit trust entry")
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(hasChanges)
        .toolbar {
            if hasChanges {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") {
                        showDiscardAlert = true
                    }
                }
            }
            ToolbarItem(placement: .navigationBarTrailing) {
                Button("Save") {
                    saveChanges()
                }
                .bold()
                .disabled(!hasChanges || isSaving)
            }
        }
        .alert("Discard changes?", isPresented: $showDiscardAlert) {
            Button("Discard", role: .destructive) {
                dismiss()
            }
            Button("Keep editing", role: .cancel) {}
        } message: {
            Text("You have unsaved changes. Are you sure you want to discard them?")
        }
        .onChange(of: selectedPhotoItem) { _, newItem in
            Task {
                guard let item = newItem else { return }
                if let data = try? await item.loadTransferable(type: Data.self),
                   let uiImage = UIImage(data: data) {
                    let squareImage = cropToSquare(image: uiImage, targetSize: 256)
                    if let pngData = squareImage.pngData() {
                        await MainActor.run {
                            self.customIconData = pngData
                        }
                    }
                }
            }
        }
        .onAppear {
            if !isInitialized, let info = entryInfo {
                displayName = info.entry.metadata.displayName ?? ""
                isTestOnly = info.entry.metadata.testOnly
                if let displayIcon = info.entry.metadata.displayIcon {
                    customIconData = displayIcon.toNSData() as Data
                }
                initialDisplayName = displayName
                initialTestOnly = isTestOnly
                initialCustomIconData = customIconData
                isInitialized = true
            }
        }
    }
    
    private func saveChanges() {
        guard let model = model, let info = entryInfo else { return }
        isSaving = true
        Task {
            do {
                let trimmedName = displayName.trimmingCharacters(in: .whitespaces)
                let newMetadata = TrustMetadata(
                    displayName: trimmedName.isEmpty ? nil : trimmedName,
                    displayIcon: customIconData?.toByteString(),
                    displayIconUrl: info.entry.metadata.displayIconUrl,
                    privacyPolicyUrl: info.entry.metadata.privacyPolicyUrl,
                    disclaimer: info.entry.metadata.disclaimer,
                    testOnly: isTestOnly,
                    extensions: info.entry.metadata.extensions
                )
                try await model.updateMetadata(entry: info.entry, metadata: newMetadata)
                await MainActor.run {
                    isSaving = false
                    dismiss()
                }
            } catch {
                print("Failed to save trust metadata: \(error)")
                await MainActor.run {
                    isSaving = false
                }
            }
        }
    }
    
    private func cropToSquare(image: UIImage, targetSize: CGFloat) -> UIImage {
        let minDimension = min(image.size.width, image.size.height)
        let x = (image.size.width - minDimension) / 2.0
        let y = (image.size.height - minDimension) / 2.0
        let cropRect = CGRect(x: x, y: y, width: minDimension, height: minDimension)
        
        guard let cgImage = image.cgImage?.cropping(to: cropRect) else { return image }
        let cropped = UIImage(cgImage: cgImage, scale: image.scale, orientation: image.imageOrientation)
        
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: targetSize, height: targetSize))
        return renderer.image { _ in
            cropped.draw(in: CGRect(x: 0, y: 0, width: targetSize, height: targetSize))
        }
    }
}
