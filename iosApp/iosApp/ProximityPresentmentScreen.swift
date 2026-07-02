import SwiftUI
import CoreImage.CIFilterBuiltins
import Multipaz

struct ProximityPresentmentScreen: View {
    @Environment(ViewModel.self) private var viewModel
    let documentId: String
    
    @State private var originalBrightness: CGFloat = 0.5
    @State private var cancelAction: (() -> Void)? = nil
    @State private var isDismissing = false
    
    var body: some View {
        VStack {
            MdocProximityQrPresentment(
                source: viewModel.getSource(),
                prepareSettings: { generateQrCode in
                    // Automatically trigger the QR generation when this view is rendered
                    Color.clear
                        .onAppear {
                            guard !isDismissing else { return }
                            let bleUuid = UUID.companion.randomUUID(random: KotlinRandom.companion)
                            let connectionMethods = [
                                MdocConnectionMethodBle(
                                    supportsPeripheralServerMode: true,
                                    supportsCentralClientMode: false,
                                    peripheralServerModeUuid: bleUuid,
                                    centralClientModeUuid: nil,
                                    peripheralServerModePsm: nil,
                                    peripheralServerModeMacAddress: nil
                                )
                            ]
                            let settings = MdocProximityQrSettings(
                                availableConnectionMethods: connectionMethods,
                                createTransportOptions: MdocTransportOptions(
                                    bleUseL2CAP: false,
                                    bleUseL2CAPInEngagement: true
                                )
                            )
                            generateQrCode(settings)
                        }
                },
                showQrCode: { uri, cancel in
                    VStack(spacing: 24) {
                        Text("Show code to verifier")
                            .font(.title2.bold())
                            .foregroundColor(.primary)
                            .padding(.top, 20)
                        
                        Text("Your personal info won't be shared until the verifier scans this QR code. You do not have to hand your phone to anyone.")
                            .font(.body)
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 24)
                        
                        Spacer()
                        
                        // Beautiful QR code container with shadows
                        VStack {
                            Image(uiImage: generateQrCode(uri: uri))
                                .resizable()
                                .interpolation(.none)
                                .aspectRatio(1, contentMode: .fit)
                                .frame(width: 260, height: 260)
                                .padding(16)
                                .background(Color.white)
                                .cornerRadius(24)
                                .shadow(color: Color.black.opacity(0.1), radius: 20, x: 0, y: 10)
                        }
                        .padding()
                        
                        Spacer()
                        
                        Button(role: .cancel) {
                            self.isDismissing = true
                            cancel()
                            viewModel.popWithoutAnimation()
                        } label: {
                            Text("Cancel")
                                .font(.headline)
                                .frame(maxWidth: .infinity)
                                .padding()
                                .foregroundColor(.white)
                                .background(Color.red)
                                .cornerRadius(16)
                        }
                        .padding(.horizontal, 24)
                        .padding(.bottom, 20)
                    }
                    .onAppear {
                        // Store the cancel action for lifecycle cleanup
                        self.cancelAction = cancel
                        // Set brightness to max
                        self.originalBrightness = UIScreen.main.brightness
                        UIScreen.main.brightness = 1.0
                    }
                    .onDisappear {
                        // Restore brightness when no longer displaying QR code
                        UIScreen.main.brightness = self.originalBrightness
                    }
                },
                showTransacting: { cancel in
                    VStack(spacing: 24) {
                        Spacer()
                        
                        ProgressView()
                            .scaleEffect(1.5)
                            .progressViewStyle(CircularProgressViewStyle(tint: .accentColor))
                        
                        Text("Sharing data...")
                            .font(.headline)
                            .foregroundColor(.primary)
                        
                        Text("Connecting to the verifier's reader device.")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 32)
                        
                        Spacer()
                        
                        Button(role: .cancel) {
                            self.isDismissing = true
                            cancel()
                            viewModel.popWithoutAnimation()
                        } label: {
                            Text("Cancel")
                                .font(.headline)
                                .frame(maxWidth: .infinity)
                                .padding()
                                .foregroundColor(.white)
                                .background(Color.secondary)
                                .cornerRadius(16)
                        }
                        .padding(.horizontal, 24)
                        .padding(.bottom, 20)
                    }
                    .onAppear {
                        self.cancelAction = cancel
                        // Restore screen brightness to normal during data transfer
                        UIScreen.main.brightness = self.originalBrightness
                    }
                },
                showCompleted: { error, reset in
                    VStack(spacing: 24) {
                        Spacer()
                        
                        if let error = error {
                            Image(systemName: "xmark.circle.fill")
                                .font(.system(size: 64))
                                .foregroundColor(.red)
                            
                            Text("Something went wrong")
                                .font(.headline)
                                .foregroundColor(.primary)
                            
                            Text(error.localizedDescription)
                                .font(.subheadline)
                                .foregroundColor(.secondary)
                                .multilineTextAlignment(.center)
                                .padding(.horizontal, 32)
                        } else {
                            Image(systemName: "checkmark.circle.fill")
                                .font(.system(size: 64))
                                .foregroundColor(.green)
                            
                            Text("The data was shared")
                                .font(.headline)
                                .foregroundColor(.primary)
                        }
                        
                        Spacer()
                    }
                    .onAppear {
                        self.isDismissing = true
                        // Clear cancel action as the transaction is finished
                        self.cancelAction = nil
                        // Pop screen back after 2 seconds
                        Task {
                            try? await Task.sleep(nanoseconds: 2_000_000_000)
                            viewModel.popWithoutAnimation()
                        }
                    }
                }
            )
        }
        .navigationTitle("")
        .navigationBarTitleDisplayMode(.inline)
        .onDisappear {
            self.isDismissing = true
            // Guarantee cancellation if navigating away early
            if let cancel = self.cancelAction {
                cancel()
            }
        }
    }
    
    private func generateQrCode(uri: String) -> UIImage {
        let data = Data(uri.utf8)
        let filter = CIFilter.qrCodeGenerator()
        filter.setValue(data, forKey: "inputMessage")
        filter.setValue("H", forKey: "inputCorrectionLevel")
        
        if let outputImage = filter.outputImage {
            // Apply scale transform for a crisp QR image
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
