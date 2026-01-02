package com.happy.jesshome.ui.main

import android.os.Bundle
import android.os.Build
import android.graphics.Color
import androidx.core.view.WindowInsetsControllerCompat
import android.content.res.Configuration
import android.view.View
import android.view.WindowManager
import android.view.WindowInsetsController
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.gyf.immersionbar.ImmersionBar
import com.happy.jesshome.R
import com.happy.jesshome.base.BaseActivity
import com.happy.jesshome.ui.tab.DeviceFragment
import com.happy.jesshome.ui.tab.SimpleTabFragment
import com.happy.jesshome.ui.tab.ProfileFragment
import com.happy.jesshome.skin.SkinManager
import com.happy.jesshome.skin.SkinStore
import com.happy.jesshome.skin.SkinKeys
import com.happy.jesshome.ui.tab.SquareFragment

private const val BLUE = 0xFF2F80FF.toInt()
private const val GREY = 0xFFA8A8A8.toInt()

class MainActivity : BaseActivity(R.layout.activity_main) {

    private data class TabView(
        val root: View,
        val icon: ImageView,
        val label: TextView,
        val tag: String,
        val titleRes: Int,
        val iconNormal: Int,
        val iconSelected: Int,
        val skinKey: String,
    )

    private lateinit var tabs: List<TabView>
    private var currentTag: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // BaseActivity 已经设置了 setDecorFitsSystemWindows(window, false)，这里不需要再设置

        SkinManager.addListener(skinListener)

        tabs = listOf(
            bindTab(
                id = R.id.tab_device,
                tag = "tab_device",
                titleRes = R.string.tab_device,
                iconNormal = R.mipmap.new_device,
                iconSelected = R.mipmap.new_device_press,
                skinKey = SkinKeys.TAB_DEVICE
            ),
            bindTab(
                id = R.id.tab_square,
                tag = "tab_square",
                titleRes = R.string.tab_square,
                iconNormal = R.mipmap.new_light_universe,
                iconSelected = R.mipmap.new_light_universe_press,
                skinKey = SkinKeys.TAB_SQUARE
            ),
            bindTab(
                id = R.id.tab_community,
                tag = "tab_community",
                titleRes = R.string.tab_community,
                iconNormal = R.mipmap.new_community,
                iconSelected = R.mipmap.new_community_press,
                skinKey = SkinKeys.TAB_COMMUNITY
            ),
            bindTab(
                id = R.id.tab_mall,
                tag = "tab_mall",
                titleRes = R.string.tab_mall,
                iconNormal = R.mipmap.new_savvy_user,
                iconSelected = R.mipmap.new_savvy_user_press,
                skinKey = SkinKeys.TAB_MALL
            ),
            bindTab(
                id = R.id.tab_profile,
                tag = "tab_profile",
                titleRes = R.string.tab_profile,
                iconNormal = R.mipmap.new_profile,
                iconSelected = R.mipmap.new_profile_press,
                skinKey = SkinKeys.TAB_PROFILE
            ),
        )

        tabs.forEach { tab ->
            tab.label.text = getString(tab.titleRes)
            tab.root.setOnClickListener { selectTab(tab.tag) }
        }

        if (savedInstanceState == null) {
            selectTab("tab_device")
        } else {
            // restore last selected if possible
            val restored = savedInstanceState.getString("currentTag")
            selectTab(restored ?: "tab_device")
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        SkinManager.removeListener(skinListener)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("currentTag", currentTag)
    }

    private fun bindTab(
        id: Int,
        tag: String,
        titleRes: Int,
        iconNormal: Int,
        iconSelected: Int,
        skinKey: String,
    ): TabView {
        val root = findViewById<View>(id)
        val icon = root.findViewById<ImageView>(R.id.iv_icon)
        val label = root.findViewById<TextView>(R.id.tv_label)
        return TabView(root, icon, label, tag, titleRes, iconNormal, iconSelected, skinKey)
    }

    private fun selectTab(tag: String) {
        if (currentTag == tag) return
        currentTag = tag

        // update UI
        tabs.forEach { tab ->
            val selected = tab.tag == tag
            if (selected) {
                tab.icon.setImageResource(tab.iconSelected)
            } else {
                SkinManager.show(tab.icon, tab.iconNormal, tab.skinKey)
            }
            tab.label.setTextColor(if (selected) BLUE else GREY)
        }

        // 使用 ImmersionBar 更新状态栏
//        updateStatusBarWithImmersionBar(tag)

        showTabFragment(tag)
    }

    private fun showTabFragment(tag: String) {
        val fm = supportFragmentManager
        val fragment = fm.findFragmentByTag(tag) ?: createFragmentFor(tag)

        fm.commit(allowStateLoss = true) {
            // hide all
            fm.fragments.forEach { f -> hide(f) }
            if (fragment.isAdded) {
                show(fragment)
            } else {
                add(R.id.fragment_container, fragment, tag)
            }
        }
    }

    private fun createFragmentFor(tag: String): Fragment {
        if (tag == "tab_profile") {
            return ProfileFragment()
        }
        if (tag == "tab_device") {
            return DeviceFragment()
        }
        if (tag == "tab_square") {
            return SquareFragment()
        }

        val title = when (tag) {
            "tab_device" -> getString(R.string.tab_device)
            "tab_square" -> getString(R.string.tab_square)
            "tab_community" -> getString(R.string.tab_community)
            "tab_mall" -> getString(R.string.tab_mall)
            "tab_profile" -> getString(R.string.tab_profile)
            else -> getString(R.string.tab_device)
        }
        return SimpleTabFragment.newInstance(title)
    }


    override fun onResume() {
        super.onResume()
        // 使用 ImmersionBar 更新状态栏
        // 注意：ProfileFragment 会自己管理状态栏，所以这里跳过它
//        currentTag?.let { tag ->
//            if (tag != "tab_profile") {
//                updateStatusBarWithImmersionBar(tag)
//            }
//            // ProfileFragment 会在自己的 onResume 中恢复状态栏颜色
//        }
        // force refresh (useful when system night mode toggles without recreation)
        SkinManager.notify(SkinStore.get(this))
    }


    private val skinListener: (Int) -> Unit = {
        applySkinToTabs()
    }

    private fun applySkinToTabs() {
        tabs.forEach { tab ->
            // If selected, keep selected icon; otherwise allow skin to override normal icon.
            val selected = tab.tag == currentTag
            if (selected) {
                tab.icon.setImageResource(tab.iconSelected)
            } else {
                SkinManager.show(tab.icon, tab.iconNormal, tab.skinKey)
            }
        }
    }

    private fun applyWindowInsets() {
        val bottomBar = findViewById<View>(R.id.bottom_bar)
        val container = findViewById<View>(R.id.fragment_container)

        ViewCompat.setOnApplyWindowInsetsListener(bottomBar) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // Keep a small visual gap above the system navigation bar
            val extraGapPx = (14 * resources.displayMetrics.density).toInt()
            v.updateLayoutParams<androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams> {
                bottomMargin = sys.bottom + extraGapPx
            }

            // Make sure content doesn't get hidden behind the floating bar + system nav
            val barHeight = v.measuredHeight.takeIf { it > 0 } ?: v.layoutParams.height
            container.updatePadding(bottom = sys.bottom + barHeight)

            insets
        }

        bottomBar.requestApplyInsets()
    }


    private fun applyStatusBarForMode() {
        // 如果已经有选中的tab，根据tab来设置状态栏
//        currentTag?.let {
//            updateStatusBarForTab(it)
//            return
//        }
        
        // 否则使用默认的日间/夜间模式设置
        val night =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        @Suppress("DEPRECATION")
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)

        val controllerCompat = WindowInsetsControllerCompat(window, window.decorView)

        if (night) {
            // Dark mode: black bar + light icons (white text)
            window.statusBarColor = Color.BLACK
            window.navigationBarColor = Color.BLACK

            // AndroidX way
            controllerCompat.isAppearanceLightStatusBars = false
            controllerCompat.isAppearanceLightNavigationBars = false

            // API 30+ hard override
            if (Build.VERSION.SDK_INT >= 30) {
                window.insetsController?.setSystemBarsAppearance(
                    0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                )
            }

            // Legacy fallback
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        } else {
            // Light mode: white bar + dark icons
            window.statusBarColor = Color.WHITE
            window.navigationBarColor = Color.WHITE

            controllerCompat.isAppearanceLightStatusBars = true
            controllerCompat.isAppearanceLightNavigationBars = true

            if (Build.VERSION.SDK_INT >= 30) {
                window.insetsController?.setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                )
            }

            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
    }

    /**
     * 使用 ImmersionBar 更新状态栏。
     * "我的"页面：状态栏透明，背景图片延伸到状态栏，深色图标。
     * 其他页面：根据日间/夜间模式设置。
     */
    private fun updateStatusBarWithImmersionBar(tag: String) {
        val night =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        if (tag == "tab_profile") {
            // "我的"页面：透明状态栏，背景延伸到状态栏，深色图标
            ImmersionBar.with(this)
                .transparentStatusBar()
                .statusBarDarkFont(true)
                .fitsSystemWindows(false)
                .init()
        } else {
            // 其他页面：根据日间/夜间模式
            if (night) {
                ImmersionBar.with(this)
                    .statusBarColorInt(Color.BLACK)
                    .statusBarDarkFont(false)
                    .fitsSystemWindows(true)
                    .init()
            } else {
                ImmersionBar.with(this)
                    .statusBarColorInt(Color.WHITE)
                    .statusBarDarkFont(true)
                    .fitsSystemWindows(true)
                    .init()
            }
        }
    }

    /**
     * 根据当前选中的tab更新状态栏颜色。
     * "我的"页面使用浅蓝色背景，其他页面使用白色或黑色。
     * @deprecated 已使用 ImmersionBar 替代
     */
    @Deprecated("使用 updateStatusBarWithImmersionBar 替代")
    private fun updateStatusBarForTab(tag: String) {
        val night =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        @Suppress("DEPRECATION")
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)

        val controllerCompat = WindowInsetsControllerCompat(window, window.decorView)

        // "我的"页面：状态栏透明，让背景图片覆盖状态栏
        if (tag == "tab_profile") {
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = if (night) Color.BLACK else Color.WHITE

            // 浅蓝色背景上使用深色图标（深色文字）
            controllerCompat.isAppearanceLightStatusBars = true
            controllerCompat.isAppearanceLightNavigationBars = !night

            if (Build.VERSION.SDK_INT >= 30) {
                window.insetsController?.setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or if (night) 0 else WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                )
            }

            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        } else {
            // 其他页面使用原来的逻辑
            if (night) {
                window.statusBarColor = Color.BLACK
                window.navigationBarColor = Color.BLACK
                controllerCompat.isAppearanceLightStatusBars = false
                controllerCompat.isAppearanceLightNavigationBars = false

                if (Build.VERSION.SDK_INT >= 30) {
                    window.insetsController?.setSystemBarsAppearance(
                        0,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    )
                }

                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility =
                    window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            } else {
                window.statusBarColor = Color.WHITE
                window.navigationBarColor = Color.WHITE
                controllerCompat.isAppearanceLightStatusBars = true
                controllerCompat.isAppearanceLightNavigationBars = true

                if (Build.VERSION.SDK_INT >= 30) {
                    window.insetsController?.setSystemBarsAppearance(
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    )
                }

                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility =
                    window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
        }
    }

}
