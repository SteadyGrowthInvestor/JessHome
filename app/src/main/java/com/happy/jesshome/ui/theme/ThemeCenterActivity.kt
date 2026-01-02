package com.happy.jesshome.ui.theme

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.happy.jesshome.R
import com.happy.jesshome.base.BaseActivity
import com.happy.jesshome.skin.SkinManager
import com.happy.jesshome.skin.SkinStore

class ThemeCenterActivity : BaseActivity(R.layout.activity_theme_center) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        applyStatusBarInsets(R.id.top_bar)

        findViewById<ImageView>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.btn_reset).setOnClickListener {
            SkinStore.set(this, SkinStore.SKIN_DEFAULT)
            SkinManager.notify(SkinStore.SKIN_DEFAULT)
            Toast.makeText(this, "已恢复默认", Toast.LENGTH_SHORT).show()
        }

        val latest = listOf(
            ThemeItem("宇宙星河", R.drawable.bg_thumb_1, SkinStore.SKIN_DEFAULT),
            ThemeItem("青蓝炫", R.drawable.bg_thumb_2, SkinStore.SKIN_ALT),
            ThemeItem("梦幻玻璃", R.drawable.bg_thumb_3, SkinStore.SKIN_ALT),
            ThemeItem("色彩风暴", R.drawable.bg_thumb_4, SkinStore.SKIN_ALT),
        )

        val rv = findViewById<RecyclerView>(R.id.rv_latest)
        rv.layoutManager = GridLayoutManager(this, 3)
        rv.isNestedScrollingEnabled = false
        rv.adapter = ThemeAdapter(latest) { item ->
            SkinStore.set(this, item.skinId)
            SkinManager.notify(item.skinId)
            Toast.makeText(this, "已应用：${item.name}", Toast.LENGTH_SHORT).show()
        }

        findViewById<TextView>(R.id.btn_apply_holiday).setOnClickListener {
            SkinStore.set(this, SkinStore.SKIN_ALT)
            SkinManager.notify(SkinStore.SKIN_ALT)
            Toast.makeText(this, "已应用：Holiday", Toast.LENGTH_SHORT).show()
        }
    }
}

data class ThemeItem(
    val name: String,
    val thumbRes: Int,
    val skinId: Int,
)
