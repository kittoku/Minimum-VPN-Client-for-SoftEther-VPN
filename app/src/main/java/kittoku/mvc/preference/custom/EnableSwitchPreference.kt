package kittoku.mvc.preference.custom

import android.content.Context
import android.util.AttributeSet
import androidx.preference.SwitchPreferenceCompat
import kittoku.mvc.preference.MvcPreference


internal abstract class EnableSwitchPreference(context: Context, attrs: AttributeSet) : SwitchPreferenceCompat(context, attrs) {
    abstract val mvcPreference: MvcPreference
    abstract val preferenceTitle: String

    override fun onAttached() {
        super.onAttached()

        title = preferenceTitle
        isSingleLineTitle = false
    }
}

internal class SslDoSelectSuitesPreference(context: Context, attrs: AttributeSet) : EnableSwitchPreference(context, attrs) {
    override val mvcPreference = MvcPreference.SSL_DO_SELECT_SUITES
    override val preferenceTitle = "Enable Only Selected Cipher Suites"
}

internal class UDPEnableAcceleration(context: Context, attrs: AttributeSet) : EnableSwitchPreference(context, attrs) {
    override val mvcPreference = MvcPreference.UDP_ENABLE_ACCELERATION
    override val preferenceTitle = "Enable UDP Acceleration"
}

internal class LogDoSaveLogPreference(context: Context, attrs: AttributeSet) : EnableSwitchPreference(context, attrs) {
    override val mvcPreference = MvcPreference.LOG_DO_SAVE_LOG
    override val preferenceTitle = "Save Log"
}
