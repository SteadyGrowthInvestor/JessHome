package com.happy.jesshome.ui.photo

import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import com.bumptech.glide.Glide
import com.happy.jesshome.base.BaseActivity
import com.happy.jesshome.databinding.ActivityPhotoViewBinding

/**
 * 图片查看 Activity，用于放大显示单张图片。
 * 支持点击关闭、删除图片等功能。
 */
class PhotoViewActivity : BaseActivity() {

    private lateinit var binding: ActivityPhotoViewBinding
    private var photoUri: Uri? = null

    /**
     * Activity 创建时的回调方法。
     * @param savedInstanceState 保存的实例状态。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 设置全屏显示
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        binding = ActivityPhotoViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 获取传递过来的图片 URI
        photoUri = intent.getParcelableExtra<Uri>("photo_uri")
        if (photoUri == null) {
            finish()
            return
        }

        // 使用 Glide 加载图片
        Glide.with(this)
            .load(photoUri)
            .into(binding.ivPhoto)

        // 设置删除按钮点击事件
        binding.ivDelete.setOnClickListener {
            showDeleteConfirmDialog()
        }

        // 点击图片关闭 Activity
        binding.ivPhoto.setOnClickListener {
            finish()
        }

        // 点击背景关闭 Activity（但不包括删除按钮）
        binding.root.setOnClickListener {
            // 如果点击的是根布局（不是删除按钮），则关闭
            finish()
        }
    }

    /**
     * 显示删除确认对话框。
     */
    private fun showDeleteConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("删除图片")
            .setMessage("确定要删除这张图片吗？")
            .setPositiveButton("删除") { _, _ ->
                deletePhoto()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 删除图片。
     * 
     * Android 版本差异说明：
     * - Android 10 (API 29): 引入分区存储，使用 MediaStore.createDeleteRequest() 请求用户确认删除
     * - Android 11-16 (API 30-35): 与 Android 10 相同，都使用 MediaStore.createDeleteRequest()
     * 
     * 注意：不需要在 Manifest 中声明额外权限，系统会通过对话框让用户确认删除。
     */
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

            // 在 Android 10+ 中，需要先请求删除权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    // Android 10+ 使用 PendingIntent 请求删除权限
                    android.util.Log.d("PhotoViewActivity", "尝试创建删除请求: $deleteUri")
                    val pendingIntent = MediaStore.createDeleteRequest(
                        contentResolver,
                        listOf(deleteUri)
                    )
                    android.util.Log.d("PhotoViewActivity", "删除请求创建成功，启动权限对话框")
                    startIntentSenderForResult(
                        pendingIntent.intentSender,
                        REQUEST_CODE_DELETE,
                        null,
                        0,
                        0,
                        0,
                        null
                    )
                } catch (e: Exception) {
                    // 如果无法创建删除请求，可能是系统不支持，尝试直接删除
                    android.util.Log.w("PhotoViewActivity", "无法创建删除请求，尝试直接删除", e)
                    // 先尝试直接删除
                    try {
                        performDelete(deleteUri)
                    } catch (e2: SecurityException) {
                        // 如果直接删除也失败，提示用户
                        android.util.Log.e("PhotoViewActivity", "删除权限不足", e2)
                        Toast.makeText(
                            this,
                            "删除失败：需要删除权限。请在弹出的系统对话框中确认删除",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } else {
                // Android 10 以下直接删除
                performDelete(deleteUri)
            }
        } catch (e: Exception) {
            android.util.Log.e("PhotoViewActivity", "删除图片异常", e)
            Toast.makeText(this, "删除失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 执行删除操作。
     * @param deleteUri 要删除的图片 URI。
     */
    private fun performDelete(deleteUri: Uri) {
        try {
            // 删除图片
            val deletedCount = contentResolver.delete(deleteUri, null, null)

            if (deletedCount > 0) {
                // 删除成功，设置结果并关闭 Activity
                setResult(RESULT_OK)
                Toast.makeText(this, "图片已删除", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                // 删除失败
                Toast.makeText(this, "删除失败，可能没有权限", Toast.LENGTH_SHORT).show()
            }
        } catch (e: SecurityException) {
            android.util.Log.e("PhotoViewActivity", "删除权限不足", e)
            Toast.makeText(
                this,
                "删除失败：没有删除权限。请在系统设置中授予应用删除媒体文件的权限",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: UnsupportedOperationException) {
            android.util.Log.e("PhotoViewActivity", "删除操作不支持", e)
            Toast.makeText(this, "删除失败：系统不支持此操作", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            android.util.Log.e("PhotoViewActivity", "删除图片异常", e)
            Toast.makeText(this, "删除失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 处理删除权限请求的结果。
     * 在 Android 10+ 中，用户通过 MediaStore.createDeleteRequest() 确认后，系统会自动删除文件，
     * 我们只需要检查结果并刷新列表即可。
     */
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: android.content.Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        android.util.Log.d(
            "PhotoViewActivity",
            "onActivityResult: requestCode=$requestCode, resultCode=$resultCode"
        )

        if (requestCode == REQUEST_CODE_DELETE) {
            if (resultCode == RESULT_OK) {
                // 用户授予了删除权限，系统已经自动删除了文件
                android.util.Log.d("PhotoViewActivity", "用户授予了删除权限，文件已被系统删除")
                setResult(RESULT_OK)
                Toast.makeText(this, "图片已删除", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                // 用户拒绝了删除权限
                android.util.Log.w("PhotoViewActivity", "用户拒绝了删除权限")
                Toast.makeText(
                    this,
                    "需要删除权限才能删除图片，请在系统对话框中确认",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    companion object {
        /**
         * 删除权限请求码。
         */
        private const val REQUEST_CODE_DELETE = 1001
    }
}

