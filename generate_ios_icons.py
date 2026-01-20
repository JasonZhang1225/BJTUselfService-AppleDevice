#!/usr/bin/env python3
"""
将 Android 的 ic_launcher.webp 转换为 iOS AppIcon 所需的全套 PNG 图标
使用虚拟环境: source bjtuservicebuild/bin/activate && python3 generate_ios_icons.py
"""

from PIL import Image
import os

# Android 图标路径（使用最高分辨率的 xxxhdpi）
android_icon_path = "AndroidOrigin/app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp"

# iOS AppIcon 输出目录
ios_appicon_dir = "BJTUselfServiceApple/BJTUselfServiceApple/Assets.xcassets/AppIcon.appiconset"

# iOS 所需的图标尺寸（基于 Contents.json）
ios_icon_sizes = {
    # iPhone
    "Icon-App-20x20@2x.png": (40, 40),
    "Icon-App-20x20@3x.png": (60, 60),
    "Icon-App-29x29@2x.png": (58, 58),
    "Icon-App-29x29@3x.png": (87, 87),
    "Icon-App-40x40@2x.png": (80, 80),
    "Icon-App-40x40@3x.png": (120, 120),
    "Icon-App-60x60@2x.png": (120, 120),
    "Icon-App-60x60@3x.png": (180, 180),
    
    # iPad
    "Icon-App-76x76@1x.png": (76, 76),
    "Icon-App-76x76@2x.png": (152, 152),
    "Icon-App-83.5x83.5@2x.png": (167, 167),
    
    # App Store
    "Icon-App-1024x1024@1x.png": (1024, 1024),
    
    # macOS
    "Icon-Mac-16x16@1x.png": (16, 16),
    "Icon-Mac-16x16@2x.png": (32, 32),
    "Icon-Mac-32x32@1x.png": (32, 32),
    "Icon-Mac-32x32@2x.png": (64, 64),
    "Icon-Mac-128x128@1x.png": (128, 128),
    "Icon-Mac-128x128@2x.png": (256, 256),
    "Icon-Mac-256x256@1x.png": (256, 256),
    "Icon-Mac-256x256@2x.png": (512, 512),
    "Icon-Mac-512x512@1x.png": (512, 512),
    "Icon-Mac-512x512@2x.png": (1024, 1024),
}

def generate_icons():
    """从 Android WebP 图标生成 iOS PNG 图标"""
    
    # 检查源图标是否存在
    if not os.path.exists(android_icon_path):
        print(f"❌ 错误: 找不到 Android 图标: {android_icon_path}")
        return
    
    # 确保输出目录存在
    os.makedirs(ios_appicon_dir, exist_ok=True)
    
    print(f"📱 开始从 Android 图标生成 iOS AppIcon...")
    print(f"源文件: {android_icon_path}")
    
    # 打开并转换 Android WebP 图标
    try:
        with Image.open(android_icon_path) as img:
            # 转换为 RGBA（确保支持透明度）
            if img.mode != 'RGBA':
                img = img.convert('RGBA')
            
            print(f"源图标尺寸: {img.size}")
            
            # 生成所有需要的 iOS 尺寸
            for filename, size in ios_icon_sizes.items():
                output_path = os.path.join(ios_appicon_dir, filename)
                
                # 使用高质量的 Lanczos 重采样
                resized = img.resize(size, Image.Resampling.LANCZOS)
                
                # 保存为 PNG
                resized.save(output_path, "PNG", optimize=True)
                print(f"  ✅ 生成: {filename} ({size[0]}x{size[1]})")
    
    except Exception as e:
        print(f"❌ 错误: {e}")
        return
    
    print(f"\n🎉 成功生成 {len(ios_icon_sizes)} 个 iOS 图标!")
    print(f"输出目录: {ios_appicon_dir}")

if __name__ == "__main__":
    generate_icons()
