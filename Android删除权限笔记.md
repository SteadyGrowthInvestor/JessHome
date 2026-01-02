# Android 删除媒体文件权限总结

## 📋 版本差异概览

| Android 版本 | API 级别 | 删除方式 | 是否需要权限声明 |
|------------|---------|---------|----------------|
| Android 9 及以下 | ≤ 28 | 直接删除 | 需要 `WRITE_EXTERNAL_STORAGE` |
| Android 10-16 | 29-35 | `MediaStore.createDeleteRequest()` | **不需要额外权限** |

## 🔑 核心要点

### Android 10+ (API 29+) 删除媒体文件

**重要：不需要在 Manifest 中声明删除权限！**

- ❌ **不需要** `WRITE_EXTERNAL_STORAGE`
- ❌ **不需要** `MANAGE_EXTERNAL_STORAGE`
- ✅ **只需要** `READ_MEDIA_IMAGES`（用于读取图片）

### 删除流程

1. 调用 `MediaStore.createDeleteRequest()` 创建删除请求
2. 系统弹出对话框让用户确认删除
3. 用户确认后，**系统自动删除文件**
4. 应用在 `onActivityResult` 中接收结果，**不需要再次调用 `delete()`**

## 💻 代码实现

### 1. AndroidManifest.xml 权限声明

```xml
<!-- Android 13+ 使用新的媒体权限 -->
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />

<!-- Android 10-12 使用旧的存储权限 -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />
```

**注意：**
- `READ_MEDIA_IMAGES` 只在 Android 13+ 可用
- `READ_EXTERNAL_STORAGE` 通过 `maxSdkVersion="32"` 限制只在 Android 10-12 使用
- **两个权限都需要声明**，系统会根据版本自动选择

### 2. 删除代码实现

```kotlin
// 删除图片
private fun deletePhoto() {
    val uri = photoUri ?: return
    
    try {
        // 从 URI 中提取图片 ID
        val photoId = ContentUris.parseId(uri)
        
        // 构建删除 URI
        val deleteUri = ContentUris.withAppendedId(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            photoId
        )
        
        // Android 10+ 使用 MediaStore.createDeleteRequest()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                // 创建删除请求，系统会弹出对话框
                val pendingIntent = MediaStore.createDeleteRequest(
                    contentResolver,
                    listOf(deleteUri)
                )
                // 启动系统删除权限对话框
                startIntentSenderForResult(
                    pendingIntent.intentSender,
                    REQUEST_CODE_DELETE,
                    null, 0, 0, 0, null
                )
            } catch (e: Exception) {
                // 如果无法创建删除请求，尝试直接删除（备用方案）
                performDelete(deleteUri)
            }
        } else {
            // Android 10 以下直接删除
            performDelete(deleteUri)
        }
    } catch (e: Exception) {
        Toast.makeText(this, "删除失败: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

// 处理删除权限请求的结果
override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    
    if (requestCode == REQUEST_CODE_DELETE) {
        if (resultCode == RESULT_OK) {
            // ⚠️ 重要：用户确认后，系统已经自动删除了文件
            // 不需要再次调用 delete() 方法！
            setResult(RESULT_OK)
            Toast.makeText(this, "图片已删除", Toast.LENGTH_SHORT).show()
            finish()
        } else {
            // 用户拒绝了删除权限
            Toast.makeText(this, "需要删除权限才能删除图片", Toast.LENGTH_SHORT).show()
        }
    }
}
```

## ⚠️ 常见错误

### 错误 1：在 onActivityResult 中再次调用 delete()

```kotlin
// ❌ 错误：系统已经删除了，不需要再次删除
override fun onActivityResult(...) {
    if (resultCode == RESULT_OK) {
        performDelete(deleteUri)  // 这会导致 SecurityException！
    }
}

// ✅ 正确：系统已删除，直接返回成功
override fun onActivityResult(...) {
    if (resultCode == RESULT_OK) {
        setResult(RESULT_OK)
        finish()
    }
}
```

### 错误 2：在 Manifest 中声明删除权限

```xml
<!-- ❌ 错误：Android 10+ 不需要这些权限 -->
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />

<!-- ✅ 正确：只需要读取权限 -->
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />
```

### 错误 3：使用错误的 API 版本检查

```kotlin
// ❌ 错误：MediaStore.createDeleteRequest() 从 Android 10 (API 29) 开始
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {  // R 是 Android 11
    // ...
}

// ✅ 正确：使用 Q (Android 10)
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    // ...
}
```

## 📝 版本详细说明

### Android 10 (API 29)
- 引入分区存储（Scoped Storage）
- 删除媒体文件需要使用 `MediaStore.createDeleteRequest()`
- 系统弹出对话框让用户确认
- **不需要额外权限声明**

### Android 11-12 (API 30-32)
- 与 Android 10 相同
- 删除方式不变
- **不需要额外权限声明**

### Android 13+ (API 33+)
- 读取权限从 `READ_EXTERNAL_STORAGE` 改为 `READ_MEDIA_IMAGES`
- **删除方式不变**，仍然使用 `MediaStore.createDeleteRequest()`
- **不需要额外权限声明**

## 🔍 调试技巧

### 添加日志

```kotlin
// 创建删除请求时
android.util.Log.d("PhotoViewActivity", "尝试创建删除请求: $deleteUri")
val pendingIntent = MediaStore.createDeleteRequest(...)
android.util.Log.d("PhotoViewActivity", "删除请求创建成功，启动权限对话框")

// 处理结果时
android.util.Log.d("PhotoViewActivity", "onActivityResult: requestCode=$requestCode, resultCode=$resultCode")
```

### 常见错误信息

1. **`SecurityException: has no access to content://...`**
   - 原因：在 `onActivityResult` 中再次调用了 `delete()`
   - 解决：系统已删除，直接返回成功

2. **`无法创建删除请求`**
   - 原因：某些设备可能不支持该 API
   - 解决：添加备用方案，尝试直接删除

3. **`删除返回 0`**
   - 原因：文件不存在或已被删除
   - 解决：检查文件是否还存在

## 📚 参考资源

- [Android 官方文档 - 删除媒体文件](https://developer.android.com/training/data-storage/shared/media#delete-media)
- `MediaStore.createDeleteRequest()` API 文档
- Android 10+ 分区存储机制

## ✅ 最佳实践

1. **始终使用 `MediaStore.createDeleteRequest()`**（Android 10+）
2. **不要在 `onActivityResult` 中再次调用 `delete()`**
3. **添加错误处理和用户友好的提示**
4. **使用 `ContentUris.withAppendedId()` 构建 URI**（更可靠）
5. **删除成功后刷新列表**（使用 ActivityResult 回调）

## 🎯 总结

- ✅ Android 10+ 删除媒体文件**不需要**在 Manifest 中声明删除权限
- ✅ 使用 `MediaStore.createDeleteRequest()` 请求用户确认
- ✅ 用户确认后系统自动删除，应用不需要再次调用 `delete()`
- ✅ Android 10-16 删除方式相同，无差异
- ✅ 只需要声明读取权限（`READ_MEDIA_IMAGES` 或 `READ_EXTERNAL_STORAGE`）

