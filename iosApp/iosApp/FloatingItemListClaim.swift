import SwiftUI
import Multipaz

struct FloatingItemListClaim: View {
    let claim: Claim
    var heading: String? = nil
    var timeZone: Multipaz.TimeZone = Multipaz.TimeZone.Companion.shared.currentSystemDefault()

    private var resolvedHeading: String {
        heading ?? claim.displayName
    }

    var body: some View {
        if claim.attribute?.type is DocumentAttributeType.Picture {
            if let uiImage = decodeImage(claim: claim) {
                FloatingItemHeadingAndContent(
                    heading: resolvedHeading,
                    content: {
                        Image(uiImage: uiImage)
                            .resizable()
                            .aspectRatio(contentMode: .fit)
                            .frame(maxWidth: 250, maxHeight: 250)
                    }
                )
            } else {
                FloatingItemHeadingAndText(
                    heading: resolvedHeading,
                    text: "Error decoding image"
                )
            }
        } else {
            FloatingItemHeadingAndText(
                heading: resolvedHeading,
                text: claim.render(timeZone: timeZone)
            )
        }
    }

    private func decodeImage(claim: Claim) -> UIImage? {
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
