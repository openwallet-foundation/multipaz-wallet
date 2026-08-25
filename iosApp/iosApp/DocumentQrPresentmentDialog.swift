import SwiftUI
import CoreImage.CIFilterBuiltins
import Multipaz

struct DocumentQrPresentmentDialog: View {
    @Environment(ViewModel.self) private var viewModel
    let documentId: String
    let onDismissed: () -> Void
    let onTransactionUnderway: () -> Void

    @State private var originalBrightness: CGFloat = 0.5

    var body: some View {
        ZStack {
            Color.black.opacity(0.4)
                .ignoresSafeArea()
                .onTapGesture {
                    viewModel.proximityPresentmentModel.cancel()
                    onDismissed()
                }

            VStack(spacing: 20) {
                Text("Show code to verifier")
                    .font(.title3.bold())
                    .foregroundColor(.primary)
                    .multilineTextAlignment(.center)

                Text("Your personal info won't be shared until the verifier scans this QR code. You do not have to hand your phone to anyone.")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)

                ZStack {
                    RoundedRectangle(cornerRadius: 16)
                        .fill(Color.white)
                        .shadow(color: Color.black.opacity(0.08), radius: 10, x: 0, y: 4)

                    if let qrCode = viewModel.proximityPresentmentModel.qrCodeToShow {
                        Image(uiImage: generateQrCode(uri: qrCode))
                            .resizable()
                            .interpolation(.none)
                            .aspectRatio(1, contentMode: .fit)
                            .padding(12)
                    } else {
                        ProgressView()
                            .scaleEffect(1.2)
                    }
                }
                .frame(width: 240, height: 240)

                Button(role: .cancel) {
                    viewModel.proximityPresentmentModel.cancel()
                    onDismissed()
                } label: {
                    Text("Cancel")
                        .font(.headline)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .foregroundColor(.primary)
                        .background(Color(uiColor: .secondarySystemBackground))
                        .cornerRadius(12)
                }
            }
            .padding(24)
            .background(
                RoundedRectangle(cornerRadius: 24)
                    .fill(Color(uiColor: .systemBackground))
                    .shadow(color: Color.black.opacity(0.15), radius: 24, x: 0, y: 8)
            )
            .padding(.horizontal, 24)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .ignoresSafeArea()
        .onAppear {
            self.originalBrightness = UIScreen.main.brightness
            ScreenBrightnessHelper.setBrightness(to: 1.0)

            Task {
                let document = try? await viewModel.documentStore.lookupDocument(identifier: documentId)
                viewModel.proximityPresentmentModel.start(
                    document: document,
                    source: viewModel.getSource(),
                    onConnected: {
                        onTransactionUnderway()
                    }
                )
            }
        }
        .onDisappear {
            ScreenBrightnessHelper.setBrightness(to: self.originalBrightness)
        }
    }

    private func generateQrCode(uri: String) -> UIImage {
        let data = Data(uri.utf8)
        let filter = CIFilter.qrCodeGenerator()
        filter.setValue(data, forKey: "inputMessage")
        filter.setValue("H", forKey: "inputCorrectionLevel")

        if let outputImage = filter.outputImage {
            let scale: CGFloat = 10.0
            let transform = CGAffineTransform(scaleX: scale, y: scale)
            let scaledImage = outputImage.transformed(by: transform)

            let context = CIContext()
            if let cgImage = context.createCGImage(scaledImage, from: scaledImage.extent) {
                return UIImage(cgImage: cgImage)
            }
        }
        return UIImage()
    }
}

@MainActor
private final class ScreenBrightnessHelper {
    private static var animationTask: Task<Void, Never>? = nil

    static func setBrightness(to targetBrightness: CGFloat, duration: TimeInterval = 0.35) {
        animationTask?.cancel()

        let startBrightness = UIScreen.main.brightness
        if startBrightness == targetBrightness { return }

        let startTime = Date().timeIntervalSinceReferenceDate

        animationTask = Task { @MainActor in
            while !Task.isCancelled {
                let elapsed = Date().timeIntervalSinceReferenceDate - startTime
                let progress = min(max(CGFloat(elapsed / duration), 0.0), 1.0)

                let easedProgress = progress < 0.5
                    ? 2 * progress * progress
                    : 1 - pow(-2 * progress + 2, 2) / 2

                let current = startBrightness + (targetBrightness - startBrightness) * easedProgress
                UIScreen.main.brightness = current

                if progress >= 1.0 {
                    break
                }

                try? await Task.sleep(nanoseconds: 16_000_000)
            }
        }
    }
}
