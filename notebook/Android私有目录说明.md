# Android 私有目录说明

## 📋 核心问题

**问题**：Android 9 没有私有目录吗？私有目录这一概念是被分区存储带来的吗？

**答案**：
- ❌ **Android 9 也有私有目录**
- ❌ **私有目录不是分区存储带来的**
- ✅ **私有目录从 Android 4.4 (API 19) 就存在了**

---

## 🔍 私有目录的历史

### Android 4.4 (API 19) - 2013年

**引入 `getExternalFilesDir()` API**：
- 这是应用私有外部存储目录的起点
- 路径：`/storage/emulated/0/Android/data/包名/files/`
- **不需要任何权限**就可以访问
- 应用卸载时自动删除

### Android 9 及以下

**私有目录已经存在**：
- ✅ 可以使用 `getExternalFilesDir()` 获取私有目录
- ✅ 不需要任何权限
- ✅ 其他应用无法访问
- ⚠️ 但很多应用不使用，习惯用公共目录

### Android 10+ (分区存储)

**分区存储的作用**：
- ❌ **不是**"带来"私有目录（私有目录早就存在）
- ✅ **而是**限制了应用访问公共目录的能力
- ✅ **强制**应用更多地使用私有目录
- ✅ 让私有目录成为**推荐**和**主要**的存储方式

---

## 📊 对比表

| 特性 | Android 4.4-9 | Android 10+ |
|------|--------------|-------------|
| **私有目录是否存在** | ✅ 存在 | ✅ 存在 |
| **私有目录 API** | `getExternalFilesDir()` | `getExternalFilesDir()` |
| **私有目录是否需要权限** | ❌ 不需要 | ❌ 不需要 |
| **能否访问公共目录** | ✅ 可以（需要权限） | ⚠️ 受限（只能通过 MediaStore） |
| **能否访问其他应用目录** | ✅ 可以（需要权限） | ❌ 完全无法访问 |
| **推荐使用私有目录** | ⚠️ 可选 | ✅ **强制推荐** |

---

## 💻 代码示例

### Android 9 及以下（私有目录已存在）

```kotlin
// Android 9 及以下也可以使用私有目录
// 不需要任何权限
val privateDir = getExternalFilesDir(null)
// 路径：/storage/emulated/0/Android/data/com.yourapp/files/

val file = File(privateDir, "myfile.txt")
file.writeText("content")  // 直接写入，不需要权限

// 读取
val content = file.readText()  // 直接读取，不需要权限
```

### Android 10+（私有目录仍然存在，使用方式相同）

```kotlin
// Android 10+ 私有目录的使用方式完全相同
// 不需要任何权限
val privateDir = getExternalFilesDir(null)
// 路径：/storage/emulated/0/Android/data/com.yourapp/files/

val file = File(privateDir, "myfile.txt")
file.writeText("content")  // 直接写入，不需要权限

// 读取
val content = file.readText()  // 直接读取，不需要权限
```

**关键点**：私有目录的使用方式在 Android 9 和 Android 10+ 中**完全相同**！

---

## 🔄 分区存储带来的变化

### 变化 1：限制公共目录访问

**Android 9 及以下**：
```kotlin
// 可以直接访问公共目录（需要权限）
val publicDir = Environment.getExternalStoragePublicDirectory(
    Environment.DIRECTORY_PICTURES
)
val file = File(publicDir, "image.jpg")
file.writeBytes(data)  // 需要 WRITE_EXTERNAL_STORAGE 权限
```

**Android 10+**：
```kotlin
// 无法直接访问公共目录的文件路径
val publicDir = Environment.getExternalStoragePublicDirectory(
    Environment.DIRECTORY_PICTURES
)
val file = File(publicDir, "image.jpg")
file.writeBytes(data)  // 可能失败或无法访问

// 必须使用 MediaStore API
val contentValues = ContentValues().apply {
    put(MediaStore.Images.Media.DISPLAY_NAME, "image.jpg")
    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
}
val uri = contentResolver.insert(
    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
    contentValues
)
// 使用 URI 写入
```

### 变化 2：限制其他应用目录访问

**Android 9 及以下**：
```kotlin
// 可以访问其他应用的目录（需要权限）
val otherAppDir = File("/storage/emulated/0/Android/data/com.other.app/files/")
if (otherAppDir.exists()) {
    val file = File(otherAppDir, "data.txt")
    val content = file.readText()  // 可以读取
}
```

**Android 10+**：
```kotlin
// 完全无法访问其他应用的目录
val otherAppDir = File("/storage/emulated/0/Android/data/com.other.app/files/")
val file = File(otherAppDir, "data.txt")
val content = file.readText()  // SecurityException，无法访问
```

### 变化 3：强制使用私有目录

**Android 9 及以下**：
- 应用可以选择使用私有目录或公共目录
- 很多应用习惯使用公共目录（因为其他应用可以看到）

**Android 10+**：
- 访问公共目录受限，必须使用 MediaStore API
- **强制推荐**使用私有目录
- 私有目录成为主要存储方式

---

## 📚 私有目录 API 历史

| Android 版本 | API 级别 | 私有目录 API | 说明 |
|------------|---------|------------|------|
| Android 1.0-4.3 | 1-18 | ❌ 不存在 | 只能使用公共目录 |
| Android 4.4 | 19 | ✅ `getExternalFilesDir()` | **首次引入** |
| Android 5.0 | 21 | ✅ `getExternalFilesDir()` | 增强功能 |
| Android 6.0 | 23 | ✅ `getExternalFilesDir()` | 运行时权限模型 |
| Android 9 | 28 | ✅ `getExternalFilesDir()` | 仍然存在 |
| Android 10 | 29 | ✅ `getExternalFilesDir()` | 分区存储，强制推荐 |
| Android 11+ | 30+ | ✅ `getExternalFilesDir()` | 继续存在 |

---

## ✅ 总结

1. **私有目录不是分区存储带来的**
   - 私有目录从 Android 4.4 (API 19) 就存在了
   - Android 9 及以下也有私有目录

2. **分区存储的作用**
   - 限制应用访问公共目录和其他应用目录的能力
   - 强制应用更多地使用私有目录
   - 让私有目录成为推荐和主要的存储方式

3. **私有目录的使用方式**
   - Android 9 和 Android 10+ 使用方式完全相同
   - 都不需要权限
   - 都使用 `getExternalFilesDir()` API

4. **关键区别**
   - Android 9：可以选择使用私有目录或公共目录
   - Android 10+：访问公共目录受限，**强制推荐**使用私有目录

---

**最后更新：2026-01-02**

