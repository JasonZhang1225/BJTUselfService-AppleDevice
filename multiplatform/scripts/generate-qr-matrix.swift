import CoreGraphics
import CoreImage
import Foundation

guard CommandLine.arguments.count == 2,
      let data = CommandLine.arguments[1].data(using: .utf8),
      let filter = CIFilter(name: "CIQRCodeGenerator") else {
    fputs("usage: swift generate-qr-matrix.swift <text>\n", stderr)
    exit(2)
}

filter.setValue(data, forKey: "inputMessage")
filter.setValue("M", forKey: "inputCorrectionLevel")

guard let image = filter.outputImage else {
    fputs("unable to generate QR image\n", stderr)
    exit(1)
}

let bounds = image.extent.integral
let width = Int(bounds.width)
let height = Int(bounds.height)
var pixels = [UInt8](repeating: 0, count: width * height)
let context = CIContext(options: [.useSoftwareRenderer: true])
context.render(
    image,
    toBitmap: &pixels,
    rowBytes: width,
    bounds: bounds,
    format: .L8,
    colorSpace: CGColorSpaceCreateDeviceGray()
)

print(width)
for y in 0..<height {
    print((0..<width).map { x in pixels[y * width + x] < 128 ? "1" : "0" }.joined())
}
