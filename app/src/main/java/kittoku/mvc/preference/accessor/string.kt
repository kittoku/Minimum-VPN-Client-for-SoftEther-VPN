package kittoku.mvc.preference.accessor

import android.content.SharedPreferences
import kittoku.mvc.preference.MvcPreference


internal fun getStringPrefValue(key: MvcPreference, prefs: SharedPreferences): String {
    val defaultValue = when (key) {
        MvcPreference.HOME_HOSTNAME,
        MvcPreference.HOME_USERNAME,
        MvcPreference.HOME_PASSWORD,
        MvcPreference.MAC_ADDRESS -> ""
        MvcPreference.SSL_VERSION -> "DEFAULT"
        else -> throw NotImplementedError()
    }

    return prefs.getString(key.name, defaultValue)!!
}

internal fun setStringPrefValue(value: String, key: MvcPreference, prefs: SharedPreferences) {
    prefs.edit().also {
        it.putString(key.name, value)
        it.apply()
    }
}
