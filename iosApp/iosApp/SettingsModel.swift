import SwiftUI
import Foundation

@Observable
class SettingsModel {
    private let defaults = UserDefaults.standard
    
    // MARK: - Published Properties
    
    var explicitlySignedOut: Bool {
        didSet {
            defaults.set(explicitlySignedOut, forKey: "explicitlySignedOut")
        }
    }
    
    var devMode: Bool {
        didSet {
            defaults.set(devMode, forKey: "devMode")
        }
    }
    
    var provisioningServerUrl: String {
        didSet {
            defaults.set(provisioningServerUrl, forKey: "provisioningServerUrl")
        }
    }
    
    // MARK: - Initialization
    
    init() {
        // UserDefaults synchronously loads data, so no async factory method is needed.
        self.explicitlySignedOut = defaults.bool(forKey: "explicitlySignedOut")
        self.devMode = defaults.bool(forKey: "devMode")
        self.provisioningServerUrl = defaults.string(forKey: "provisioningServerUrl") ?? "https://issuer.multipaz.org/issuer"
    }
    
    // MARK: - Methods
    
    func resetSettings() {
        explicitlySignedOut = false
        devMode = false
        provisioningServerUrl = "https://issuer.multipaz.org/issuer"
    }
    
}
