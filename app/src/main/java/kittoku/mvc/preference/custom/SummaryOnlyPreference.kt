package kittoku.mvc.preference.custom

import android.content.Context
import android.content.SharedPreferences
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import kittoku.mvc.preference.MvcPreference
import kittoku.mvc.preference.accessor.getStringPrefValue


internal abstract class SummaryOnlyPreference(context: Context, attrs: AttributeSet) : Preference(context, attrs) {
    abstract val mvcPreference: MvcPreference
    abstract val preferenceTitle: String
    protected open val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == mvcPreference.name) {
            updateSummary()
        }
    }

    protected open val summaryValue: String
        get() = getStringPrefValue(mvcPreference, sharedPreferences)

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

    override fun onBindViewHolder(holder: PreferenceViewHolder?) {
        super.onBindViewHolder(holder)

        holder?.findViewById(android.R.id.summary)?.also {
            it as TextView
            it.maxLines = Int.MAX_VALUE
        }
    }
}

internal class EthernetMacAddressPreference(context: Context, attrs: AttributeSet) : SummaryOnlyPreference(context, attrs) {
    override val mvcPreference = MvcPreference.ETHERNET_MAC_ADDRESS
    override val preferenceTitle = "MAC Address"
    override val summaryValue: String
        get() {
            val address = getStringPrefValue(mvcPreference, sharedPreferences)

            return if (address.isEmpty()) {
                "[No Address Assigned]"
            } else {
                address.chunked(2).joinToString(":")
            }
        }
}

internal class AboutProjectPreference(context: Context, attrs: AttributeSet) : SummaryOnlyPreference(context, attrs) {
    override val mvcPreference = MvcPreference.ABOUT_PROJECT
    override val preferenceTitle = "About This App"
    override val summaryValue = listOf(
        "This is an unofficial open-source SoftEther-VPN-protocol-based VPN client." ,
        "This app includes part of SoftEtherVPN source code which is under Apache License 2.0.",
        """"SoftEther" is a registered trademark of SoftEther Corporation.""",
        "If you need more information, please move to each project page from the following links.",
    ).joinToString("\n\n")

    override fun onAttached() {
        super.onAttached()

        isIconSpaceReserved = false
    }
}

internal class HomeStatusPreference(context: Context, attrs: AttributeSet) : SummaryOnlyPreference(context, attrs) {
    override val mvcPreference = MvcPreference.HOME_STATUS
    override val preferenceTitle = "Current Status"
    override val summaryValue: String
        get() {
            val currentValue = getStringPrefValue(mvcPreference, sharedPreferences)

            return if (currentValue.isEmpty()) {
                "[No Connection Established]"
            } else {
                currentValue
            }
        }
}
