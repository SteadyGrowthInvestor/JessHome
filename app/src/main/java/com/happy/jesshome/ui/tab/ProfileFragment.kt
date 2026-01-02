package com.happy.jesshome.ui.tab

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.gyf.immersionbar.ImmersionBar
import com.happy.jesshome.R
import com.happy.jesshome.base.BaseFragment
import com.happy.jesshome.databinding.FragmentProfileBinding
import com.happy.jesshome.databinding.ItemProfileEntryBinding
import com.happy.jesshome.databinding.ItemProfileQuickBinding
import com.happy.jesshome.skin.SkinKeys
import com.happy.jesshome.skin.SkinManager
import com.happy.jesshome.ui.theme.ThemeCenterActivity

/**
 * Profile / 我的 页面
 * 使用 CoordinatorLayout + AppBarLayout + CollapsingToolbarLayout
 * 不再手写滚动和 alpha
 * 使用 ViewBinding 来访问视图，避免 findViewById 调用
 * 
 * 注意：如果看到 ViewBinding 相关的错误，请先同步项目（Sync Project with Gradle Files）
 * ViewBinding 类会在项目同步后自动生成
 */
class ProfileFragment : BaseFragment(R.layout.fragment_profile) {

    // ViewBinding 实例，在 onViewCreated 中初始化，在 onDestroyView 中清理
    // 使用 _binding 和 binding 模式，确保在视图销毁时安全清理
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val skinListener: (Int) -> Unit = {
        _binding?.let { applySkin() }
    }

    override fun onStart() {
        super.onStart()
        SkinManager.addListener(skinListener)
    }

    override fun onStop() {
        super.onStop()
        SkinManager.removeListener(skinListener)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 初始化 ViewBinding
        _binding = FragmentProfileBinding.bind(view)

        // 让内容绘制到状态栏下
        WindowCompat.setDecorFitsSystemWindows(requireActivity().window, false)

        applySkin()
        initStatusBar()
        initToolbar()
        // 注释掉 alpha 渐变效果，避免 header 背景色与 contentScrim 混合产生中间色
        // 使用 CollapsingToolbarLayout 的 layout_collapseMode 来控制 header 的显示/隐藏
        // setupAppBarHeaderFadeEffect(binding.appBar, binding.header)
        
        initClicks()
        initDemoData()
    }

    /**
     * Toolbar（吸顶标题）
     */
    private fun initToolbar() {
        binding.btnSetting.setOnClickListener {
            startActivity(Intent(requireContext(), ThemeCenterActivity::class.java))
        }

        // 动态设置 Toolbar 的 paddingTop，确保折叠时内容不被状态栏遮挡
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(
                view.paddingLeft,
                statusBarHeight,
                view.paddingRight,
                view.paddingBottom
            )
            insets
        }
    }


    /**
     * 所有点击事件
     */
    private fun initClicks() {
        // 顶部用户区域点击
        binding.userRow.setOnClickListener {
            Toast.makeText(requireContext(), "User Center", Toast.LENGTH_SHORT).show()
        }

        // Savvy 卡片
        binding.suCard.setOnClickListener {
            Toast.makeText(requireContext(), "Savvy User Center", Toast.LENGTH_SHORT).show()
        }

        // Quick actions
        // ViewBinding 会将 qa_1 转换为 qa1，qa_2 转换为 qa2，qa_3 转换为 qa3
        // 使用 findViewById 确保在 ViewBinding 类未生成时也能正常工作
        bindQuick(binding.root.findViewById(R.id.qa_1), R.mipmap.new_profile_btn_presale, "客服服务")
        bindQuick(binding.root.findViewById(R.id.qa_2), R.mipmap.new_profile_btn_postsale, "Govee Store")
        bindQuick(binding.root.findViewById(R.id.qa_3), R.mipmap.new_profile_btn_replacement, "设备分享")

        // Entry list
        val entries = listOf(
            Entry(R.mipmap.new_profile_btn_presale, "我的订单"),
            Entry(R.mipmap.new_profile_icon_user_notifications, "我的消息"),
            Entry(R.mipmap.new_profile_btn_postsale, "固件更新"),
            Entry(R.mipmap.new_profile_btn_replacement, "设备分享"),
            Entry(R.mipmap.new_profile_btn_repott_issue_01, "小组件"),
            Entry(R.mipmap.new_profile_btn_postsale, "语音助手"),
        )

        val rows = listOf(
            binding.root.findViewById<View>(R.id.entry_1),
            binding.root.findViewById<View>(R.id.entry_2),
            binding.root.findViewById<View>(R.id.entry_3),
            binding.root.findViewById<View>(R.id.entry_4),
            binding.root.findViewById<View>(R.id.entry_5),
            binding.root.findViewById<View>(R.id.entry_6),
        )

        rows.zip(entries).forEach { (row, item) ->
            // 使用 ViewBinding 访问 include 布局中的视图
            // 注意：include 布局需要使用 android:id 才能通过 ViewBinding 访问
            row?.let {
                val rowBinding = ItemProfileEntryBinding.bind(it)
                rowBinding.ivEntryIcon.setImageResource(item.iconRes)
                rowBinding.tvEntryText.text = item.title
                it.setOnClickListener {
                    Toast.makeText(requireContext(), item.title, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Demo 数据
     */
    private fun initDemoData() {
        binding.tvUsername.text = "GH-TRCk0V"
    }

    private data class Entry(val iconRes: Int, val title: String)

    /**
     * 绑定 Quick Action 按钮
     * @param root Quick Action 的根 View（通过 ViewBinding 获取）
     * @param iconRes 背景图片资源 ID
     * @param text 显示的文本
     */
    private fun bindQuick(root: View, iconRes: Int, text: String) {
        root.setBackgroundResource(iconRes)
        // 使用 ViewBinding 访问 include 布局中的视图
        val quickBinding = ItemProfileQuickBinding.bind(root)
        quickBinding.tvQuick.text = text
        root.setOnClickListener {
            Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 皮肤适配
     * 使用 ViewBinding 访问视图，无需传递 root View 参数
     */
    private fun applySkin() {
//        // 顶部背景图
//        binding.header.setBackgroundResource(
////            SkinManager.resolveDrawable(
////                requireContext(),
////                R.mipmap.new_bg_my_profile_blue,
////                SkinKeys.PROFILE_BG
////            )
//        )

        // 设置按钮
        SkinManager.show(binding.btnSetting, R.mipmap.new_profile_icon_user_setting, SkinKeys.PROFILE_SETTING)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 清理 ViewBinding 引用，避免内存泄漏
        _binding = null
        immersionBar = null
    }
}
