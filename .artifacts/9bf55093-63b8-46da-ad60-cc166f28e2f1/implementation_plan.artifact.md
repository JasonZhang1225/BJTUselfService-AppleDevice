# 修复编译报错：可空接收者上的操作符调用

解决 `MainActivity.kt` 和 `SettingScreen.kt` 中因 `versionName` 为空导致的编译错误。

## 待解决的问题

在 Kotlin 中，`versionName` 被推断为 `String?`类型。在对其使用 `<` 操作符（比较大小时），编译器报错，因为 `<` 不能直接用于可空对象。

报错位置：
- `MainActivity.kt:207`
- `SettingScreen.kt:563`
- `SettingScreen.kt:580`
- `SettingScreen.kt:601`

## 提议的变更

### [BJTUselfService](file:///Users/zjg/BJTUselfService/app/src/main/java/team/bjtuss/bjtuselfservice)

#### [MODIFY] [MainActivity.kt](file:///Users/zjg/BJTUselfService/app/src/main/java/team/bjtuss/bjtuselfservice/MainActivity.kt)

为 `versionName` 变量提供默认值（`?: "0.0.0"`），确保其类型为 `String` 而非 `String?`。

#### [MODIFY] [SettingScreen.kt](file:///Users/zjg/BJTUselfService/app/src/main/java/team/bjtuss/bjtuselfservice/screen/SettingScreen.kt)

同上，为 `versionName` 变量提供默认值。

## 验证计划

### 自动测试
- 运行 `./gradlew assembleDebug` 确保编译通过。

### 手动验证
- 确认应用在获取版本号失败时（理论上极少发生）能正常运行。
