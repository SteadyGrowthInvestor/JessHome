package com.happy.jesshome.ui.tab

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.happy.jesshome.R
import com.happy.jesshome.base.BaseFragment

class SimpleTabFragment : BaseFragment(R.layout.fragment_simple_tab) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val title = requireArguments().getString(ARG_TITLE).orEmpty()
        view.findViewById<TextView>(R.id.tv_title).text = title
    }

    companion object {
        private const val ARG_TITLE = "arg_title"

        fun newInstance(title: String): SimpleTabFragment {
            return SimpleTabFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, title)
                }
            }
        }
    }
}
