package com.happy.jesshome.ui.tab

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import com.happy.jesshome.R
import com.happy.jesshome.base.BaseFragment
import com.happy.jesshome.ui.ir.IrActivity

class DeviceFragment : BaseFragment(R.layout.fragment_device) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 这里需要处理系统状态栏的安全区，否则内容可能会顶到状态栏下面。
        applySystemBarInsets(view)

        val entry = view.findViewById<View>(R.id.entry_ir)
        entry.findViewById<ImageView>(R.id.iv_entry_icon).setImageResource(R.mipmap.new_device)
        entry.findViewById<TextView>(R.id.tv_entry_text).text = "红外线"
        entry.setOnClickListener {
            startActivity(Intent(requireContext(), IrActivity::class.java))
        }
    }

    private fun applySystemBarInsets(root: View) {
        val scroll = root.findViewById<View>(R.id.scroll_container)
        val density = resources.displayMetrics.density
        val designPaddingPx = (20f * density).toInt()

        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(
                left = designPaddingPx,
                top = sys.top + designPaddingPx,
                right = designPaddingPx,
                bottom = sys.bottom + designPaddingPx
            )
            insets
        }
        ViewCompat.requestApplyInsets(scroll)
    }
}


