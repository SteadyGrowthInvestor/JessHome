package com.happy.jesshome.ui.tab

// Android 权限相关导入
import android.Manifest
import android.content.pm.PackageManager
// Android 系统版本和 Bundle 导入
import android.content.ContentUris
import android.os.Build
import android.os.Bundle
// 媒体库查询相关导入
import android.provider.MediaStore
// View 相关导入
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
// AndroidX 兼容性库导入
import androidx.core.content.ContextCompat
// RecyclerView 相关导入
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
// 图片加载库 Glide 导入
import com.bumptech.glide.Glide
// 项目资源文件导入
import com.happy.jesshome.R
// 基础 Fragment 类导入
import com.happy.jesshome.base.BaseFragment
// 数据绑定相关导入
import com.happy.jesshome.databinding.FragmentSquareBinding
import com.happy.jesshome.databinding.ItemDateHeaderBinding
import com.happy.jesshome.databinding.ItemPhotoGridBinding
// 相机工具类导入
import com.happy.jesshome.util.CameraHelper
// Intent 和 Uri 导入
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
// Toast 提示导入
import android.widget.Toast
// Activity Result API 导入
import androidx.activity.result.contract.ActivityResultContracts
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.target.Target
// 文件操作导入
import java.io.File
// 日期时间相关导入
import java.util.Calendar
import java.util.Locale

/**
 * 广场 Fragment - 显示系统相册照片
 * 支持按时间筛选：所有、日、月、年
 * 按日期分组显示，支持展开/折叠
 */
class SquareFragment : BaseFragment(R.layout.fragment_square) {

    /**
     * ViewBinding 的私有可变引用，用于在 onDestroyView 中置空以避免内存泄漏。
     */
    private var _binding: FragmentSquareBinding? = null
    
    /**
     * ViewBinding 的公共不可变引用，用于访问视图元素。
     */
    private val binding get() = _binding!!

    /**
     * 当前选中的筛选类型，默认为显示所有照片。
     */
    private var currentFilter: FilterType = FilterType.ALL
    
    /**
     * 按日期分组的照片列表，每个 DateGroup 包含一个日期及其对应的照片集合。
     */
    private val dateGroups = mutableListOf<DateGroup>()
    
    /**
     * 记录已展开的日期键集合，用于控制日期分组的展开和折叠状态。
     */
    private val expandedDates = mutableSetOf<String>()
    
    /**
     * 选中的照片 ID 集合，用于多选模式下的照片选择管理。
     */
    private val selectedPhotos = mutableSetOf<Long>()
    
    /**
     * 是否处于选择模式的标志，当为 true 时用户可以多选照片进行拼图操作。
     */
    private var isSelectionMode = false
    
    /**
     * 照片列表的 RecyclerView 适配器，负责管理日期标签和照片项的显示。
     */
    private lateinit var photoAdapter: PhotoAdapter
    
    /**
     * 当前拍照时使用的照片 URI，用于保存拍照结果。
     */
    private var currentPhotoUri: Uri? = null
    
    /**
     * 当前拍照时使用的照片文件，用于保存拍照结果。
     */
    private var currentPhotoFile: File? = null

    /**
     * 筛选类型枚举，用于定义照片的时间筛选范围。
     * ALL: 显示所有照片
     * DAY: 仅显示今天的照片
     * MONTH: 仅显示本月的照片
     * YEAR: 仅显示本年的照片
     */
    private enum class FilterType {
        ALL, DAY, MONTH, YEAR
    }

    /**
     * 日期分组数据类，用于存储按日期分组的照片信息。
     * @param dateKey 日期键，格式为 yyyy-MM-dd，用于唯一标识一个日期。
     * @param dateLabel 日期标签，格式为 2024年1月1日，用于在界面上显示给用户。
     * @param photos 该日期下的照片列表，包含该日期拍摄的所有照片。
     */
    private data class DateGroup(
        val dateKey: String,
        val dateLabel: String,
        val photos: MutableList<PhotoItem>
    )

    /**
     * 照片数据类，用于存储单张照片的元数据信息。
     * @param id 照片在媒体库中的唯一标识符。
     * @param uri 照片的 URI 地址，用于加载和显示图片。
     * @param dateAdded 照片添加到设备的时间戳，单位为秒。
     * @param isVideo 是否为视频文件，默认为 false 表示是图片。
     */
    private data class PhotoItem(
        val id: Long,
        val uri: android.net.Uri,
        val dateAdded: Long,
        val isVideo: Boolean = false
    )

    /**
     * RecyclerView 列表项类型的密封类，用于区分不同类型的列表项。
     * DateHeaderItem: 日期标签项，用于显示日期分组标题。
     * PhotoItem: 照片项，用于显示单张照片。
     */
    private sealed class RecyclerViewItem {
        /**
         * 日期标签项，包含一个日期分组信息。
         */
        data class DateHeaderItem(val dateGroup: DateGroup) : RecyclerViewItem()
        
        /**
         * 照片项，包含一张照片的信息。
         */
        data class PhotoItem(val photo: SquareFragment.PhotoItem) : RecyclerViewItem()
    }

    /**
     * 相机权限请求的 ActivityResultLauncher，用于处理用户对相机权限的授权结果。
     * 如果用户授予权限，则打开相机；否则显示提示信息。
     */
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openCamera()
        } else {
            Toast.makeText(requireContext(), "需要相机权限才能拍照", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 拍照结果的 ActivityResultLauncher，用于处理拍照完成后的回调。
     * 如果拍照成功且照片 URI 和文件都存在，则处理拍照结果；否则显示失败提示。
     */
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && currentPhotoUri != null && currentPhotoFile != null) {
            handlePhotoTaken()
        } else {
            Toast.makeText(requireContext(), "拍照失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 图片查看 Activity 的结果回调，用于在删除图片后刷新列表。
     */
    private val photoViewLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // 如果图片被删除（RESULT_OK），刷新照片列表
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            loadPhotos()
        }
    }

    /**
     * Fragment 视图创建完成后的回调方法，用于初始化所有 UI 组件和功能。
     * @param view 根视图对象。
     * @param savedInstanceState 保存的实例状态，如果之前有保存则不为空。
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 绑定视图绑定对象
        _binding = FragmentSquareBinding.bind(view)
        
        // 初始化 RecyclerView 列表
        initRecyclerView()
        // 初始化筛选按钮
        initFilters()
        // 初始化底部操作栏
        initBottomActions()
        // 初始化相机功能
        initCamera()
        // 检查权限并加载照片
        checkPermissionAndLoadPhotos()
    }

    /**
     * 初始化 RecyclerView，设置网格布局管理器和适配器。
     * 使用 3 列网格布局，日期标签占据整行，照片占据 1 列。
     */
    private fun initRecyclerView() {
        // 创建照片适配器
        photoAdapter = PhotoAdapter()
        // 创建 4 列网格布局管理器
        val layoutManager = GridLayoutManager(requireContext(), 4)
        // 设置 SpanSizeLookup，让日期标签占据整行，照片占据 1 列
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            /**
             * 根据位置返回该位置项占据的列数。
             * @param position 列表项的位置。
             * @return 占据的列数，日期标签返回 3，照片返回 1。
             */
            override fun getSpanSize(position: Int): Int {
                return when (photoAdapter.getItemViewType(position)) {
                    SquareFragment.VIEW_TYPE_DATE_HEADER -> 4 // 日期标签占据整行
                    SquareFragment.VIEW_TYPE_PHOTO -> 1 // 照片占据 1 列
                    else -> 1
                }
            }
        }
        // 设置布局管理器和适配器
        binding.recyclerPhotos.layoutManager = layoutManager
        binding.recyclerPhotos.adapter = photoAdapter
        
        // 优化 RecyclerView 的缓存和预加载，提升图片加载性能
        binding.recyclerPhotos.setItemViewCacheSize(20) // 增加 ViewHolder 缓存数量
        binding.recyclerPhotos.setHasFixedSize(true) // 如果 item 大小固定，可以提升性能
    }

    /**
     * 初始化筛选按钮，为每个筛选按钮设置点击监听器，并默认选中"所有"筛选类型。
     */
    private fun initFilters() {
        // 设置"所有"筛选按钮的点击事件
        binding.filterAll.setOnClickListener { setFilter(FilterType.ALL) }
        // 设置"日"筛选按钮的点击事件
        binding.filterDay.setOnClickListener { setFilter(FilterType.DAY) }
        // 设置"月"筛选按钮的点击事件
        binding.filterMonth.setOnClickListener { setFilter(FilterType.MONTH) }
        // 设置"年"筛选按钮的点击事件
        binding.filterYear.setOnClickListener { setFilter(FilterType.YEAR) }
        
        // 默认选中"所有"筛选类型
        setFilter(FilterType.ALL)
    }

    /**
     * 初始化底部操作栏，为取消选择和拼图按钮设置点击监听器。
     */
    private fun initBottomActions() {
        // 设置取消选择按钮的点击事件，退出选择模式
        binding.btnCancelSelect.setOnClickListener {
            exitSelectionMode()
        }
        
        // 设置拼图按钮的点击事件，开始拼图操作
        binding.btnCollage.setOnClickListener {
            startCollage()
        }
    }

    /**
     * 初始化相机功能，为相机按钮设置点击监听器。
     */
    private fun initCamera() {
        // 设置相机按钮的点击事件，检查权限并打开相机
        binding.btnCamera.setOnClickListener {
            checkCameraPermissionAndOpen()
        }
    }

    /**
     * 检查相机权限并打开相机，如果已有权限则直接打开，否则请求权限。
     */
    private fun checkCameraPermissionAndOpen() {
        if (CameraHelper.hasCameraPermission(requireContext())) {
            // 已有权限，直接打开相机
            openCamera()
        } else {
            // 没有权限，请求相机权限
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    /**
     * 打开相机应用进行拍照，创建拍照 Intent 并启动拍照流程。
     */
    private fun openCamera() {
        // 创建拍照 Intent，获取照片 URI 和文件对象
        val result = CameraHelper.createTakePictureIntent(requireContext())
        if (result != null) {
            val (intent, photoUri, photoFile) = result
            // 保存当前拍照的 URI 和文件，用于后续处理
            currentPhotoUri = photoUri
            currentPhotoFile = photoFile
            
            // 启动拍照流程，使用 CameraHelper 返回的文件对象确保路径正确
            takePictureLauncher.launch(photoUri)
        } else {
            // 无法创建拍照 Intent，显示错误提示
            Toast.makeText(requireContext(), "无法打开相机", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 处理拍照结果，验证照片文件并保存到相册，然后刷新照片列表。
     */
    private fun handlePhotoTaken() {
        // 检查照片文件是否存在
        if (currentPhotoFile == null) {
            Toast.makeText(requireContext(), "照片文件不存在", Toast.LENGTH_SHORT).show()
            return
        }

        // 检查文件是否存在且不为空
        if (!currentPhotoFile!!.exists() || currentPhotoFile!!.length() == 0L) {
            Toast.makeText(requireContext(), "照片文件无效", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // 相机应用已经将照片写入到 currentPhotoFile 中了，直接保存到相册
            val savedUri = CameraHelper.savePhotoToGallery(requireContext(), currentPhotoFile!!)
            
            if (savedUri != null) {
                // 保存成功，显示提示并刷新照片列表
                Toast.makeText(requireContext(), "照片已保存", Toast.LENGTH_SHORT).show()
                loadPhotos()
            } else {
                // 保存失败，显示错误提示
                Toast.makeText(requireContext(), "保存照片失败", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            // 处理过程中发生异常，打印堆栈并显示错误提示
            e.printStackTrace()
            Toast.makeText(requireContext(), "处理照片失败: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            // 清理临时文件引用，释放资源
            currentPhotoUri = null
            currentPhotoFile = null
        }
    }

    /**
     * 设置筛选类型，如果与当前筛选类型相同则直接返回，否则更新筛选类型并刷新 UI 和照片列表。
     * @param filterType 要设置的筛选类型。
     */
    private fun setFilter(filterType: FilterType) {
        // 如果筛选类型相同，无需更新
        if (currentFilter == filterType) return
        // 更新当前筛选类型
        currentFilter = filterType

        // 更新筛选按钮的 UI 状态
        updateFilterUI()
        
        // 根据新的筛选类型重新加载照片
        loadPhotos()
    }

    /**
     * 更新筛选按钮的 UI 状态，根据当前选中的筛选类型高亮对应的按钮。
     */
    private fun updateFilterUI() {
        // 创建筛选按钮和筛选类型的映射列表
        val filters = listOf(
            binding.filterAll to FilterType.ALL,
            binding.filterDay to FilterType.DAY,
            binding.filterMonth to FilterType.MONTH,
            binding.filterYear to FilterType.YEAR
        )

        // 遍历所有筛选按钮，更新选中和未选中状态
        filters.forEach { (view, type) ->
            if (type == currentFilter) {
                // 选中状态：设置选中背景和白色文字
                view.setBackgroundResource(R.drawable.bg_segmented_item_selected)
                view.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
            } else {
                // 未选中状态：设置未选中背景和灰色文字
                view.setBackgroundResource(R.drawable.bg_segmented_item_unselected)
                view.setTextColor(0xFF999999.toInt())
            }
        }
    }

    /**
     * 检查相册读取权限并加载照片，根据 Android 版本选择相应的权限类型。
     * Android 13 (API 33) 及以上使用 READ_MEDIA_IMAGES，以下使用 READ_EXTERNAL_STORAGE。
     */
    private fun checkPermissionAndLoadPhotos() {
        // 根据 Android 版本选择相应的权限类型
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13 及以上使用新的媒体权限
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            // Android 13 以下使用存储权限
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        // 检查权限是否已授予
        if (ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED) {
            // 已有权限，直接加载照片
            loadPhotos()
        } else {
            // 没有权限，请求权限
            requestPermissions(arrayOf(permission), REQUEST_PERMISSION)
        }
    }

    /**
     * 权限请求结果回调，处理用户对相册权限的授权结果。
     * @param requestCode 请求码，用于区分不同的权限请求。
     * @param permissions 请求的权限数组。
     * @param grantResults 权限授权结果数组。
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // 检查是否为相册权限请求且用户已授予权限
        if (requestCode == REQUEST_PERMISSION && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // 权限已授予，加载照片
            loadPhotos()
        } else {
            // 权限被拒绝，显示提示信息
            binding.tvEmpty.visibility = View.VISIBLE
            binding.tvEmpty.text = "需要相册权限才能查看照片"
        }
    }

    /**
     * 从系统媒体库加载照片并按日期分组，根据当前筛选类型过滤照片。
     */
    private fun loadPhotos() {
        // 创建照片列表用于存储查询结果
        val allPhotos = mutableListOf<PhotoItem>()
        
        // 定义要查询的媒体库字段
        val projection = arrayOf(
            MediaStore.Images.Media._ID,           // 照片 ID
            MediaStore.Images.Media.DATA,           // 照片路径
            MediaStore.Images.Media.DATE_ADDED,     // 添加时间
            MediaStore.Images.Media.MIME_TYPE       // MIME 类型
        )

        // 获取时间筛选条件和参数
        val selection = getTimeSelection()
        val selectionArgs = getTimeSelectionArgs()

        // 按添加时间倒序排列，最新的照片在前
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        // 查询系统媒体库中的照片
        android.util.Log.d("SquareFragment", "开始查询照片，筛选类型: $currentFilter")
        val cursor = requireContext().contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )
        
        cursor?.use {
            // 获取各字段的列索引
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)

            android.util.Log.d("SquareFragment", "查询到 ${cursor.count} 张照片")
            
            // 遍历查询结果，创建 PhotoItem 对象
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val data = cursor.getString(dataColumn)
                val dateAdded = cursor.getLong(dateAddedColumn)
                val mimeType = cursor.getString(mimeTypeColumn)

                // 构建照片的 URI，使用 ContentUris.withAppendedId 更可靠（Android 10+ 兼容）
                // 这种方式在所有 Android 版本都有效，推荐使用
                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                // 判断是否为视频文件，并添加到照片列表
                allPhotos.add(PhotoItem(id, uri, dateAdded, mimeType?.startsWith("video/") == true))
            }
        } ?: android.util.Log.e("SquareFragment", "查询照片失败，cursor 为 null")

        android.util.Log.d("SquareFragment", "共加载 ${allPhotos.size} 张照片")

        // 按日期分组照片
        groupPhotosByDate(allPhotos)
        
        android.util.Log.d("SquareFragment", "分组后共有 ${dateGroups.size} 个日期组")
        
        // 使用 DiffUtil 智能更新 UI，避免全量刷新造成抖动
        photoAdapter.updateItems()
        // 根据是否有照片显示或隐藏空状态提示
        binding.tvEmpty.visibility = if (dateGroups.isEmpty()) View.VISIBLE else View.GONE
    }

    /**
     * 按日期分组照片，将照片列表按照拍摄日期进行分组。
     * @param photos 要分组的照片列表。
     */
    private fun groupPhotosByDate(photos: List<PhotoItem>) {
        // 清空现有的日期分组
        dateGroups.clear()
        
        // 使用 Map 按日期键分组照片
        val dateMap = mutableMapOf<String, MutableList<PhotoItem>>()
        
        // 遍历所有照片，按日期分组
        photos.forEach { photo ->
            // 将时间戳转换为 Calendar 对象
            val calendar = Calendar.getInstance().apply {
                timeInMillis = photo.dateAdded * 1000
            }
            
            // 生成日期键，格式为 yyyy-MM-dd
            val dateKey = String.format(
                Locale.getDefault(),
                "%04d-%02d-%02d",
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            
            // 如果该日期键不存在，创建新的列表
            if (!dateMap.containsKey(dateKey)) {
                dateMap[dateKey] = mutableListOf()
            }
            // 将照片添加到对应日期的列表中
            dateMap[dateKey]?.add(photo)
        }
        
        // 转换为 DateGroup 列表，按日期倒序排列（最新的日期在前）
        dateGroups.addAll(
            dateMap.map { (dateKey, photoList) ->
                DateGroup(
                    dateKey = dateKey,
                    dateLabel = formatDateLabel(dateKey), // 格式化日期标签
                    photos = photoList
                )
            }.sortedByDescending { it.dateKey }
        )
        
        // 默认展开所有日期组，将所有日期键添加到展开集合中
        expandedDates.clear()
        dateGroups.forEach { dateGroup ->
            expandedDates.add(dateGroup.dateKey)
        }
    }

    /**
     * 格式化日期标签，根据日期与今天的关系返回不同的显示格式。
     * @param dateKey 日期键，格式为 yyyy-MM-dd。
     * @return 格式化后的日期标签，格式为：今天、昨天、12月27日、2023年12月27日等。
     */
    private fun formatDateLabel(dateKey: String): String {
        // 解析日期键，分割为年、月、日
        val parts = dateKey.split("-")
        if (parts.size != 3) return dateKey
        
        // 提取年、月、日数值
        val year = parts[0].toIntOrNull() ?: 0
        val month = parts[1].toIntOrNull() ?: 0
        val day = parts[2].toIntOrNull() ?: 0
        
        // 创建目标日期的 Calendar 对象，时间设为 00:00:00
        val calendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1) // Calendar 的月份从 0 开始
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        // 创建今天的 Calendar 对象，时间设为 00:00:00
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        // 创建昨天的 Calendar 对象，时间设为 00:00:00
        val yesterday = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, -1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        // 根据日期与今天的关系返回相应的标签
        return when {
            // 如果是今天，显示"今天"
            calendar.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            calendar.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> {
                "今天"
            }
            // 如果是昨天，显示"昨天"
            calendar.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
            calendar.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR) -> {
                "昨天"
            }
            // 如果是今年但不是今天或昨天，只显示月日
            calendar.get(Calendar.YEAR) == today.get(Calendar.YEAR) -> {
                "${month}月${day}日"
            }
            // 如果是往年，显示完整的年月日
            else -> {
                "${year}年${month}月${day}日"
            }
        }
    }

    /**
     * 获取时间筛选的 SQL WHERE 条件字符串，用于查询媒体库时过滤照片。
     * @return WHERE 条件字符串，如果不需要筛选则返回 null。
     */
    private fun getTimeSelection(): String? {
        return when (currentFilter) {
            // 显示所有照片，不需要筛选条件
            FilterType.ALL -> null
            // 显示今天的照片，筛选条件为添加时间大于等于今天 00:00:00
            FilterType.DAY -> "${MediaStore.Images.Media.DATE_ADDED} >= ?"
            // 显示本月的照片，筛选条件为添加时间大于等于本月 1 日 00:00:00
            FilterType.MONTH -> "${MediaStore.Images.Media.DATE_ADDED} >= ?"
            // 显示本年的照片，筛选条件为添加时间大于等于本年 1 月 1 日 00:00:00
            FilterType.YEAR -> "${MediaStore.Images.Media.DATE_ADDED} >= ?"
        }
    }

    /**
     * 获取时间筛选的参数数组，用于填充 SQL WHERE 条件中的占位符。
     * @return 参数数组，如果不需要筛选则返回 null。
     */
    private fun getTimeSelectionArgs(): Array<String>? {
        val calendar = Calendar.getInstance()

        return when (currentFilter) {
            // 显示所有照片，不需要参数
            FilterType.ALL -> null
            // 显示今天的照片，参数为今天 00:00:00 的时间戳
            FilterType.DAY -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                arrayOf((calendar.timeInMillis / 1000).toString())
            }
            // 显示本月的照片，参数为本月 1 日 00:00:00 的时间戳
            FilterType.MONTH -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                arrayOf((calendar.timeInMillis / 1000).toString())
            }
            // 显示本年的照片，参数为本年 1 月 1 日 00:00:00 的时间戳
            FilterType.YEAR -> {
                calendar.set(Calendar.MONTH, 0)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                arrayOf((calendar.timeInMillis / 1000).toString())
            }
        }
    }

    /**
     * 照片适配器 - 支持日期标签和照片两种类型
     */
    private inner class PhotoAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        /**
         * 当前列表项数据
         */
        private var items: List<RecyclerViewItem> = emptyList()

        /**
         * 获取列表项，将日期分组转换为 RecyclerView 的列表项。
         * @return 包含日期标签和照片项的列表。
         */
        private fun getItems(): List<RecyclerViewItem> {
            val newItems = mutableListOf<RecyclerViewItem>()
            
            // 遍历所有日期分组
            dateGroups.forEach { dateGroup ->
                // 添加日期标签项
                newItems.add(RecyclerViewItem.DateHeaderItem(dateGroup))
                
                // 默认展开所有日期组，直接添加该日期下的所有照片
                dateGroup.photos.forEach { photo ->
                    newItems.add(RecyclerViewItem.PhotoItem(photo))
                }
            }
            
            return newItems
        }

        /**
         * 更新数据并智能刷新
         * 使用 DiffUtil 计算差异，只更新变化的 item，避免全量刷新造成抖动
         */
        fun updateItems() {
            val newItems = getItems()
            // 如果是首次加载（items 为空），直接设置数据并通知全部更新
            if (items.isEmpty()) {
                items = newItems
                notifyDataSetChanged()
            } else {
                // 使用 DiffUtil 计算差异，只更新变化的 item
                val diffCallback = PhotoDiffCallback(items, newItems)
                val diffResult = DiffUtil.calculateDiff(diffCallback)
                items = newItems
                diffResult.dispatchUpdatesTo(this)
            }
        }


        /**
         * 创建 ViewHolder，根据 viewType 创建不同类型的 ViewHolder。
         * @param parent 父视图组。
         * @param viewType 视图类型，用于区分日期标签和照片。
         * @return 创建的 ViewHolder 对象。
         */
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return when (viewType) {
                // 创建日期标签 ViewHolder
                SquareFragment.VIEW_TYPE_DATE_HEADER -> {
                    val binding = ItemDateHeaderBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    )
                    DateHeaderViewHolder(binding)
                }
                // 创建照片 ViewHolder
                SquareFragment.VIEW_TYPE_PHOTO -> {
                    val binding = ItemPhotoGridBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    )
                    PhotoViewHolder(binding)
                }
                // 未知的视图类型，抛出异常
                else -> throw IllegalArgumentException("Unknown view type: $viewType")
            }
        }

        /**
         * 绑定 ViewHolder，将数据绑定到 ViewHolder 上。
         * @param holder 要绑定的 ViewHolder。
         * @param position 列表项的位置。
         */
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                // 绑定日期标签项
                is RecyclerViewItem.DateHeaderItem -> {
                    (holder as DateHeaderViewHolder).bind(item.dateGroup)
                }
                // 绑定照片项
                is RecyclerViewItem.PhotoItem -> {
                    (holder as PhotoViewHolder).bind(item.photo)
                }
            }
        }

        /**
         * ViewHolder 被回收时的回调，用于清理资源。
         * @param holder 被回收的 ViewHolder。
         */
        override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
            super.onViewRecycled(holder)
            // 当 ViewHolder 被回收时，清除 Glide 请求，防止图片错位
            if (holder is PhotoViewHolder) {
                holder.clear()
            }
        }

        /**
         * 获取列表项总数。
         * @return 列表项的数量。
         */
        override fun getItemCount(): Int = items.size

        /**
         * 获取指定位置的视图类型。
         * @param position 列表项的位置。
         * @return 视图类型常量。
         */
        override fun getItemViewType(position: Int): Int {
            return when (items[position]) {
                is RecyclerViewItem.DateHeaderItem -> SquareFragment.VIEW_TYPE_DATE_HEADER
                is RecyclerViewItem.PhotoItem -> SquareFragment.VIEW_TYPE_PHOTO
            }
        }

        /**
         * 根据照片 ID 查找对应的 position
         * @param photoId 照片 ID
         * @return position，如果未找到返回 RecyclerView.NO_POSITION
         */
        fun findPositionByPhotoId(photoId: Long): Int {
            items.forEachIndexed { index, item ->
                if (item is RecyclerViewItem.PhotoItem && item.photo.id == photoId) {
                    return index
                }
            }
            return RecyclerView.NO_POSITION
        }

        /**
         * 日期标签 ViewHolder，用于显示日期分组标题。
         * @param binding 日期标签项的数据绑定对象。
         */
        inner class DateHeaderViewHolder(
            private val binding: ItemDateHeaderBinding
        ) : RecyclerView.ViewHolder(binding.root) {

            /**
             * 绑定日期分组数据到视图。
             * @param dateGroup 日期分组数据对象。
             */
            fun bind(dateGroup: DateGroup) {
                // 只显示日期标签，不需要展开/折叠功能
                binding.tvDate.text = dateGroup.dateLabel
            }
        }

        /**
         * 照片 ViewHolder，用于显示单张照片。
         * @param binding 照片项的数据绑定对象。
         */
        inner class PhotoViewHolder(
            private val binding: ItemPhotoGridBinding
        ) : RecyclerView.ViewHolder(binding.root) {

            /**
             * 清理 ViewHolder 资源，用于 ViewHolder 被回收时调用。
             * 注意：不要在这里清除 Glide 请求，因为可能会取消正在加载的图片。
             * 只在 bind() 时清除，确保新的加载请求能正常进行。
             */
            fun clear() {
                // 只清除 tag，不清除 Glide 请求，避免取消正在加载的图片
                binding.ivPhoto.tag = null
            }

            /**
             * 绑定照片数据到视图，设置图片、选中状态和点击事件。
             * @param photo 照片数据对象。
             */
            fun bind(photo: PhotoItem) {
                // 关键：先清除之前的图片和 Glide 请求，防止 ViewHolder 复用导致的图片错位
                // 使用 Fragment 作为上下文，而不是 View，确保请求不会被意外取消
                Glide.with(this@SquareFragment).clear(binding.ivPhoto)
                binding.ivPhoto.setImageDrawable(null)
                
                // 使用 tag 标记当前要加载的照片 ID，确保加载到正确的 View
                binding.ivPhoto.tag = photo.id
                
                // 设置 item 为正方形（宽度 = 高度）
                // 先立即设置一个初始高度，避免 ImageView 高度为 0
                val rootWidth = binding.root.width
                if (rootWidth > 0) {
                    val layoutParams = binding.root.layoutParams
                    layoutParams.height = rootWidth
                    binding.root.layoutParams = layoutParams
                } else {
                    // 如果宽度还没测量出来，使用 post 延迟设置
                    binding.root.post {
                        val width = binding.root.width
                        if (width > 0) {
                            val layoutParams = binding.root.layoutParams
                            layoutParams.height = width
                            binding.root.layoutParams = layoutParams
                        }
                    }
                }
                
                // 使用 Glide 加载图片，添加错误处理和占位符
                // 使用 Fragment 作为上下文，确保请求不会被 ViewHolder 回收影响
                // 在 Android 10+ 中，需要确保 URI 格式正确并使用合适的加载方式
                // 确保 Fragment 已经 attached 再加载
                if (!this@SquareFragment.isAdded || this@SquareFragment.isDetached) {
                    android.util.Log.w("SquareFragment", "Fragment 未 attached，跳过图片加载: id=${photo.id}, uri=${photo.uri}")
                    return
                }
                
                // 添加调试日志（在检查 Fragment 状态之后）
                android.util.Log.d("SquareFragment", "开始加载图片: id=${photo.id}, uri=${photo.uri}, tag=${binding.ivPhoto.tag}")
                
                // 确保 ImageView 可见
                binding.ivPhoto.visibility = View.VISIBLE
                binding.ivPhoto.alpha = 1f
                
                try {
                    // 先设置一个占位背景色，确保 ImageView 可见
                    binding.ivPhoto.setBackgroundColor(0xFFE0E0E0.toInt())
                    
                    Glide.with(this@SquareFragment)
                        .asDrawable()
                        .load(photo.uri)
                        .centerCrop()
                        .placeholder(0xFFE0E0E0.toInt()) // 使用浅灰色占位符，确保可见
                        .error(android.R.color.darker_gray) // 加载失败时的占位符
                        .fallback(android.R.color.darker_gray) // URI 为 null 时的占位符
                        .signature(com.bumptech.glide.signature.ObjectKey(photo.id)) // 添加签名确保缓存正确
                        .skipMemoryCache(false) // 启用内存缓存
                        .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL) // 启用磁盘缓存
                        .listener(object : com.bumptech.glide.request.RequestListener<Drawable> {
                            override fun onLoadFailed(
                                e: GlideException?,
                                model: Any?,
                                target: Target<Drawable>,
                                isFirstResource: Boolean
                            ): Boolean {
                                // 图片加载失败，打印详细日志便于调试
                                android.util.Log.e("SquareFragment", "图片加载失败: id=${photo.id}, uri=${photo.uri}, error: ${e?.message}")
                                e?.logRootCauses("SquareFragment")
                                return false
                            }

                        override fun onResourceReady(
                            resource: Drawable,
                            model: Any,
                            target: Target<Drawable>?,
                            dataSource: DataSource,
                            isFirstResource: Boolean
                        ): Boolean {
                            // 检查 tag 是否匹配，确保加载到正确的 View
                            val currentTag = binding.ivPhoto.tag
                            if (currentTag != null && currentTag != photo.id) {
                                android.util.Log.w("SquareFragment", "ViewHolder 已被复用，忽略此图片加载: 期望=${photo.id}, 实际=$currentTag, uri=${photo.uri}")
                                return true // 返回 true 表示已处理，不显示图片
                            }
                            // 检查 ImageView 的尺寸和可见性
                            val width = binding.ivPhoto.width
                            val height = binding.ivPhoto.height
                            val visibility = binding.ivPhoto.visibility
                            android.util.Log.d("SquareFragment", "图片加载成功: id=${photo.id}, uri=${photo.uri}, tag=$currentTag, dataSource=$dataSource, ImageView尺寸=${width}x${height}, visibility=$visibility")
                            
                            // 如果 ImageView 高度为 0，可能是布局问题
                            if (height == 0) {
                                android.util.Log.w("SquareFragment", "警告：ImageView 高度为 0，图片可能无法显示: id=${photo.id}")
                            }
                            
                            // 确保图片显示：清除背景色，确保图片可见
                            binding.ivPhoto.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            
                            // 验证图片是否真的设置了
                            val drawable = binding.ivPhoto.drawable
                            android.util.Log.d("SquareFragment", "图片设置后检查: id=${photo.id}, drawable=${drawable != null}, drawable类型=${drawable?.javaClass?.simpleName}")
                            
                            return false // 返回 false 让 Glide 显示图片
                        }
                        })
                        .into(binding.ivPhoto)
                } catch (e: Exception) {
                    android.util.Log.e("SquareFragment", "加载图片异常: id=${photo.id}, uri=${photo.uri}", e)
                }

                // 如果是视频，显示视频图标；否则隐藏
                binding.ivVideoIcon.visibility = if (photo.isVideo) View.VISIBLE else View.GONE

                // 更新选中状态，根据是否在选择模式和是否被选中来显示选中覆盖层和选中图标
                val isSelected = selectedPhotos.contains(photo.id)
                binding.vSelectedOverlay.visibility = if (isSelected && isSelectionMode) View.VISIBLE else View.GONE
                binding.ivSelectedCheck.visibility = if (isSelected && isSelectionMode) View.VISIBLE else View.GONE

                // 设置点击事件
                binding.root.setOnClickListener {
                    if (isSelectionMode) {
                        // 选择模式：切换选中状态
                        togglePhotoSelection(photo.id)
                    } else {
                        // 普通模式：打开照片详情页面，放大显示图片
                        val intent = android.content.Intent(requireContext(), com.happy.jesshome.ui.photo.PhotoViewActivity::class.java).apply {
                            putExtra("photo_uri", photo.uri)
                        }
                        // 使用 ActivityResultLauncher 启动，以便在删除后刷新列表
                        photoViewLauncher.launch(intent)
                    }
                }

                // 设置长按事件：进入选择模式
                binding.root.setOnLongClickListener {
                    if (!isSelectionMode) {
                        // 如果不在选择模式，进入选择模式并选中当前照片
                        enterSelectionMode(photo.id)
                        true
                    } else {
                        // 如果已在选择模式，不处理长按事件
                        false
                    }
                }
            }
        }
    }

    /**
     * 进入选择模式，显示底部操作栏并选中被长按的照片。
     * @param photoId 被长按的照片 ID，只刷新该 item 以避免全量刷新造成抖动。
     */
    private fun enterSelectionMode(photoId: Long) {
        // 设置选择模式标志为 true
        isSelectionMode = true
        // 显示底部操作栏
        binding.llBottomAction.visibility = View.VISIBLE
        // 先选中被长按的照片
        selectedPhotos.add(photoId)
        // 更新选中数量显示
        updateSelectedCount()
        // 只刷新被长按的那个 item，避免全量刷新造成抖动
        val position = photoAdapter.findPositionByPhotoId(photoId)
        if (position != RecyclerView.NO_POSITION) {
            photoAdapter.notifyItemChanged(position)
        }
    }

    /**
     * 退出选择模式，隐藏底部操作栏并清除所有选中状态。
     */
    private fun exitSelectionMode() {
        // 设置选择模式标志为 false
        isSelectionMode = false
        // 先保存选中的照片 ID 列表，用于后续刷新
        val selectedPhotoIds = selectedPhotos.toList()
        // 清空选中集合
        selectedPhotos.clear()
        // 隐藏底部操作栏
        binding.llBottomAction.visibility = View.GONE
        // 更新选中数量显示
        updateSelectedCount()
        // 只刷新之前选中的 items，避免全量刷新造成抖动
        selectedPhotoIds.forEach { photoId ->
            val position = photoAdapter.findPositionByPhotoId(photoId)
            if (position != RecyclerView.NO_POSITION) {
                photoAdapter.notifyItemChanged(position)
            }
        }
    }

    /**
     * 切换照片选中状态，如果已选中则取消选中，如果未选中则选中。
     * @param photoId 要切换选中状态的照片 ID。
     */
    private fun togglePhotoSelection(photoId: Long) {
        // 根据当前选中状态切换
        if (selectedPhotos.contains(photoId)) {
            selectedPhotos.remove(photoId)
        } else {
            selectedPhotos.add(photoId)
        }
        // 更新选中数量显示
        updateSelectedCount()
        
        // 找到对应的 position 并只刷新该 item，避免全量刷新造成抖动
        val position = photoAdapter.findPositionByPhotoId(photoId)
        if (position != RecyclerView.NO_POSITION) {
            photoAdapter.notifyItemChanged(position)
        }
    }

    /**
     * 更新选中数量显示，根据选中数量更新文本和拼图按钮的可用状态。
     */
    private fun updateSelectedCount() {
        val count = selectedPhotos.size
        // 更新选中数量文本
        binding.tvSelectedCount.text = "已选择 $count 张"
        // 根据是否有选中照片来启用或禁用拼图按钮
        binding.btnCollage.isEnabled = count > 0
        // 根据是否有选中照片来设置按钮透明度
        binding.btnCollage.alpha = if (count > 0) 1f else 0.5f
    }

    /**
     * 开始拼图，将选中的照片传递给拼图页面并启动拼图 Activity。
     */
    private fun startCollage() {
        // 如果没有选中照片，直接返回
        if (selectedPhotos.isEmpty()) {
            return
        }

        // 获取选中的照片 URI 列表
        val selectedUris = mutableListOf<android.net.Uri>()
        dateGroups.forEach { dateGroup ->
            dateGroup.photos.forEach { photo ->
                if (selectedPhotos.contains(photo.id)) {
                    selectedUris.add(photo.uri)
                }
            }
        }

        // 如果没有有效的 URI，直接返回
        if (selectedUris.isEmpty()) {
            return
        }

        // 创建 Intent 并传递选中的照片 URI 列表，打开拼图页面
        val intent = android.content.Intent(requireContext(), com.happy.jesshome.ui.collage.CollageActivity::class.java).apply {
            putParcelableArrayListExtra("photos", ArrayList(selectedUris))
        }
        startActivity(intent)
        
        // 退出选择模式
        exitSelectionMode()
    }


    /**
     * Fragment 视图销毁时的回调方法，用于清理 ViewBinding 引用以避免内存泄漏。
     */
    override fun onDestroyView() {
        super.onDestroyView()
        // 将 ViewBinding 引用置空，避免内存泄漏
        _binding = null
    }

    /**
     * 伴生对象，包含 Fragment 的常量定义。
     */
    companion object {
        /**
         * 权限请求码，用于区分不同的权限请求。
         */
        private const val REQUEST_PERMISSION = 1001
        
        /**
         * RecyclerView 日期标签项的视图类型常量。
         */
        const val VIEW_TYPE_DATE_HEADER = 0
        
        /**
         * RecyclerView 照片项的视图类型常量。
         */
        const val VIEW_TYPE_PHOTO = 1
    }

    /**
     * DiffUtil Callback 用于智能计算列表差异，优化 RecyclerView 的更新性能。
     * 通过比较新旧列表的差异，只更新变化的 item，避免全量刷新造成界面抖动。
     * @param oldList 旧的列表数据。
     * @param newList 新的列表数据。
     */
    private class PhotoDiffCallback(
        private val oldList: List<RecyclerViewItem>,
        private val newList: List<RecyclerViewItem>
    ) : DiffUtil.Callback() {

        /**
         * 获取旧列表的大小。
         * @return 旧列表的项数。
         */
        override fun getOldListSize(): Int = oldList.size

        /**
         * 获取新列表的大小。
         * @return 新列表的项数。
         */
        override fun getNewListSize(): Int = newList.size

        /**
         * 判断两个位置的 item 是否是同一个对象（通过唯一标识符判断）。
         * @param oldItemPosition 旧列表中 item 的位置。
         * @param newItemPosition 新列表中 item 的位置。
         * @return 如果两个 item 是同一个对象则返回 true，否则返回 false。
         */
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]
            
            // 如果类型不同，肯定不是同一个 item
            if (oldItem::class != newItem::class) {
                return false
            }
            
            return when {
                // 日期标签通过 dateKey 判断是否相同
                oldItem is RecyclerViewItem.DateHeaderItem && newItem is RecyclerViewItem.DateHeaderItem -> {
                    oldItem.dateGroup.dateKey == newItem.dateGroup.dateKey
                }
                // 照片通过 id 判断是否相同
                oldItem is RecyclerViewItem.PhotoItem && newItem is RecyclerViewItem.PhotoItem -> {
                    oldItem.photo.id == newItem.photo.id
                }
                else -> false
            }
        }

        /**
         * 判断两个位置的 item 内容是否相同（在 areItemsTheSame 返回 true 的前提下）。
         * @param oldItemPosition 旧列表中 item 的位置。
         * @param newItemPosition 新列表中 item 的位置。
         * @return 如果两个 item 内容相同则返回 true，否则返回 false。
         */
        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]
            
            // 如果类型不同，内容肯定不同
            if (oldItem::class != newItem::class) {
                return false
            }
            
            return when {
                // 日期标签内容相同（dateLabel 可能变化，但 dateKey 相同即可）
                oldItem is RecyclerViewItem.DateHeaderItem && newItem is RecyclerViewItem.DateHeaderItem -> {
                    oldItem.dateGroup.dateKey == newItem.dateGroup.dateKey &&
                    oldItem.dateGroup.dateLabel == newItem.dateGroup.dateLabel
                }
                // 照片内容相同（id 相同即可，其他属性一般不会变化）
                oldItem is RecyclerViewItem.PhotoItem && newItem is RecyclerViewItem.PhotoItem -> {
                    oldItem.photo.id == newItem.photo.id
                }
                else -> false
            }
        }
    }
}

