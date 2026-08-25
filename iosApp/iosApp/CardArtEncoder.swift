import UIKit
import Multipaz

/// Encodes card art image data into JPEG format such that the resulting output fits within `maxBytes`.
func encodeCardArt(
    cardArt: ByteString,
    maxBytes: Int
) -> ByteString? {
    if maxBytes <= 0 || cardArt.size == 0 {
        return nil
    }

    let data = cardArt.toNSData() as Data

    // If it's already a JPEG and fits within maxBytes, return it as-is to preserve maximum quality.
    if isJpeg(data: data) && data.count <= maxBytes {
        return cardArt
    }

    guard let bitmap = UIImage(data: data) else {
        return nil
    }

    var currentBitmap = bitmap
    while currentBitmap.size.width >= 1 && currentBitmap.size.height >= 1 {
        if let jpegData = findBestQualityJpeg(image: currentBitmap, maxBytes: maxBytes) {
            return jpegData.toByteString()
        }

        // Downscale the image resolution
        let nextWidth = currentBitmap.size.width * 0.8
        let nextHeight = currentBitmap.size.height * 0.8
        if nextWidth < 1 || nextHeight < 1 {
            break
        }

        let newSize = CGSize(width: nextWidth, height: nextHeight)
        let renderer = UIGraphicsImageRenderer(size: newSize)
        let scaled = renderer.image { _ in
            currentBitmap.draw(in: CGRect(origin: .zero, size: newSize))
        }
        currentBitmap = scaled
    }

    return nil
}

private func isJpeg(data: Data) -> Bool {
    return data.count >= 3 &&
        data[0] == 0xFF &&
        data[1] == 0xD8 &&
        data[2] == 0xFF
}

private func findBestQualityJpeg(image: UIImage, maxBytes: Int) -> Data? {
    // Check quality 0
    guard let minData = image.jpegData(compressionQuality: 0.0) else {
        return nil
    }
    if minData.count > maxBytes {
        return nil
    }

    // Check quality 1.0
    if let maxData = image.jpegData(compressionQuality: 1.0), maxData.count <= maxBytes {
        return maxData
    }

    // Binary search for highest quality in range 0.0..1.0
    var low: CGFloat = 0.0
    var high: CGFloat = 1.0
    var bestResult: Data? = minData

    for _ in 0..<10 {
        let mid = (low + high) / 2.0
        if let compressed = image.jpegData(compressionQuality: mid) {
            if compressed.count <= maxBytes {
                bestResult = compressed
                low = mid
            } else {
                high = mid
            }
        } else {
            high = mid
        }
    }

    return bestResult
}
