package com.happy.jesshome.skin

import android.content.Context
import android.widget.ImageView
import androidx.annotation.DrawableRes
import com.happy.jesshome.R

/**
 * Lightweight SkinManager: maps a tag -> resource depending on current skin.
 * This is a JessHome-only simplified version of Govee's SkinM.
 */
object SkinManager {

    private val listeners = LinkedHashSet<(Int) -> Unit>()

    fun addListener(listener: (Int) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (Int) -> Unit) {
        listeners.remove(listener)
    }

    fun toggle(context: Context): Int {
        val cur = SkinStore.get(context)
        val next = if (cur == SkinStore.SKIN_DEFAULT) SkinStore.SKIN_ALT else SkinStore.SKIN_DEFAULT
        SkinStore.set(context, next)
        notify(next)
        return next
    }

    fun notify(skinId: Int) {
        listeners.forEach { it.invoke(skinId) }
    }

    @DrawableRes
    fun resolveDrawable(context: Context, @DrawableRes defRes: Int, tag: String): Int {
        return when (SkinStore.get(context)) {
            SkinStore.SKIN_ALT -> when (tag) {
                // Alternate skin uses the "press" (blue) icons as default to make it obvious
                SkinKeys.TAB_DEVICE -> R.mipmap.new_device_press
                SkinKeys.TAB_SQUARE -> R.mipmap.new_light_universe_press
                SkinKeys.TAB_COMMUNITY -> R.mipmap.new_community_press
                SkinKeys.TAB_MALL -> R.mipmap.new_savvy_user_press
                SkinKeys.TAB_PROFILE -> R.mipmap.new_profile_press

                // Profile top background: reuse the same for now (you can swap later)
                SkinKeys.PROFILE_BG -> R.mipmap.new_bg_my_profile_blue
                SkinKeys.PROFILE_SETTING -> R.mipmap.new_profile_icon_user_setting_pre
                else -> defRes
            }

            else -> defRes
        }
    }

    fun show(imageView: ImageView, @DrawableRes defRes: Int, tag: String) {
        val res = resolveDrawable(imageView.context, defRes, tag)
        imageView.setImageResource(res)
    }
}
