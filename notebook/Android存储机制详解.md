# Android 存储机制详解

## 📚 目录

1. [存储机制演进历史](#存储机制演进历史)
2. [各版本详细说明](#各版本详细说明)
3. [权限对比表](#权限对比表)
4. [代码实现示例](#代码实现示例)
5. [迁移指南](#迁移指南)

---

## 📅 存储机制演进历史

### 时间线

```
Android 1.0-5.1 (API 1-22)
├─ 安装时权限模型
├─ 所有权限在安装时一次性授予
└─ 用户无法撤销权限

Android 6.0 (API 23) - 2015年
├─ 引入运行时权限模型
├─ 危险权限需要运行时请求
└─ 用户可以随时撤销权限

Android 10 (API 29) - 2019年
├─ 引入分区存储 (Scoped Storage)
├─ 应用只能访问自己的文件和媒体库
└─ 需要用户明确授权才能访问其他文件

Android 11 (API 30) - 2020年
├─ 强化分区存储
├─ 所有应用默认启用分区存储
└─ 更严格的媒体文件访问控制

Android 13 (API 33) - 2022年
├─ 细粒度媒体权限
├─ 分离图片、视频、音频权限
└─ 更精确的权限控制
```

---

## 🔍 各版本详细说明

### Android 1.0 - 5.1 (API 1-22) - 传统存储模型

#### 特点
- **安装时权限模型**：所有权限在应用安装时一次性授予
- **用户无法撤销**：一旦安装，权限永久有效
- **完全访问**：应用可以访问整个外部存储

#### 权限声明
```xml
<!-- 读取外部存储 -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<!-- 写入外部存储 -->
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
```

#### 文件访问方式

**公共目录访问**（需要权限）：
```kotlin
// 直接使用文件路径访问公共目录
val file = File(Environment.getExternalStorageDirectory(), "myfile.txt")
file.readText()  // 直接读取
file.writeText("content")  // 直接写入
```

**应用私有目录**（不需要权限，Android 4.4+ 引入）：
```kotlin
// Android 4.4 (API 19) 引入了 getExternalFilesDir()
// 应用私有目录一直存在，不是分区存储带来的
val privateDir = getExternalFilesDir(null)  // /Android/data/包名/files/
val file = File(privateDir, "myfile.txt")
file.writeText("content")  // 不需要权限
```

#### 重要说明：私有目录的历史

**私有目录不是分区存储带来的**：
- ✅ **Android 4.4 (API 19)** 就引入了 `getExternalFilesDir()` API
- ✅ **Android 9 及以下**也有应用私有目录
- ✅ 私有目录一直**不需要权限**就可以访问
- ⚠️ 但很多应用习惯使用公共目录，因为公共目录的文件可以被其他应用访问

**分区存储的变化**：
- ❌ 不是"带来"私有目录（私有目录早就存在）
- ✅ 而是**限制**了应用访问公共目录和其他应用目录的能力
- ✅ **强制**应用更多地使用私有目录
- ✅ 让私有目录成为**推荐**的存储方式

#### 问题
- ❌ 用户无法控制权限
- ❌ 应用可以访问所有文件
- ❌ 隐私和安全问题
- ⚠️ 很多应用不使用私有目录，导致文件散乱

---

### Android 6.0 (API 23) - 运行时权限模型

#### 特点
- **运行时权限**：危险权限需要在运行时请求
- **用户可以拒绝**：用户可以随时撤销权限
- **权限分组**：权限按组管理

#### 权限分类

**普通权限**（自动授予）：
- `INTERNET`
- `ACCESS_NETWORK_STATE`
- 等

**危险权限**（需要运行时请求）：
- `READ_EXTERNAL_STORAGE`
- `WRITE_EXTERNAL_STORAGE`
- `CAMERA`
- `LOCATION`
- 等

#### 代码实现
```kotlin
// 检查权限
if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) 
    != PackageManager.PERMISSION_GRANTED) {
    // 请求权限
    ActivityCompat.requestPermissions(
        activity,
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
        REQUEST_CODE
    )
}

// 处理权限结果
override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    grantResults: IntArray
) {
    if (requestCode == REQUEST_CODE && grantResults.isNotEmpty() 
        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        // 权限已授予
    }
}
```

#### 改进
- ✅ 用户可以控制权限
- ✅ 运行时请求，更灵活
- ⚠️ 仍然可以访问整个外部存储

---

### Android 10 (API 29) - 分区存储 (Scoped Storage)

#### 核心概念

**分区存储的目标**：
- 保护用户隐私
- 减少应用对存储的滥用
- 提供更安全的文件访问机制

#### 存储区域划分

```
外部存储
├─ 应用私有目录 (无需权限)
│   ├─ /storage/emulated/0/Android/data/包名/
│   └─ 应用卸载时自动删除
│
├─ 媒体库 (需要权限)
│   ├─ 图片：MediaStore.Images
│   ├─ 视频：MediaStore.Video
│   ├─ 音频：MediaStore.Audio
│   └─ 下载：MediaStore.Downloads
│
└─ 其他应用目录 (无法访问)
    └─ 其他应用的私有目录
```

#### ⚠️ 重要区别：Android 9 vs Android 10-12

虽然都使用 `READ_EXTERNAL_STORAGE` 权限，但**作用范围完全不同**：

| 特性 | Android 9 及以下 | Android 10-12 |
|------|----------------|---------------|
| **权限名称** | `READ_EXTERNAL_STORAGE` | `READ_EXTERNAL_STORAGE` |
| **访问范围** | ✅ **整个外部存储**<br/>- 可以访问所有文件<br/>- 可以访问其他应用的目录<br/>- 可以访问任意路径 | ⚠️ **仅媒体库**<br/>- 只能访问媒体库（图片/视频/音频）<br/>- ❌ 无法访问其他应用的目录<br/>- ❌ 无法访问任意路径 |
| **访问方式** | ✅ **文件路径**<br/>`File("/storage/emulated/0/Pictures/image.jpg")` | ⚠️ **MediaStore API**<br/>必须使用 `ContentResolver` + `URI` |
| **文件路径访问** | ✅ 可以直接使用 `File` 对象 | ❌ 无法直接使用文件路径访问媒体文件 |
| **其他应用目录** | ✅ 可以访问 | ❌ 完全无法访问 |
| **删除权限** | 需要 `WRITE_EXTERNAL_STORAGE` | ❌ **不需要权限**<br/>使用 `MediaStore.createDeleteRequest()` |

#### 详细对比示例

**Android 9 及以下（有权限后）**：
```kotlin
// ✅ 可以直接使用文件路径访问任意文件
val file = File("/storage/emulated/0/Pictures/image.jpg")
val bitmap = BitmapFactory.decodeFile(file.absolutePath)

// ✅ 可以访问其他应用的目录（如果知道路径）
val otherAppFile = File("/storage/emulated/0/Android/data/com.other.app/files/data.txt")
if (otherAppFile.exists()) {
    val content = otherAppFile.readText()  // 可以读取
}

// ✅ 可以遍历整个外部存储
val rootDir = Environment.getExternalStorageDirectory()
rootDir.listFiles()?.forEach { file ->
    // 可以访问所有文件
}
```

**Android 10-12（有权限后）**：
```kotlin
// ❌ 无法直接使用文件路径访问媒体文件
val file = File("/storage/emulated/0/Pictures/image.jpg")
val bitmap = BitmapFactory.decodeFile(file.absolutePath)  // 可能失败或无法访问

// ✅ 必须使用 MediaStore API
val projection = arrayOf(MediaStore.Images.Media._ID)
val cursor = contentResolver.query(
    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
    projection,
    null,
    null,
    null
)
cursor?.use {
    while (it.moveToNext()) {
        val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
        val uri = ContentUris.withAppendedId(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            id
        )
        // 使用 URI 访问
        Glide.with(context).load(uri).into(imageView)
    }
}

// ❌ 完全无法访问其他应用的目录
val otherAppFile = File("/storage/emulated/0/Android/data/com.other.app/files/data.txt")
// 即使文件存在，也无法访问（SecurityException）

// ❌ 无法遍历整个外部存储
val rootDir = Environment.getExternalStorageDirectory()
rootDir.listFiles()?.forEach { file ->
    // 只能访问媒体库中的文件，其他文件无法访问
}
```

#### 权限变化

**读取权限**：
- `READ_EXTERNAL_STORAGE` - 仍然可用，但**作用范围受限**
- **Android 9**：可以访问整个外部存储
- **Android 10-12**：只能访问媒体库，不能访问其他应用目录

**写入权限**：
- **Android 9**：需要 `WRITE_EXTERNAL_STORAGE` 权限
- **Android 10-12**：
  - ✅ **写入到应用私有目录**：不需要权限
  - ⚠️ **写入到媒体库**：根据情况而定
    - 写入到 `Pictures`、`Movies`、`Music` 等公共目录：**需要** `WRITE_EXTERNAL_STORAGE` 权限
    - 写入到应用自己的媒体目录：可能不需要权限（取决于实现方式）
  - ❌ **写入到其他位置**：无法写入

**删除权限**：
- **Android 9**：需要 `WRITE_EXTERNAL_STORAGE` 权限
- **Android 10-12**：❌ **不需要声明权限**
- ✅ 使用 `MediaStore.createDeleteRequest()` 请求用户确认

#### 文件访问方式

**1. 访问应用私有目录（无需权限，Android 4.4+ 就存在）**
```kotlin
// 获取应用私有外部存储目录
// 注意：私有目录从 Android 4.4 (API 19) 就存在，不是分区存储带来的
val privateDir = getExternalFilesDir(null)  // /Android/data/包名/files/
val file = File(privateDir, "myfile.txt")
file.writeText("content")  // 直接写入，无需权限

// Android 9 及以下也可以使用，同样不需要权限
// 分区存储只是让私有目录成为更推荐的方式
```

**2. 访问媒体库（需要权限）**
```kotlin
// 查询图片
val projection = arrayOf(
    MediaStore.Images.Media._ID,
    MediaStore.Images.Media.DISPLAY_NAME,
    MediaStore.Images.Media.DATE_ADDED
)

val cursor = contentResolver.query(
    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
    projection,
    null,
    null,
    "${MediaStore.Images.Media.DATE_ADDED} DESC"
)

cursor?.use {
    while (it.moveToNext()) {
        val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
        val uri = ContentUris.withAppendedId(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            id
        )
        // 使用 URI 访问文件
    }
}
```

**3. 保存文件到媒体库**
```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "image.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MyApp")
    }
    
    val uri = contentResolver.insert(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        contentValues
    )
    
    uri?.let {
        contentResolver.openOutputStream(it)?.use { outputStream ->
            // 写入文件内容
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        }
    }
}
```

**4. 删除媒体文件**
```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    // 创建删除请求
    val pendingIntent = MediaStore.createDeleteRequest(
        contentResolver,
        listOf(deleteUri)
    )
    // 启动系统删除对话框
    startIntentSenderForResult(
        pendingIntent.intentSender,
        REQUEST_CODE_DELETE,
        null, 0, 0, 0, null
    )
}
```

#### 兼容性选项

**临时禁用分区存储**（不推荐）：
```xml
<application
    android:requestLegacyExternalStorage="true">
    <!-- 仅在迁移期间使用，Android 11+ 无效 -->
</application>
```

---

### Android 11 (API 30) - 强化分区存储

#### 主要变化

1. **强制启用分区存储**
   - `requestLegacyExternalStorage="true"` **不再生效**
   - 所有应用默认启用分区存储
   - 无法回退到旧模式

2. **更严格的媒体访问**
   - 访问媒体文件需要明确权限
   - 无法直接访问文件路径
   - 必须使用 MediaStore API

3. **所有文件访问权限**
   - 新增 `MANAGE_EXTERNAL_STORAGE` 权限
   - 需要用户在系统设置中手动授予
   - 仅限文件管理器等特殊应用

#### 权限声明
```xml
<!-- Android 11+ 仍然使用这些权限 -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />

<!-- 所有文件访问权限（需要特殊申请） -->
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE"
    tools:ignore="ScopedStorage" />
```

#### 检查所有文件访问权限
```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
    if (!Environment.isExternalStorageManager()) {
        // 引导用户到设置页面授予权限
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
        intent.data = Uri.parse("package:$packageName")
        startActivity(intent)
    }
}
```

---

### Android 13 (API 33) - 细粒度媒体权限

#### 权限拆分

**旧权限**（Android 12 及以下）：
```xml
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
```

**新权限**（Android 13+）：
```xml
<!-- 读取图片 -->
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
<!-- 读取视频 -->
<uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
<!-- 读取音频 -->
<uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />
```

#### 兼容性声明

**推荐做法**（同时声明两个权限）：
```xml
<!-- Android 13+ 使用新权限 -->
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />

<!-- Android 10-12 使用旧权限 -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />
```

#### 权限请求代码
```kotlin
private fun checkPermissionAndLoadPhotos() {
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // Android 13+ 使用新权限
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        // Android 10-12 使用旧权限
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    if (ContextCompat.checkSelfPermission(context, permission) 
        == PackageManager.PERMISSION_GRANTED) {
        // 已有权限
    } else {
        // 请求权限
        requestPermissions(arrayOf(permission), REQUEST_CODE)
    }
}
```

#### 优势
- ✅ 更精确的权限控制
- ✅ 用户可以选择授予哪些类型的媒体权限
- ✅ 更好的隐私保护

---

### Android 14+ (API 34+)

#### 主要变化

1. **部分照片访问**
   - 用户可以授予应用访问部分照片的权限
   - 通过照片选择器（Photo Picker）实现
   - 更精细的权限控制

2. **更严格的权限管理**
   - 继续强化分区存储
   - 更安全的文件访问机制

---

## 📊 权限对比表

| 操作 | Android 9- | Android 10-12 | Android 13+ |
|------|-----------|---------------|-------------|
| **读取图片** | `READ_EXTERNAL_STORAGE` | `READ_EXTERNAL_STORAGE` | `READ_MEDIA_IMAGES` |
| **读取视频** | `READ_EXTERNAL_STORAGE` | `READ_EXTERNAL_STORAGE` | `READ_MEDIA_VIDEO` |
| **读取音频** | `READ_EXTERNAL_STORAGE` | `READ_EXTERNAL_STORAGE` | `READ_MEDIA_AUDIO` |
| **写入文件** | `WRITE_EXTERNAL_STORAGE` | `WRITE_EXTERNAL_STORAGE` | `WRITE_EXTERNAL_STORAGE` (maxSdkVersion="28") |
| **删除文件** | `WRITE_EXTERNAL_STORAGE` | **无需权限** | **无需权限** |
| **访问方式** | 文件路径 | MediaStore API | MediaStore API |

---

## 💻 代码实现示例

### 完整的权限检查和文件访问示例

```kotlin
class StorageHelper(private val context: Context) {
    
    companion object {
        private const val REQUEST_CODE_PERMISSION = 1001
    }
    
    /**
     * 检查并请求读取图片权限
     */
    fun checkAndRequestReadImagePermission(activity: Activity): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 使用新权限
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            // Android 10-12 使用旧权限
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        
        return if (ContextCompat.checkSelfPermission(context, permission) 
            == PackageManager.PERMISSION_GRANTED) {
            true
        } else {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(permission),
                REQUEST_CODE_PERMISSION
            )
            false
        }
    }
    
    /**
     * 查询所有图片
     */
    fun queryAllImages(): List<ImageItem> {
        val images = mutableListOf<ImageItem>()
        
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.SIZE
        )
        
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val dateAdded = cursor.getLong(dateColumn)
                val size = cursor.getLong(sizeColumn)
                
                // 构建 URI
                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                
                images.add(ImageItem(id, uri, name, dateAdded, size))
            }
        }
        
        return images
    }
    
    /**
     * 保存图片到媒体库
     * 
     * 注意：
     * - Android 9 及以下：需要 WRITE_EXTERNAL_STORAGE 权限
     * - Android 10-12：写入到公共目录需要 WRITE_EXTERNAL_STORAGE 权限
     * - Android 10-12：写入到应用私有目录不需要权限
     */
    fun saveImageToGallery(bitmap: Bitmap, fileName: String, needPublicAccess: Boolean = true): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ 使用 MediaStore API
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                
                if (needPublicAccess) {
                    // 写入到公共 Pictures 目录（需要 WRITE_EXTERNAL_STORAGE 权限）
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                } else {
                    // 写入到应用专属目录（可能不需要权限）
                    put(MediaStore.Images.Media.RELATIVE_PATH, 
                        Environment.DIRECTORY_PICTURES + "/" + context.packageName)
                }
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
            
            uri
        } else {
            // Android 9 及以下使用文件路径（需要 WRITE_EXTERNAL_STORAGE 权限）
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
            
            Uri.fromFile(imageFile)
        }
    }
    
    /**
     * 保存图片到应用私有目录（不需要权限）
     */
    fun saveImageToPrivateDir(bitmap: Bitmap, fileName: String): File? {
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
    
    /**
     * 删除图片
     */
    fun deleteImage(uri: Uri, activity: Activity, requestCode: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ 使用 MediaStore.createDeleteRequest()
            try {
                val pendingIntent = MediaStore.createDeleteRequest(
                    context.contentResolver,
                    listOf(uri)
                )
                activity.startIntentSenderForResult(
                    pendingIntent.intentSender,
                    requestCode,
                    null, 0, 0, 0, null
                )
            } catch (e: Exception) {
                // 备用方案：尝试直接删除
                try {
                    context.contentResolver.delete(uri, null, null)
                } catch (e2: SecurityException) {
                    Toast.makeText(context, "删除失败：没有权限", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            // Android 9 及以下直接删除
            try {
                context.contentResolver.delete(uri, null, null)
            } catch (e: Exception) {
                Toast.makeText(context, "删除失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * 访问应用私有目录（无需权限）
     */
    fun saveToPrivateDir(fileName: String, content: String): File? {
        val privateDir = context.getExternalFilesDir(null) ?: return null
        val file = File(privateDir, fileName)
        file.writeText(content)
        return file
    }
    
    /**
     * 读取应用私有目录文件（无需权限）
     */
    fun readFromPrivateDir(fileName: String): String? {
        val privateDir = context.getExternalFilesDir(null) ?: return null
        val file = File(privateDir, fileName)
        return if (file.exists()) file.readText() else null
    }
}

data class ImageItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val dateAdded: Long,
    val size: Long
)
```

---

## 🔄 迁移指南

### 从旧版本迁移到 Android 10+

#### 1. 替换文件路径访问为 MediaStore API

**旧代码**（Android 9-）：
```kotlin
// ❌ 直接使用文件路径
val file = File("/storage/emulated/0/Pictures/image.jpg")
val bitmap = BitmapFactory.decodeFile(file.absolutePath)
```

**新代码**（Android 10+）：
```kotlin
// ✅ 使用 MediaStore API 和 URI
val uri = ContentUris.withAppendedId(
    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
    imageId
)
Glide.with(context).load(uri).into(imageView)
```

#### 2. 更新权限声明

**旧代码**：
```xml
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
```

**新代码**：
```xml
<!-- Android 13+ -->
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
<!-- Android 10-12 -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />
<!-- 写入权限（仅 Android 9 及以下需要） -->
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="28" />
```

#### 3. 更新删除逻辑

**旧代码**：
```kotlin
// ❌ 直接删除
contentResolver.delete(uri, null, null)
```

**新代码**：
```kotlin
// ✅ 使用 MediaStore.createDeleteRequest()
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    val pendingIntent = MediaStore.createDeleteRequest(
        contentResolver,
        listOf(uri)
    )
    startIntentSenderForResult(pendingIntent.intentSender, REQUEST_CODE, ...)
}
```

---

## 📋 最佳实践总结

### ✅ 推荐做法

1. **使用 MediaStore API 访问媒体文件**
   - 不要使用文件路径
   - 使用 URI 和 ContentResolver

2. **正确声明权限**
   - Android 13+：`READ_MEDIA_IMAGES`
   - Android 10-12：`READ_EXTERNAL_STORAGE` (maxSdkVersion="32")

3. **使用 ContentUris.withAppendedId() 构建 URI**
   - 更可靠，兼容性更好

4. **删除文件使用 MediaStore.createDeleteRequest()**
   - 不需要声明删除权限
   - 系统会自动处理

5. **应用私有文件使用 getExternalFilesDir()**
   - 无需权限
   - 应用卸载时自动删除

### ❌ 避免的做法

1. **不要使用文件路径访问媒体文件**（Android 10+）
2. **不要声明不必要的权限**
3. **不要在 onActivityResult 中再次调用 delete()**
4. **不要使用 requestLegacyExternalStorage**（Android 11+ 无效，所有应用强制启用分区存储）

---

## 🎯 快速参考

### 权限检查代码模板

#### 1. 只需要图片权限

```kotlin
private fun checkPermission(): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // Android 13+
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        // Android 10-12
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
}

// 请求权限
val permission = checkPermission()
if (ContextCompat.checkSelfPermission(context, permission) 
    != PackageManager.PERMISSION_GRANTED) {
    ActivityCompat.requestPermissions(activity, arrayOf(permission), REQUEST_CODE)
}
```

#### 2. 需要图片、视频、音频权限

```kotlin
private fun getRequiredPermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // Android 13+：分别申请
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO
        )
    } else {
        // Android 10-12：一个权限覆盖所有
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

// 请求权限
val permissions = getRequiredPermissions()
val permissionsToRequest = permissions.filter {
    ContextCompat.checkSelfPermission(context, it) 
        != PackageManager.PERMISSION_GRANTED
}.toTypedArray()

if (permissionsToRequest.isNotEmpty()) {
    ActivityCompat.requestPermissions(activity, permissionsToRequest, REQUEST_CODE)
}
```

### Manifest 权限声明模板

```xml
<!-- Android 13+ 读取图片 -->
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
<!-- Android 10-12 读取存储 -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />
```

---

## 📚 参考资料

- [Android 官方文档 - 分区存储](https://developer.android.com/training/data-storage)
- [Android 官方文档 - 媒体文件访问](https://developer.android.com/training/data-storage/shared/media)
- [Android 官方文档 - 运行时权限](https://developer.android.com/training/permissions/requesting)

---

**最后更新：2026-01-02**

