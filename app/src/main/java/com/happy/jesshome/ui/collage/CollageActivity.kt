package com.happy.jesshome.ui.collage

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.happy.jesshome.R
import com.happy.jesshome.databinding.ActivityCollageBinding
import com.happy.jesshome.databinding.ItemCollageTemplateBinding
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 拼图 Activity
 * 支持选择拼图模板，将多张照片拼接成一张图片
 */
class CollageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCollageBinding
    private val photoUris = mutableListOf<Uri>()
    private var currentTemplate: CollageTemplate = CollageTemplate.TWO_HORIZONTAL

    /**
     * 拼图模板枚举
     */
    private enum class CollageTemplate(val layoutRes: Int, val templateName: String) {
        TWO_HORIZONTAL(R.layout.template_collage_two_horizontal, "横向两张"),
        TWO_VERTICAL(R.layout.template_collage_two_vertical, "竖向两张"),
        THREE_GRID(R.layout.template_collage_three_grid, "三宫格"),
        FOUR_GRID(R.layout.template_collage_four_grid, "四宫格"),
        SIX_GRID(R.layout.template_collage_six_grid, "六宫格"),
        NINE_GRID(R.layout.template_collage_nine_grid, "九宫格")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCollageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 获取传递的照片URI列表
        val uriList = intent.getParcelableArrayListExtra<Uri>("photos")
        if (uriList != null && uriList.isNotEmpty()) {
            photoUris.addAll(uriList)
        } else {
            Toast.makeText(this, "未选择照片", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initViews()
        initTemplateRecyclerView()
        loadTemplate(currentTemplate)
    }

    /**
     * 初始化视图
     */
    private fun initViews() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnSave.setOnClickListener {
            saveCollage()
        }
    }

    /**
     * 初始化模板选择 RecyclerView
     */
    private fun initTemplateRecyclerView() {
        val templates = CollageTemplate.values().toList()
        binding.recyclerTemplates.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
            this,
            androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL,
            false
        )
        binding.recyclerTemplates.adapter = TemplateAdapter(templates) { template ->
            currentTemplate = template
            loadTemplate(template)
            binding.recyclerTemplates.adapter?.notifyDataSetChanged()
        }
    }

    /**
     * 加载拼图模板
     */
    private fun loadTemplate(template: CollageTemplate) {
        // 根据照片数量自动选择合适的模板
        val maxPhotos = when (template) {
            CollageTemplate.TWO_HORIZONTAL, CollageTemplate.TWO_VERTICAL -> 2
            CollageTemplate.THREE_GRID -> 3
            CollageTemplate.FOUR_GRID -> 4
            CollageTemplate.SIX_GRID -> 6
            CollageTemplate.NINE_GRID -> 9
        }

        val photosToUse = photoUris.take(maxPhotos)

        // 加载模板布局
        val templateView =
            LayoutInflater.from(this).inflate(template.layoutRes, binding.collageContainer, false)
        binding.collageContainer.removeAllViews()
        binding.collageContainer.addView(templateView)

        // 填充照片
        fillPhotosToTemplate(templateView, photosToUse)
    }

    /**
     * 将照片填充到模板中
     */
    private fun fillPhotosToTemplate(templateView: View, photos: List<Uri>) {
        // 根据模板类型查找对应的 ImageView
        val imageViews = mutableListOf<View>()
        findImageViews(templateView, imageViews)

        photos.forEachIndexed { index, uri ->
            if (index < imageViews.size) {
                val imageView = imageViews[index]
                if (imageView is android.widget.ImageView) {
                    Glide.with(this)
                        .load(uri)
                        .centerCrop()
                        .into(imageView)
                }
            }
        }
    }

    /**
     * 递归查找所有 ImageView
     */
    private fun findImageViews(view: View, imageViews: MutableList<View>) {
        if (view is android.widget.ImageView && view.id != android.R.id.empty) {
            imageViews.add(view)
        } else if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findImageViews(view.getChildAt(i), imageViews)
            }
        }
    }

    /**
     * 保存拼图
     */
    private fun saveCollage() {
        try {
            // 将拼图容器转换为 Bitmap
            val bitmap = createBitmapFromView(binding.collageContainer)

            // 保存到相册
            val savedUri = saveBitmapToGallery(bitmap)

            if (savedUri != null) {
                Toast.makeText(this, "拼图已保存到相册", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 将 View 转换为 Bitmap
     */
    private fun createBitmapFromView(view: View): Bitmap {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)

        val bitmap = Bitmap.createBitmap(
            view.measuredWidth,
            view.measuredHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        view.draw(canvas)
        return bitmap
    }

    /**
     * 保存 Bitmap 到相册
     */
    private fun saveBitmapToGallery(bitmap: Bitmap): Uri? {
        val imagesDir = File(getExternalFilesDir(null), "Pictures")
        if (!imagesDir.exists()) {
            imagesDir.mkdirs()
        }

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFile = File(imagesDir, "collage_$timeStamp.jpg")

        try {
            FileOutputStream(imageFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            // 通知系统相册更新
            val contentValues = android.content.ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, imageFile.name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/JessHome")
            }

            val uri =
                contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                contentResolver.openOutputStream(it)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                }
            }

            return uri
        } catch (e: IOException) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * 模板适配器
     */
    private inner class TemplateAdapter(
        private val templates: List<CollageTemplate>,
        private val onTemplateSelected: (CollageTemplate) -> Unit
    ) : RecyclerView.Adapter<TemplateAdapter.TemplateViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TemplateViewHolder {
            val binding = ItemCollageTemplateBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return TemplateViewHolder(binding)
        }

        override fun onBindViewHolder(holder: TemplateViewHolder, position: Int) {
            holder.bind(templates[position])
        }

        override fun getItemCount(): Int = templates.size

        inner class TemplateViewHolder(
            private val binding: ItemCollageTemplateBinding
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(template: CollageTemplate) {
                binding.tvTemplateName.text = template.templateName
                binding.root.isSelected = template == currentTemplate

                // 显示模板预览图（使用简单的占位符，实际可以使用真实的预览图）
                // 这里使用一个通用的网格图标作为预览
                binding.ivTemplatePreview.setImageResource(android.R.drawable.ic_menu_gallery)

                binding.root.setOnClickListener {
                    onTemplateSelected(template)
                }
            }
        }
    }
}

