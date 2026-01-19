//
//  CaptchaRecognizer.swift
//  BJTUselfServiceApple
//
//  Created on 2026/1/19.
//

import Foundation
import Vision
import CoreML
import CoreImage
#if canImport(UIKit)
import UIKit
#endif

enum CaptchaError: Error {
    case modelNotFound
    case modelLoadFailed
    case inferenceFailed
    case imageProcessingFailed
}

/// 验证码识别器 - 使用 Core ML 模型识别数学算式验证码
/// 匹配 Android 端的实现逻辑
@MainActor
final class CaptchaRecognizer {
    static let shared = CaptchaRecognizer()
    
    private var mlModel: MLModel?
    
    // 字符集，匹配 Android: {' ', '0'-'9', '+', '-', '*', '='}
    private let charset: [Character] = [" ", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "+", "-", "*", "="]
    private let positions = 8  // 8个位置
    private let numClasses = 15  // 15个类别
    
    private init() {
        loadModel()
    }
    
    private func loadModel() {
        // 尝试多种可能的文件名
        let possibleNames = [
            ("CaptchaModel", "mlmodelc"),  // 编译后的模型
            ("CaptchaModel", "mlpackage"),  // 新格式
            ("CaptchaModel", "mlmodel")     // 旧格式
        ]
        
        for (name, ext) in possibleNames {
            if let url = Bundle.main.url(forResource: name, withExtension: ext) {
                do {
                    let model = try MLModel(contentsOf: url)
                    mlModel = model
                    // 打印模型输入输出描述以便调试
                    let inputs = model.modelDescription.inputDescriptionsByName.keys.sorted()
                    let outputs = model.modelDescription.outputDescriptionsByName.keys.sorted()
                    print("[CaptchaRecognizer] ✅ 成功加载模型: \(name).\(ext)")
                    print("[CaptchaRecognizer] model inputs: \(inputs)")
                    print("[CaptchaRecognizer] model outputs: \(outputs)")
                    return
                } catch {
                    print("[CaptchaRecognizer] ⚠️ 无法加载 \(name).\(ext): \(error)")
                }
            }
        }
        
        print("[CaptchaRecognizer] ❌ 未找到任何可用的模型文件")
        print("[CaptchaRecognizer] 💡 请运行 convert_captcha_model.py 转换模型，并将生成的 CaptchaModel.mlpackage 拖入 Xcode 项目，确保 Target Membership 已选中")
        mlModel = nil
    }
    
    /// 识别验证码图片
    /// - Parameter imageData: 验证码图片数据
    /// - Returns: 识别结果（例如 "1+2=3"）
    func recognize(imageData: Data) async throws -> String {
        // 确保模型已加载
        if mlModel == nil {
            loadModel()
        }
        guard let model = mlModel else {
            throw CaptchaError.modelNotFound
        }
        
        // 1. 将图片数据转为 MLMultiArray (匹配 Android 的预处理)
        let inputArray = try preprocessImage(imageData)
        print("[CaptchaRecognizer] preprocessed MLMultiArray shape: \(inputArray.shape.map { Int(truncating: $0) })")
        
        // 2. 创建模型输入（尝试多个可能的输入 key）
        let candidateInputKeys = ["image", "input", "input1"]
        var predictionOutput: MLFeatureProvider? = nil
        for key in candidateInputKeys {
            do {
                let input = try MLDictionaryFeatureProvider(dictionary: [key: inputArray])
                predictionOutput = try await model.prediction(from: input)
                print("[CaptchaRecognizer] ✅ 模型接受输入 key='\(key)'，已执行推理")
                break
            } catch {
                print("[CaptchaRecognizer] ℹ️ 模型未接受输入 key='\(key)': \(error)")
            }
        }
        
        guard let output = predictionOutput else {
            // 输出更多的模型期望信息
            if let desc = model.modelDescription as MLModelDescription? {
                print("[CaptchaRecognizer] ❌ 推理失败；模型输入期望：\(desc.inputDescriptionsByName.keys)")
            }
            throw CaptchaError.inferenceFailed
        }
        
        // 3. 尝试多种可能的输出名称
        let possibleOutputNames = ["output", "logits", "var_580", "logit", "probabilities"]
        for name in possibleOutputNames {
            if let logits = output.featureValue(for: name)?.multiArrayValue {
                print("[CaptchaRecognizer] ✅ 找到输出: \(name)")
                if let decoded = decodeLogits(logits) {
                    print("[CaptchaRecognizer] 识别结果: \(decoded)")
                    return decoded
                }
            }
        }
        
        // 打印所有可用的输出名称
        print("[CaptchaRecognizer] ⚠️ 可用的输出特征:")
        for key in output.featureNames {
            print("  - \(key)")
        }
        
        throw CaptchaError.inferenceFailed
    }
    
    /// 图像预处理：缩放 + 转 MLMultiArray，通道优先 [1, 3, 42, 130]
    private func preprocessImage(_ imageData: Data) throws -> MLMultiArray {
        guard let ciImage = CIImage(data: imageData) else {
            throw CaptchaError.imageProcessingFailed
        }
        
        let targetWidth = 130
        let targetHeight = 42
        
        // 缩放到目标尺寸并绘制到带有已知像素布局的 CGContext（RGBA8888）以避免字节序问题
        let scaleX = CGFloat(targetWidth) / ciImage.extent.width
        let scaleY = CGFloat(targetHeight) / ciImage.extent.height
        let scaledImage = ciImage.transformed(by: CGAffineTransform(scaleX: scaleX, y: scaleY))

        let colorSpace = CGColorSpaceCreateDeviceRGB()
        let bytesPerPixel = 4
        let bytesPerRow = bytesPerPixel * targetWidth
        let bitmapInfo = CGBitmapInfo.byteOrder32Big.union(CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue))

        guard let contextRef = CGContext(data: nil,
                                         width: targetWidth,
                                         height: targetHeight,
                                         bitsPerComponent: 8,
                                         bytesPerRow: bytesPerRow,
                                         space: colorSpace,
                                         bitmapInfo: bitmapInfo.rawValue) else {
            throw CaptchaError.imageProcessingFailed
        }

        let drawRect = CGRect(x: 0, y: 0, width: targetWidth, height: targetHeight)
        let uiImage = UIImage(ciImage: scaledImage)
        UIGraphicsPushContext(contextRef)
        uiImage.draw(in: drawRect)
        UIGraphicsPopContext()

        guard let cgImage = contextRef.makeImage() else {
            throw CaptchaError.imageProcessingFailed
        }

        // 创建 MLMultiArray: [1, 3, 42, 130]
        guard let array = try? MLMultiArray(shape: [1, 3, 42, 130], dataType: .float32) else {
            throw CaptchaError.imageProcessingFailed
        }

        // 提取像素数据（确保为 RGBA）
        guard let data = cgImage.dataProvider?.data, let bytes = CFDataGetBytePtr(data) else {
            throw CaptchaError.imageProcessingFailed
        }

        // 检查像素字节序：我们使用 byteOrder32Big + premultipliedLast -> RGBA
        for y in 0..<targetHeight {
            for x in 0..<targetWidth {
                let offset = y * bytesPerRow + x * bytesPerPixel
                // RGBA 顺序
                let r = Float(bytes[offset]) / 255.0
                let g = Float(bytes[offset + 1]) / 255.0
                let b = Float(bytes[offset + 2]) / 255.0

                // 通道优先布局: [batch, channel, height, width]
                array[[0, 0, y as NSNumber, x as NSNumber] as [NSNumber]] = NSNumber(value: r)
                array[[0, 1, y as NSNumber, x as NSNumber] as [NSNumber]] = NSNumber(value: g)
                array[[0, 2, y as NSNumber, x as NSNumber] as [NSNumber]] = NSNumber(value: b)
            }
        }

        return array
    }
    
    /// 将 logits 解码为字符串
    /// PyTorch 输出形状: [8, 1, 15] -> [positions, batch, classes]
    private func decodeLogits(_ logits: MLMultiArray) -> String? {
        let shape = logits.shape.map { Int(truncating: $0) }
        print("[CaptchaRecognizer] Logits shape: \(shape)")
        
        // 检测实际的维度顺序
        var posCount = positions
        var clsCount = numClasses
        var isPositionFirst = false
        
        // 判断维度顺序
        if shape.count == 3 {
            if shape[0] == 8 && shape[2] == 15 {
                // [8, 1, 15] - positions first (PyTorch 实际输出)
                posCount = shape[0]
                clsCount = shape[2]
                isPositionFirst = true
                print("[CaptchaRecognizer] 维度: [pos=\(posCount), batch, cls=\(clsCount)]")
            } else if shape[1] == 15 && shape[2] == 8 {
                // [1, 15, 8] - batch first
                clsCount = shape[1]
                posCount = shape[2]
                print("[CaptchaRecognizer] 维度: [batch, cls=\(clsCount), pos=\(posCount)]")
            }
        }
        
        var argmaxIndices: [Int] = []
        
        for pos in 0..<posCount {
            var maxVal: Float = -.greatestFiniteMagnitude
            var maxIdx = 0
            
            for cls in 0..<clsCount {
                let index: Int
                if isPositionFirst {
                    // [8, 1, 15]: pos * 15 + cls
                    index = pos * clsCount + cls
                } else {
                    // [1, 15, 8]: cls * 8 + pos
                    index = cls * posCount + pos
                }
                
                if index < logits.count {
                    let val = logits[index].floatValue
                    if val > maxVal {
                        maxVal = val
                        maxIdx = cls
                    }
                }
            }
            
            if maxIdx >= charset.count {
                maxIdx = charset.count - 1
            }
            argmaxIndices.append(maxIdx)
        }
        
        print("[CaptchaRecognizer] Argmax: \(argmaxIndices)")
        return ctcDecode(indices: argmaxIndices)
    }
    
    /// CTC 解码（匹配 Android 的 decode 方法）
    /// 规则：去除连续重复的字符，空格(index=0)不输出
    private func ctcDecode(indices: [Int]) -> String {
        var result = ""
        var lastIndex = -1
        
        for index in indices {
            // 跳过连续重复
            if index == lastIndex {
                continue
            }
            // 跳过空格 (index=0)
            if index != 0 {
                result.append(charset[index])
            }
            lastIndex = index
        }
        
        return result
    }
}
