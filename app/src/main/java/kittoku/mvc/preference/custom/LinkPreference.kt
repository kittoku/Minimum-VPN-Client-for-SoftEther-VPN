package kittoku.mvc.preference.custom

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import kittoku.mvc.preference.MvcPreference


internal abstract class LinkPreference(context: Context, attrs: AttributeSet) : Preference(context, attrs) {
    abstract val mvcPreference: MvcPreference
    abstract val preferenceTitle: String
    abstract val preferenceSummary: String
    abstract val url: String

    override fun onAttached() {
        super.onAttached()

        title = preferenceTitle
        summary = preferenceSummary
        intent = Intent(Intent.ACTION_VIEW).also { it.data = Uri.parse(url) }
        isIconSpaceReserved = false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder?) {
        super.onBindViewHolder(holder)

        holder?.findViewById(android.R.id.summary)?.also {
            it as TextView
            it.maxLines = Int.MAX_VALUE
        }
    }
}

internal class AboutLinkMvc(context: Context, attrs: AttributeSet) : LinkPreference(context, attrs) {
    override val mvcPreference = MvcPreference.ABOUT_LINK_MVC
    override val preferenceTitle = "Move to this app's project page"
    override val preferenceSummary = "github.com/kittoku/Minimum-VPN-Client-for-SoftEther-VPN"
    override val url = "https://github.com/kittoku/Minimum-VPN-Client-for-SoftEther-VPN"
}

internal class AboutLinkSoftEther(context: Context, attrs: AttributeSet) : LinkPreference(context, attrs) {
    override val mvcPreference = MvcPreference.ABOUT_LINK_SOFTETHER
    override val preferenceTitle = "Move to SoftEther VPN project page"
    override val preferenceSummary = "github.com/SoftEtherVPN/SoftEtherVPN"
    override val url = "https://github.com/SoftEtherVPN/SoftEtherVPN"
}
