package com.happy.jesshome.base

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams

/**
 * BaseActivity that helps avoid drawing content under the system status bar.
 * Call [applyStatusBarInsets] for the view that represents your top bar/header.
 */
open class BaseActivity : AppCompatActivity {

    constructor() : super()
    constructor(contentLayoutId: Int) : super(contentLayoutId)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // We control insets manually per-screen.
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    protected fun applyStatusBarInsets(@IdRes topBarId: Int) {
        val v = findViewById<View>(topBarId) ?: return
        applyStatusBarInsets(v)
    }

    protected fun applyStatusBarInsets(topBar: View) {
        // record original padding & height to avoid double-adding when insets re-applied
        val initialTop = topBar.paddingTop
        val initialLeft = topBar.paddingLeft
        val initialRight = topBar.paddingRight
        val initialBottom = topBar.paddingBottom
        val initialHeight = topBar.layoutParams?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT

        ViewCompat.setOnApplyWindowInsetsListener(topBar) { view, insets ->
            val topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top

            view.setPadding(initialLeft, initialTop + topInset, initialRight, initialBottom)

            // If the top bar has a fixed height, expand it to include the inset so children aren't clipped.
            view.updateLayoutParams {
                if (initialHeight > 0) {
                    height = initialHeight + topInset
                }
            }

            insets
        }
        topBar.requestApplyInsets()
    }
}
