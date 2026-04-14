import Foundation
import Security

enum GoogleDriveError: Error {
    case invalidResponse
    case fileMetadataCreationFailed
    case uploadFailed
}

struct GoogleDriveFileListResponse: Codable {
    struct DriveFile: Codable {
        let id: String
        let name: String
    }
    let files: [DriveFile]
}

class GoogleDriveManager {
    let accessToken: String
    private let session = URLSession.shared
    private let fileName = "WalletServerEncryptionKey"
    
    init(accessToken: String) {
        self.accessToken = accessToken
    }
    
    func retrieveOrCreateEncryptionKey() async throws -> Data {
        // 1. Search for the key in the hidden App Data folder
        if let existingFileId = try await searchForFile() {
            // 2a. File exists, download and verify it
            let data = try await downloadFile(id: existingFileId)
            if data.count == 32 {
                print("Found existing 32-byte encryption key in Drive.")
                return data
            } else {
                print("Existing key was not 32 bytes (size: \(data.count)). Regenerating.")
                let newKey = generateSecureKey()
                try await uploadMedia(to: existingFileId, data: newKey)
                return newKey
            }
        }
        
        // 2b. File doesn't exist, generate and create it
        print("Creating new 32-byte encryption key.")
        let newKey = generateSecureKey()
        let newFileId = try await createFileMetadata()
        try await uploadMedia(to: newFileId, data: newKey)
        
        return newKey
    }
    
    private func searchForFile() async throws -> String? {
        var components = URLComponents(string: "https://www.googleapis.com/drive/v3/files")!
        components.queryItems = [
            URLQueryItem(name: "spaces", value: "appDataFolder"),
            URLQueryItem(name: "q", value: "name='\(fileName)'"),
            URLQueryItem(name: "fields", value: "files(id,name)")
        ]
        
        var request = URLRequest(url: components.url!)
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        
        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
            throw GoogleDriveError.invalidResponse
        }
        
        let listResponse = try JSONDecoder().decode(GoogleDriveFileListResponse.self, from: data)
        return listResponse.files.first?.id
    }
    
    private func downloadFile(id: String) async throws -> Data {
        let url = URL(string: "https://www.googleapis.com/drive/v3/files/\(id)?alt=media")!
        var request = URLRequest(url: url)
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        
        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
            throw GoogleDriveError.invalidResponse
        }
        
        return data
    }
    
    // Step 1 of upload: Create the file metadata pointing to appDataFolder
    private func createFileMetadata() async throws -> String {
        let url = URL(string: "https://www.googleapis.com/drive/v3/files")!
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        let body: [String: Any] = [
            "name": fileName,
            "parents": ["appDataFolder"]
        ]
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        
        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
            throw GoogleDriveError.fileMetadataCreationFailed
        }
        
        struct FileResponse: Codable { let id: String }
        let fileResponse = try JSONDecoder().decode(FileResponse.self, from: data)
        return fileResponse.id
    }
    
    // Step 2 of upload: Push the actual 32 bytes to the file ID
    private func uploadMedia(to fileId: String, data: Data) async throws {
        let url = URL(string: "https://www.googleapis.com/upload/drive/v3/files/\(fileId)?uploadType=media")!
        var request = URLRequest(url: url)
        request.httpMethod = "PATCH" // PATCH is used for media uploads in Drive v3
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue("application/octet-stream", forHTTPHeaderField: "Content-Type")
        request.httpBody = data
        
        let (_, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
            throw GoogleDriveError.uploadFailed
        }
    }
    
    private func generateSecureKey() -> Data {
        var key = Data(count: 32)
        let result = key.withUnsafeMutableBytes {
            SecRandomCopyBytes(kSecRandomDefault, 32, $0.baseAddress!)
        }
        if result != errSecSuccess {
            // Fallback if SecRandom fails
            return Data((0..<32).map { _ in UInt8.random(in: 0...255) })
        }
        return key
    }
}
