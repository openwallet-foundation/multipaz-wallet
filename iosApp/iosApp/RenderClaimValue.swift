import SwiftUI
import Multipaz

struct RenderClaimValue: View {
    let claim: Claim

    var body: some View {
        if claim.attribute?.type is DocumentAttributeType.Picture {
            if let uiImage = decodeImage() {
                Image(uiImage: uiImage)
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(maxWidth: 150, maxHeight: 150)
            } else {
                Text("Error decoding image")
                    .font(.system(size: 15))
                    .foregroundColor(.red)
            }
        } else {
            // Try claim.render() or claim.render(timeZone:)
            Text(claim.render(timeZone: Multipaz.TimeZone.Companion.shared.currentSystemDefault()))
                .font(.system(size: 15))
        }
    }

    private func decodeImage() -> UIImage? {
        var data: Data? = nil
        if let mdocClaim = claim as? MdocClaim {
            var value = mdocClaim.value
            if let tagged = value as? Tagged {
                value = tagged.taggedItem
            }
            if let bstr = value as? Bstr {
                data = bstr.asBstr.toNSData() as Data
            }
        } else if let jsonClaim = claim as? JsonClaim {
            if let content = jsonClaim.value.jsonPrimitive.content as? String {
                data = decodeBase64Url(content)
            }
        }
        
        if let data = data {
            return UIImage(data: data)
        }
        return nil
    }

    private func decodeBase64Url(_ string: String) -> Data? {
        var base64 = string
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        
        let mod = base64.count % 4
        if mod > 0 {
            base64 += String(repeating: "=", count: 4 - mod)
        }
        
        return Data(base64Encoded: base64)
    }
}
