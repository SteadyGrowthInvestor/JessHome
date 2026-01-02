# Android 11+ 权限申请完整示例

## 📋 核心要点

**Android 11+ 不使用 `requestLegacyExternalStorage` 的正确做法**：

1. ✅ **在 Manifest 中声明权限**（根据应用需求）
2. ✅ **在代码中根据 Android 版本动态申请权限**

---

## 1️⃣ Manifest 权限声明

### 场景 A：只需要读取图片

```xml
<!-- Android 13+ 读取图片 -->
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
<!-- Android 10-12 读取存储（包含图片） -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />
```

### 场景 B：需要读取图片、视频、音频

```xml
<!-- Android 13+ 分别声明三个权限 -->
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
<uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
<uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />
<!-- Android 10-12 一个权限覆盖所有 -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />
```

**重要说明**：
- Android 13+ 需要**分别声明** `READ_MEDIA_IMAGES`、`READ_MEDIA_VIDEO`、`READ_MEDIA_AUDIO`
- Android 10-12 只需要 `READ_EXTERNAL_STORAGE`（一个权限覆盖所有媒体类型）
- 使用 `maxSdkVersion="32"` 限制旧权限只在 Android 10-12 使用

---

## 2️⃣ 代码中动态申请权限

### 完整示例代码

```kotlin
class MediaPermissionHelper(private val context: Context) {
    
    companion object {
        private const val REQUEST_CODE_MEDIA_PERMISSION = 1001
    }
    
    /**
     * 获取需要申请的权限列表（根据 Android 版本和应用需求）
     * @param needImages 是否需要图片权限
     * @param needVideos 是否需要视频权限
     * @param needAudio 是否需要音频权限
     */
    fun getRequiredPermissions(
        needImages: Boolean = true,
        needVideos: Boolean = false,
        needAudio: Boolean = false
    ): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+：根据需求分别申请
            val permissions = mutableListOf<String>()
            if (needImages) permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            if (needVideos) permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            if (needAudio) permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            permissions.toTypedArray()
        } else {
            // Android 10-12：一个权限覆盖所有媒体类型
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
    
    /**
     * 检查权限是否已授予
     */
    fun hasPermissions(
        needImages: Boolean = true,
        needVideos: Boolean = false,
        needAudio: Boolean = false
    ): Boolean {
        val permissions = getRequiredPermissions(needImages, needVideos, needAudio)
        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) 
                == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * 请求权限
     */
    fun requestPermissions(
        activity: Activity,
        needImages: Boolean = true,
        needVideos: Boolean = false,
        needAudio: Boolean = false
    ) {
        val permissions = getRequiredPermissions(needImages, needVideos, needAudio)
        val permissionsToRequest = permissions.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) 
                != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                activity,
                permissionsToRequest,
                REQUEST_CODE_MEDIA_PERMISSION
            )
        }
    }
    
    /**
     * 处理权限请求结果
     */
    fun handlePermissionResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ): Boolean {
        if (requestCode == REQUEST_CODE_MEDIA_PERMISSION) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            return allGranted
        }
        return false
    }
}
```

---

## 3️⃣ 在 Fragment/Activity 中使用

### Fragment 中使用示例

```kotlin
class SquareFragment : BaseFragment() {
    
    private val permissionHelper = MediaPermissionHelper(requireContext())
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // 检查权限
        if (!permissionHelper.hasPermissions(needImages = true)) {
            // 请求权限（只需要图片权限）
            permissionHelper.requestPermissions(
                requireActivity(),
                needImages = true,
                needVideos = false,
                needAudio = false
            )
        } else {
            // 已有权限，加载照片
            loadPhotos()
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (permissionHelper.handlePermissionResult(requestCode, permissions, grantResults)) {
            // 权限已授予
            loadPhotos()
        } else {
            // 权限被拒绝
            showPermissionDeniedMessage()
        }
    }
    
    private fun loadPhotos() {
        // 加载照片的逻辑
    }
    
    private fun showPermissionDeniedMessage() {
        Toast.makeText(context, "需要存储权限才能查看照片", Toast.LENGTH_SHORT).show()
    }
}
```

### Activity 中使用示例

```kotlin
class PhotoGalleryActivity : AppCompatActivity() {
    
    private val permissionHelper = MediaPermissionHelper(this)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_gallery)
        
        // 检查权限
        if (!permissionHelper.hasPermissions(
            needImages = true,
            needVideos = true,
            needAudio = false
        )) {
            // 请求权限（需要图片和视频权限）
            permissionHelper.requestPermissions(
                this,
                needImages = true,
                needVideos = true,
                needAudio = false
            )
        } else {
            // 已有权限，加载媒体文件
            loadMediaFiles()
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (permissionHelper.handlePermissionResult(requestCode, permissions, grantResults)) {
            // 权限已授予
            loadMediaFiles()
        } else {
            // 权限被拒绝
            showPermissionDeniedDialog()
        }
    }
    
    private fun loadMediaFiles() {
        // 加载媒体文件的逻辑
    }
    
    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("需要权限")
            .setMessage("需要存储权限才能查看照片和视频")
            .setPositiveButton("去设置") { _, _ ->
                // 引导用户到设置页面
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
```

---

## 4️⃣ 简化版本（如果只需要图片权限）

### 简化版权限检查

```kotlin
class SimplePermissionHelper(private val context: Context) {
    
    /**
     * 检查是否有读取图片的权限
     */
    fun hasReadImagePermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            // Android 10-12
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        
        return ContextCompat.checkSelfPermission(context, permission) 
            == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * 请求读取图片的权限
     */
    fun requestReadImagePermission(activity: Activity, requestCode: Int) {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        
        if (!hasReadImagePermission()) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(permission),
                requestCode
            )
        }
    }
}
```

### 使用示例

```kotlin
class SquareFragment : BaseFragment() {
    
    private val permissionHelper = SimplePermissionHelper(requireContext())
    private val REQUEST_CODE_PERMISSION = 1001
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        if (permissionHelper.hasReadImagePermission()) {
            loadPhotos()
        } else {
            permissionHelper.requestReadImagePermission(requireActivity(), REQUEST_CODE_PERMISSION)
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == REQUEST_CODE_PERMISSION 
            && grantResults.isNotEmpty() 
            && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadPhotos()
        }
    }
}
```

---

## 📊 权限申请流程总结

```
1. 检查权限
   ↓
2. 如果没有权限 → 请求权限
   ↓
3. 用户选择（允许/拒绝）
   ↓
4. 处理结果
   ├─ 允许 → 执行需要权限的操作
   └─ 拒绝 → 提示用户或引导到设置
```

---

## ✅ 关键要点

1. **Android 11+ 强制启用分区存储**
   - ❌ `requestLegacyExternalStorage="true"` **无效**
   - ✅ 必须使用 MediaStore API 访问媒体文件

2. **权限声明策略**
   - **Android 13+**：根据需求分别声明 `READ_MEDIA_IMAGES`、`READ_MEDIA_VIDEO`、`READ_MEDIA_AUDIO`
   - **Android 10-12**：只需要 `READ_EXTERNAL_STORAGE`（一个权限覆盖所有）
   - 使用 `maxSdkVersion="32"` 限制旧权限只在 Android 10-12 使用

3. **代码中动态申请**
   - 根据 Android 版本选择要申请的权限数组
   - Android 13+：根据应用需求选择申请哪些媒体权限
   - Android 10-12：申请 `READ_EXTERNAL_STORAGE`

4. **只申请需要的权限**
   - 如果只需要图片，只申请 `READ_MEDIA_IMAGES`
   - 如果还需要视频，再申请 `READ_MEDIA_VIDEO`
   - 不要申请应用不需要的权限

---

**最后更新：2026-01-02**

