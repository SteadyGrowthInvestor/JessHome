package com.happy.jesshome.skin

import android.content.Context

object SkinStore {
    private const val PREF = "jess_skin"
    private const val KEY = "skin_id"

    const val SKIN_DEFAULT = 0
    const val SKIN_ALT = 1

    fun get(context: Context): Int {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getInt(KEY, SKIN_DEFAULT)
    }

    fun set(context: Context, skinId: Int) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY, skinId)
            .apply()
    }
}
