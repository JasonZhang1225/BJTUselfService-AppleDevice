# 验证码识别集成指南

## 📋 概述

iOS/macOS 端的验证码识别使用 **Core ML**（不是 Python），实现步骤分为两部分：

### 🔄 第一步：模型转换（一次性操作）

#### 1. 安装依赖

```bash
cd /Users/zjg/BJTUselfService-AppleDevice
source .venv/bin/activate
pip install torch coremltools
```

#### 2. 运行转换脚本

```bash
python3 convert_captcha_model.py
```

**预期输出**：
```
✅ 成功加载 TorchScript 模型
✅ 模型追踪完成
✅ 转换成功!
📦 模型已保存到: BJTUselfServiceApple/BJTUselfServiceApple/CaptchaModel.mlpackage
```

如果失败，可能原因：
- `model.pt` 格式不兼容 → 需要提供原始训练代码或重新导出模型
- PyTorch 版本问题 → 尝试 `pip install torch==1.13.1`

---

### 📦 第二步：集成到 Xcode 项目

#### 1. 添加模型文件

1. 打开 Xcode，在左侧项目导航器中找到 `BJTUselfServiceApple` 文件夹
2. **拖拽** `CaptchaModel.mlpackage` 文件到项目中
3. 在弹出的对话框中：
   - ✅ **Copy items if needed**
   - ✅ **Add to targets: BJTUselfServiceApple**
   - 点击 **Finish**

#### 2. 验证集成

在 Xcode 中点击 `CaptchaModel.mlpackage`，应该看到：
- **Model Class**: `CaptchaModel`
- **Input**: `image` (MultiArray, Float32, 1×3×42×130)
- **Output**: `logits` (MultiArray, Float32, 1×15×8)

如果没有自动生成类，在右侧属性面板：
- **Model Class** → 选择 **Manual**
- **Language** → 选择 **Swift**

---

## 🎯 工作原理

### Android vs iOS 对比

| 组件 | Android | iOS |
|------|---------|-----|
| **模型格式** | `.pt` (PyTorch Mobile) | `.mlpackage` (Core ML) |
| **推理引擎** | PyTorch | Core ML (Apple Neural Engine) |
| **图片预处理** | `ImageToTensorConverter.java` | `CaptchaRecognizer.preprocessImage()` |
| **解码逻辑** | `CaptchaModel.decode()` | `CaptchaRecognizer.ctcDecode()` |

### 识别流程

```
1. 下载验证码图片 (130×42 px)
   ↓
2. 预处理: 缩放 + RGB归一化 (0~255 → 0~1)
   ↓
3. 转为 MLMultiArray [1, 3, 42, 130]
   ↓
4. Core ML 推理 → logits [1, 15, 8]
   ↓
5. CTC 解码: 每个位置取 argmax + 去重
   ↓
6. 输出字符串 (例如: "1+2=3")
```

---

## 🐛 调试技巧

### 查看日志

运行 App 后，在 Xcode 控制台搜索 `[CaptchaRecognizer]`：

```
[CaptchaRecognizer] ✅ 成功加载模型: CaptchaModel.mlpackage
[CaptchaRecognizer] Logits shape: [1, 15, 8]
[CaptchaRecognizer] Argmax indices: [0, 1, 11, 2, 14, 3, 0, 0]
[CaptchaRecognizer] 识别结果: 1+2=3
```

### 常见问题

**Q: 提示 "未找到任何可用的模型文件"？**
- 检查 Xcode 中 `CaptchaModel.mlpackage` 的 Target Membership
- 确保文件在 **Copy Bundle Resources** 中

**Q: 识别结果总是错误？**
- 检查输出 shape 是否为 `[1, 15, 8]`
- 对比 Android 端识别同一验证码的结果
- 可能需要调整 `getLinearIndex()` 中的索引计算

**Q: 模型转换失败？**
- 提供完整的训练代码或联系模型作者
- 尝试使用 ONNX 中间格式转换

---

## 📊 性能优化

Core ML 会自动利用：
- **Neural Engine** (A12 及以上芯片)
- **GPU** 加速
- **CPU** 回退

在 iPhone 12 及以上设备，推理速度约 **5-20ms**，比 Android 的 PyTorch Mobile 更快。

---

## 🔒 安全说明

- 模型文件会被编译进 `.app` 包，用户**无法直接提取**
- Core ML 模型比 PyTorch 模型体积更小（通常减少 30%-50%）
- 支持 **On-Device** 推理，无需网络请求

---

## 📝 后续改进

如果当前模型识别率不高，可以考虑：

1. **数据增强训练**：旋转、噪声、模糊
2. **架构升级**：使用 Transformer 或 CRNN
3. **集成 OCR SDK**：如 Apple Vision Framework 的文本识别
