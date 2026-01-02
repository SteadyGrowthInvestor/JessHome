package com.happy.jesshome.util

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 相机辅助类
 * 提供调用系统相机、保存照片等功能
 */
object CameraHelper {

    /**
     * 检查相机权限
     */
    fun hasCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 检查存储权限（Android 10 以下需要）
     */
    fun hasStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10 及以上不需要存储权限
            true
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * 创建拍照 Intent
     * @param context 上下文
     * @return Triple<Intent, Uri, File> Intent、照片 URI 和照片文件
     */
    fun createTakePictureIntent(context: Context): Triple<Intent, Uri, File>? {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        
        // 检查是否有相机应用
        if (takePictureIntent.resolveActivity(context.packageManager) == null) {
            return null
        }

        // 创建照片文件
        val photoFile = createImageFile(context)
        if (photoFile == null) {
            return null
        }

        // 获取照片 URI（使用 FileProvider 确保安全）
        val photoUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )

        // 添加 URI 到 Intent
        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
        
        // 授予临时权限
        takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        return Triple(takePictureIntent, photoUri, photoFile)
    }

    /**
     * 创建照片文件（临时文件，用于拍照）
     */
    private fun createImageFile(context: Context): File? {
        return try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val imageFileName = "IMG_$timeStamp.jpg"
            
            // 在缓存目录创建临时文件
            val cacheDir = context.cacheDir
            val imageFile = File(cacheDir, imageFileName)
            
            // 如果文件已存在，先删除
            if (imageFile.exists()) {
                imageFile.delete()
            }
            
            // 创建新文件
            imageFile.createNewFile()
            imageFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 保存照片到相册（从临时文件）
     * @param context 上下文
     * @param tempFile 临时文件
     * @return 保存后的 URI，失败返回 null
     */
    fun savePhotoToGallery(context: Context, tempFile: File): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10 及以上使用 MediaStore
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, tempFile.name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/JessHome")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                )

                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { outputStream ->
                        tempFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }

                    // 更新 IS_PENDING 状态
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(it, contentValues, null, null)

                    // 通知系统相册更新
                    val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                    mediaScanIntent.data = it
                    context.sendBroadcast(mediaScanIntent)

                    return it
                }
            } else {
                // Android 10 以下
                val imagesDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "JessHome"
                )
                if (!imagesDir.exists()) {
                    imagesDir.mkdirs()
                }

                val destFile = File(imagesDir, tempFile.name)
                tempFile.copyTo(destFile, overwrite = true)

                // 通知系统相册更新
                val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                mediaScanIntent.data = Uri.fromFile(destFile)
                context.sendBroadcast(mediaScanIntent)

                Uri.fromFile(destFile)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            // 删除临时文件
            try {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 从 Intent 结果中获取照片 URI
     * @param context 上下文
     * @param intent 拍照返回的 Intent
     * @param photoUri 拍照时使用的 URI
     * @return 照片 URI，失败返回 null
     */
    fun getPhotoUriFromResult(context: Context, intent: Intent?, photoUri: Uri?): Uri? {
        return if (intent != null && intent.data != null) {
            // 有些相机应用会返回缩略图
            intent.data
        } else {
            // 使用我们指定的 URI
            photoUri
        }
    }
}

