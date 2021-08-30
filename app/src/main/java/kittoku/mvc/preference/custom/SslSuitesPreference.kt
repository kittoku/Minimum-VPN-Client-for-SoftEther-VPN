package kittoku.mvc.preference.custom

import android.content.Context
import android.util.AttributeSet
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.Preference.SummaryProvider
import kittoku.mvc.preference.MvcPreference
import kittoku.mvc.preference.accessor.getSetPrefValue
import javax.net.ssl.SSLContext


internal class SslSuitesPreference(context: Context, attrs: AttributeSet) : MultiSelectListPreference(context, attrs) {
    private val mvcPreference = MvcPreference.SSL_SUITES
    private val preferenceTitle = "Select Cipher Suites"
    private val provider = SummaryProvider<Preference> {
        val currentValue = getSetPrefValue(mvcPreference, it.sharedPreferences)

        when (currentValue.size) {
            0 -> "[No Suite Entered]"
            1 -> "1 Suite Selected"
            else -> "${currentValue.size} Suites Selected"
        }
    }

    override fun onAttached() {
        super.onAttached()

        title = preferenceTitle
        summaryProvider = provider
        dependency = MvcPreference.SSL_DO_SELECT_SUITES.name

        SSLContext.getDefault().supportedSSLParameters.also {
            entries = it.cipherSuites
            entryValues = it.cipherSuites
        }
    }
}
