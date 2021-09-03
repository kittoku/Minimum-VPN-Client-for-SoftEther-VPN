package kittoku.mvc.preference.custom

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.AttributeSet
import androidx.preference.Preference
import kittoku.mvc.preference.MvcPreference
import kittoku.mvc.preference.accessor.getStringPrefValue


internal abstract class DirectoryPreference(context: Context, attrs: AttributeSet) : Preference(context, attrs) {
    abstract val mvcPreference: MvcPreference
    abstract val preferenceTitle: String
    protected open val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == mvcPreference.name) {
            updateSummary()
        }
    }

    private val summaryValue: String
        get() {
            val currentValue = getStringPrefValue(mvcPreference, sharedPreferences)

            return if (currentValue.isEmpty()) {
                "[No Directory Selected]"
            } else {
                Uri.parse(currentValue).path!!
            }
        }

    private fun updateSummary() {
        summary = summaryValue
    }

    override fun onAttached() {
        super.onAttached()

        title = preferenceTitle
        updateSummary()

        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun onDetached() {
        super.onDetached()

        sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
    }
}

internal class LogDirectoryPreference(context: Context, attrs: AttributeSet) : DirectoryPreference(context, attrs) {
    override val mvcPreference = MvcPreference.LOG_DIRECTORY
    override val preferenceTitle = "Select Log Directory"

    override fun onAttached() {
        super.onAttached()

        dependency = MvcPreference.LOG_DO_SAVE_LOG.name
    }
}
