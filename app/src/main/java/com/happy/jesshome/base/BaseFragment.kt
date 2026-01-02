package com.happy.jesshome.base

import android.os.Bundle
import android.util.DisplayMetrics
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.AppBarLayout
import com.gyf.immersionbar.ImmersionBar

/**
 * BaseFragment that helps avoid drawing content under the system status bar.
 */
open class BaseFragment(layoutId: Int) : Fragment(layoutId) {

    protected var immersionBar: ImmersionBar? = null

    protected fun applyStatusBarInsets(topBar: View) {
        val initialTop = topBar.paddingTop
        val initialLeft = topBar.paddingLeft
        val initialRight = topBar.paddingRight
        val initialBottom = topBar.paddingBottom
        val initialHeight = topBar.layoutParams?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT

        ViewCompat.setOnApplyWindowInsetsListener(topBar) { view, insets ->
            val topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top

            view.setPadding(initialLeft, initialTop + topInset, initialRight, initialBottom)

            view.updateLayoutParams {
                if (initialHeight > 0) {
                    height = initialHeight + topInset
                }
            }

            insets
        }
        topBar.requestApplyInsets()
    }

    /**
     * 兼容状态栏内边距，参考 GoveeHome 的实现方式。
     * 给 top_flag View 设置 topMargin，让背景图片可以延伸到状态栏，但内容不会被状态栏遮挡。
     *
     * @param topFlagView 占位符 View（通常是高度为 0dp 的 TextView）
     * @param defMarginTop 默认的顶部边距比例（如 66，表示屏幕宽度的 66/750）
     */
    protected fun adaptationInsetTop(topFlagView: View, defMarginTop: Int) {
        try {
            if (topFlagView == null) return
            val displayMetrics: DisplayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val defMarginTopPx = (screenWidth * defMarginTop / 750).toInt()

            ViewCompat.setOnApplyWindowInsetsListener(topFlagView) { view, insets ->
                val insetTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
                if (insetTop > 0 && insetTop > defMarginTopPx) {
                    val marginTop = insetTop - defMarginTopPx
                    val lp = view.layoutParams
                    if (lp is ViewGroup.MarginLayoutParams) {
                        lp.topMargin = marginTop
                        view.layoutParams = lp
                    }
                }
                insets
            }
            ViewCompat.requestApplyInsets(topFlagView)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 设置 AppBarLayout 滚动时的 Header 内容淡出效果。
     * 
     * 此方法用于解决使用 CollapsingToolbarLayout 时，Header 内容（如用户信息、卡片等）
     * 与 Toolbar 标题在折叠过程中同时显示的问题。通过动态调整 Header 内容的透明度，
     * 让其在折叠到一定程度时快速消失，避免视觉冲突。
     * 
     * 工作原理：
     * 1. 监听 AppBarLayout 的滚动偏移量
     * 2. 根据滚动比例计算 Header 内容的透明度
     * 3. 使用非线性动画（平方函数）让消失效果更自然
     * 
     * 折叠阶段说明：
     * - 0% - 20%：Header 内容完全显示（alpha = 1.0）
     * - 20% - 50%：Header 内容快速淡出（使用平方函数加速）
     * - 50% - 100%：Header 内容完全隐藏（alpha = 0.0）
     * 
     * 使用示例：
     * ```kotlin
     * override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
     *     super.onViewCreated(view, savedInstanceState)
     *     val appBarLayout = view.findViewById<AppBarLayout>(R.id.app_bar)
     *     val headerView = view.findViewById<View>(R.id.header)
     *     setupAppBarHeaderFadeEffect(appBarLayout, headerView)
     * }
     * ```
     * 
     * @param appBarLayout AppBarLayout 实例，用于监听滚动事件
     * @param headerView Header 内容的根 View，将根据滚动状态调整其透明度
     * 
     * @see AppBarLayout.OnOffsetChangedListener
     */
    protected fun setupAppBarHeaderFadeEffect(appBarLayout: AppBarLayout?, headerView: View?) {
        // 参数校验：如果 AppBarLayout 或 HeaderView 为空，则不设置监听器
        if (appBarLayout == null || headerView == null) {
            return
        }

        // 添加滚动偏移监听器
        appBarLayout.addOnOffsetChangedListener(AppBarLayout.OnOffsetChangedListener { appBarLayout, verticalOffset ->
            // verticalOffset 表示当前滚动偏移量，值为负数（向下滚动为负）
            // totalScrollRange 表示 AppBarLayout 可以滚动的总范围
            
            val totalScrollRange = appBarLayout.totalScrollRange
            
            // 计算折叠比例：
            // scrollRatio = 0.0 表示完全展开（初始状态）
            // scrollRatio = 1.0 表示完全折叠（滚动到底）
            // 使用 coerceAtLeast(1) 防止除零错误
            val scrollRatio = (-verticalOffset).toFloat() / totalScrollRange.coerceAtLeast(1)

            // 根据折叠比例计算透明度，使用非线性动画让效果更自然
            val alpha = when {
                // 阶段1：前 20% 的滚动过程中，Header 内容完全显示
                // 这样可以保证在开始滚动时，用户能看到完整的 Header 内容
                scrollRatio < 0.2f -> 1f
                
                // 阶段2：20% - 50% 的滚动过程中，Header 内容快速淡出
                // 使用平方函数 (progress^2) 来加速消失，让过渡更平滑
                // 当 scrollRatio = 0.2 时，progress = 0，alpha = 1.0
                // 当 scrollRatio = 0.5 时，progress = 1，alpha = 0.0
                scrollRatio < 0.5f -> {
                    // 将 20%-50% 的范围映射到 0-1 的进度值
                    val progress = (scrollRatio - 0.2f) / 0.3f
                    // 使用平方函数：1 - progress^2，让消失速度先慢后快
                    // 这样可以避免突然消失，同时确保在 50% 时完全隐藏
                    1f - progress * progress
                }
                
                // 阶段3：50% 之后的滚动过程中，Header 内容完全隐藏
                // 此时 Toolbar 标题已经显示，避免两者同时出现
                else -> 0f
            }.coerceIn(0f, 1f) // 确保透明度值在 0-1 范围内

            // 应用计算出的透明度到 Header View
            // 这会触发平滑的淡出动画效果
            headerView.alpha = alpha
        })
    }

    /**
     * 状态栏：只初始化一次
     * 透明状态栏 + 深色图标
     * 折叠后的颜色由 CollapsingToolbarLayout 的 scrim 控制
     */
    protected fun initStatusBar() {
        immersionBar = ImmersionBar.with(this)
            .transparentStatusBar()
            .statusBarDarkFont(true)
            .fitsSystemWindows(false)
            .keyboardEnable(false)

        immersionBar?.init()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initStatusBar()
    }

    override fun onResume() {
        super.onResume()
//        initStatusBar()
    }

}
