# Android 10-12 写入文件完整说明

## 📋 核心问题

**问题**：Android 10-12 删除文件不需要 `WRITE_EXTERNAL_STORAGE` 权限（使用 `MediaStore.createDeleteRequest()`），那如何写入文件呢？

**答案**：写入文件的情况比较复杂，取决于写入的位置：

---

## 🔍 写入权限总结

| 写入位置 | Android 9 | Android 10-12 | 说明 |
|---------|----------|---------------|------|
| **应用私有目录** | ❌ 不需要权限 | ❌ **不需要权限** | `/Android/data/包名/` |
| **应用专属媒体目录** | ❌ 不需要权限 | ⚠️ **可能不需要权限** | `Pictures/包名/` |
| **公共媒体目录** | ✅ 需要 `WRITE_EXTERNAL_STORAGE` | ✅ **需要 `WRITE_EXTERNAL_STORAGE`** | `Pictures/`、`Movies/`、`Music/` |
| **其他位置** | ✅ 需要 `WRITE_EXTERNAL_STORAGE` | ❌ **无法写入** | 分区存储限制 |

---

## 💻 三种写入方式详解

### 方式 1：写入到应用私有目录（推荐，不需要权限）

**优点**：
- ✅ 不需要任何权限
- ✅ 应用卸载时自动删除
- ✅ 其他应用无法访问（隐私保护）

**缺点**：
- ❌ 其他应用看不到这些文件
- ❌ 用户删除应用后文件也会被删除

**代码示例**：
```kotlin
/**
 * 保存图片到应用私有目录（不需要权限）
 */
fun saveImageToPrivateDir(bitmap: Bitmap, fileName: String): File? {
    // 获取应用私有外部存储目录
    val privateDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
    if (privateDir == null || !privateDir.exists()) {
        privateDir?.mkdirs()
    }
    
    val imageFile = File(privateDir, fileName)
    FileOutputStream(imageFile).use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
    }
    
    return imageFile
}

// 使用示例
val file = saveImageToPrivateDir(bitmap, "my_image.jpg")
// 文件路径：/storage/emulated/0/Android/data/com.yourapp/files/Pictures/my_image.jpg
```

---

### 方式 2：写入到公共媒体目录（需要 WRITE_EXTERNAL_STORAGE 权限）

**优点**：
- ✅ 其他应用可以看到
- ✅ 用户可以在相册中看到
- ✅ 应用卸载后文件仍然存在

**缺点**：
- ❌ 需要 `WRITE_EXTERNAL_STORAGE` 权限
- ❌ 需要运行时申请权限

**Manifest 声明**：
```xml
<!-- Android 10-12 写入公共目录需要此权限 -->
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="28" />
```

**代码示例**：
```kotlin
/**
 * 保存图片到公共 Pictures 目录（需要 WRITE_EXTERNAL_STORAGE 权限）
 */
fun saveImageToPublicGallery(bitmap: Bitmap, fileName: String): Uri? {
    // 先检查权限
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // Android 10+ 使用 MediaStore API
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            // 写入到公共 Pictures 目录（需要权限）
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
        }
        
        val uri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        )
        
        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            }
        }
        
        return uri
    } else {
        // Android 9 及以下使用文件路径
        val imagesDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_PICTURES
        )
        if (!imagesDir.exists()) {
            imagesDir.mkdirs()
        }
        
        val imageFile = File(imagesDir, fileName)
        FileOutputStream(imageFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        
        // 通知媒体库更新
        val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
        mediaScanIntent.data = Uri.fromFile(imageFile)
        context.sendBroadcast(mediaScanIntent)
        
        return Uri.fromFile(imageFile)
    }
}

// 使用前需要先申请权限
private fun checkAndRequestWritePermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        // Android 9 及以下需要权限
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQUEST_CODE_WRITE
            )
        }
    } else {
        // Android 10+ 写入公共目录也需要权限
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQUEST_CODE_WRITE
            )
        }
    }
}
```

---

### 方式 3：写入到应用专属媒体目录（Android 10+，可能不需要权限）

**说明**：
- Android 10+ 允许应用在媒体目录下创建以包名命名的子目录
- 这种方式可能不需要 `WRITE_EXTERNAL_STORAGE` 权限（取决于具体实现）
- 文件仍然可以在相册中看到，但组织在应用专属目录下

**代码示例**：
```kotlin
/**
 * 保存图片到应用专属媒体目录（Android 10+，可能不需要权限）
 */
fun saveImageToAppMediaDir(bitmap: Bitmap, fileName: String): Uri? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            // 写入到应用专属目录：Pictures/包名/
            put(MediaStore.Images.Media.RELATIVE_PATH, 
                Environment.DIRECTORY_PICTURES + "/" + context.packageName)
        }
        
        val uri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        )
        
        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            }
        }
        
        return uri
    }
    return null
}
```

**注意**：这种方式的行为可能因设备而异，建议测试后再使用。

---

## 📊 完整对比表

| 特性 | 应用私有目录 | 应用专属媒体目录 | 公共媒体目录 |
|------|------------|----------------|-------------|
| **需要权限** | ❌ 不需要 | ⚠️ 可能不需要 | ✅ 需要 `WRITE_EXTERNAL_STORAGE` |
| **其他应用可见** | ❌ 不可见 | ✅ 可见 | ✅ 可见 |
| **相册中显示** | ❌ 不显示 | ✅ 显示 | ✅ 显示 |
| **应用卸载后** | ❌ 自动删除 | ✅ 保留 | ✅ 保留 |
| **文件路径** | `/Android/data/包名/` | `Pictures/包名/` | `Pictures/` |
| **推荐场景** | 临时文件、缓存 | 应用专属媒体 | 用户保存的媒体 |

---

## 🎯 推荐做法

### 场景 1：保存用户拍摄的照片（推荐使用公共目录）

```kotlin
// 需要 WRITE_EXTERNAL_STORAGE 权限
fun saveUserPhoto(bitmap: Bitmap) {
    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "photo_${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
    }
    
    val uri = contentResolver.insert(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        contentValues
    )
    
    uri?.let {
        contentResolver.openOutputStream(it)?.use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        }
    }
}
```

### 场景 2：保存应用临时文件（推荐使用私有目录）

```kotlin
// 不需要权限
fun saveTempFile(data: ByteArray, fileName: String): File? {
    val privateDir = context.getExternalFilesDir(null)
    val file = File(privateDir, fileName)
    file.writeBytes(data)
    return file
}
```

### 场景 3：保存应用生成的媒体文件（推荐使用应用专属目录）

```kotlin
// 可能不需要权限（取决于实现）
fun saveAppGeneratedMedia(bitmap: Bitmap, fileName: String): Uri? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, 
                Environment.DIRECTORY_PICTURES + "/" + context.packageName)
        }
        
        val uri = contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        )
        
        uri?.let {
            contentResolver.openOutputStream(it)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            }
        }
        
        return uri
    }
    return null
}
```

---

## ✅ 总结

1. **删除文件**：Android 10+ 不需要权限，使用 `MediaStore.createDeleteRequest()`

2. **写入文件**：
   - **应用私有目录**：不需要权限 ✅
   - **应用专属媒体目录**：可能不需要权限 ⚠️
   - **公共媒体目录**：需要 `WRITE_EXTERNAL_STORAGE` 权限 ✅

3. **推荐策略**：
   - 临时文件、缓存 → 使用应用私有目录（不需要权限）
   - 用户保存的媒体 → 使用公共目录（需要权限）
   - 应用生成的媒体 → 使用应用专属目录（可能不需要权限）

---

**最后更新：2026-01-02**

