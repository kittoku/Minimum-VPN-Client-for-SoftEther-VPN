package kittoku.mvc.preference.accessor

import android.content.SharedPreferences
import kittoku.mvc.preference.MvcPreference


internal fun getIntPrefValue(key: MvcPreference, prefs: SharedPreferences): Int {
    val defaultValue = when (key) {
        MvcPreference.SSL_PORT -> 443
        else -> throw NotImplementedError()
    }

    return prefs.getInt(key.name, defaultValue)
}

internal fun setIntPrefValue(value: Int, key: MvcPreference, prefs: SharedPreferences) {
    prefs.edit().also {
        it.putInt(key.name, value)
        it.apply()
    }
}
