import CoreML
import Foundation
import ImageIO

@main
struct CaptchaCoreMLHelper {
    static func main() throws {
        guard CommandLine.arguments.count == 2 else {
            throw HelperError.missingModelPath
        }
        let imageData = FileHandle.standardInput.readDataToEndOfFile()
        guard
            !imageData.isEmpty,
            let source = CGImageSourceCreateWithData(imageData as CFData, nil),
            let image = CGImageSourceCreateImageAtIndex(source, 0, nil)
        else {
            throw HelperError.invalidImage
        }

        let modelURL = URL(fileURLWithPath: CommandLine.arguments[1])
        let configuration = MLModelConfiguration()
        configuration.computeUnits = .all
        let model = try MLModel(contentsOf: modelURL, configuration: configuration)
        guard
            let description = model.modelDescription.inputDescriptionsByName["captcha"],
            let constraint = description.imageConstraint
        else {
            throw HelperError.invalidModel
        }
        let imageFeature = try MLFeatureValue(
            cgImage: image,
            constraint: constraint,
            options: nil
        )
        let input = try MLDictionaryFeatureProvider(
            dictionary: ["captcha": imageFeature]
        )
        let prediction = try model.prediction(from: input)
        guard let logits = prediction.featureValue(for: "logits")?.multiArrayValue else {
            throw HelperError.invalidOutput
        }
        let values = (0..<logits.count).map { logits[$0].stringValue }
        FileHandle.standardOutput.write(values.joined(separator: ",").data(using: .utf8)!)
    }
}

enum HelperError: Error {
    case missingModelPath
    case invalidImage
    case invalidModel
    case invalidOutput
}
