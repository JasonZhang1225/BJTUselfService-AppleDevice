#!/usr/bin/env python3
"""
PyTorch 转 Core ML 脚本
将 Android 的 model.pt 转换为 iOS/macOS 可用的 Core ML 模型

使用方法:
1. 确保已安装依赖: pip install torch coremltools numpy
2. 运行: python3 convert_captcha_model.py
3. 生成的 CaptchaModel.mlpackage 拖入 Xcode 项目
"""

import torch
import coremltools as ct
import numpy as np
from torch import nn

class CaptchaModel(nn.Module):
    """
    验证码识别模型架构
    输入: [1, 3, 42, 130] - RGB 图像
    输出: [1, 15, 8] - 8个位置，每个位置15个类别的logits
    """
    def __init__(self):
        super().__init__()
        # 这里需要根据实际的 model.pt 架构调整
        # 如果有原始训练代码，请替换此处的架构定义
        pass
    
    def forward(self, x):
        pass

def convert_model():
    """
    方法1: 直接转换已有的 .pt 文件（推荐）
    """
    print("🔄 开始转换 PyTorch 模型为 Core ML...")
    
    # 加载 PyTorch 模型
    model_path = "AndroidOrigin/app/src/main/assets/model.pt"
    try:
        # 尝试直接加载（如果是完整模型）
        model = torch.jit.load(model_path)
        print("✅ 成功加载 TorchScript 模型")
    except Exception as e:
        print(f"❌ 无法加载模型: {e}")
        print("\n⚠️  需要原始训练代码来重建模型架构")
        print("   或提供一个可以直接加载的 .pt 文件")
        return
    
    # 设置为评估模式
    model.eval()
    
    # 定义输入样例
    example_input = torch.rand(1, 3, 42, 130)
    
    # 追踪模型（如果还不是 TorchScript）
    try:
        traced_model = torch.jit.trace(model, example_input)
        print("✅ 模型追踪完成")
    except:
        traced_model = model
        print("ℹ️  模型已是 TorchScript 格式")
    
    # 转换为 Core ML
    print("🔄 转换为 Core ML 格式...")
    
    # Core ML 输入定义 - 使用 MultiArray 而不是 Image（匹配 Android 的 Tensor 输入）
    tensor_input = ct.TensorType(
        name="image",
        shape=(1, 3, 42, 130),
        dtype=np.float32
    )
    
    try:
        # 转换
        mlmodel = ct.convert(
            traced_model,
            inputs=[tensor_input],
            outputs=[ct.TensorType(name="logits")],
            convert_to="mlprogram",  # 使用新格式 (.mlpackage)
            compute_units=ct.ComputeUnit.ALL,  # CPU + GPU + Neural Engine
        )
        
        # 添加元数据
        mlmodel.author = "BJTU SelfService Team"
        mlmodel.license = "同 Android 版本"
        mlmodel.short_description = "验证码识别模型 -MultiArray [1, 3, 42, 130] - RGB 验证码张量 (归一化到 0-1)"
        mlmodel.output_description["logits"] = "MultiArray30 RGB 验证码图片"
        mlmodel.output_description["logits"] = "形状 [1, 15, 8] - 8个位置的类别 logits"
        
        # 保存
        output_path = "BJTUselfServiceApple/BJTUselfServiceApple/CaptchaModel.mlpackage"
        mlmodel.save(output_path)
        
        print(f"✅ 转换成功!")
        print(f"📦 模型已保存到: {output_path}")
        print(f"📏 输入形状: 1×3×42×130 (C×H×W)")
        print(f"📐 输出形状: 1×15×8 (classes×positions)")
        print("\n📝 下一步:")
        print("   1. 在 Xcode 中将 CaptchaModel.mlpackage 拖入项目")
        print("   2. 确保 'Target Membership' 勾选了主 target")
        print("   3. 运行 App 测试验证码识别")
        
    except Exception as e:
        print(f"❌ 转换失败: {e}")
        print("\n💡 可能的原因:")
        print("   - 模型架构不支持 Core ML")
        print("   - 需要调整输入/输出定义")
        print("   - PyTorch 版本不兼容")

def inspect_model():
    """
    检查现有模型的结构
    """
    print("🔍 检查模型信息...")
    model_path = "AndroidOrigin/app/src/main/assets/model.pt"
    
    try:
        model = torch.jit.load(model_path)
        print(f"✅ 模型类型: TorchScript")
        
        # 测试推理
        example_input = torch.rand(1, 3, 42, 130)
        with torch.no_grad():
            output = model(example_input)
        
        print(f"📏 输入形状: {example_input.shape}")
        print(f"📐 输出形状: {output.shape}")
        print(f"🎯 输出数据类型: {output.dtype}")
        
        return True
    except Exception as e:
        print(f"❌ 检查失败: {e}")
        return False

if __name__ == "__main__":
    print("=" * 60)
    print("PyTorch 验证码模型 → Core ML 转换工具")
    print("=" * 60)
    
    # 先检查模型
    if inspect_model():
        print("\n" + "=" * 60)
        convert_model()
    else:
        print("\n⚠️  请确保 model.pt 存在且可以正常加载")
