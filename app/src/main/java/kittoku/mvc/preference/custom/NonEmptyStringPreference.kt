package kittoku.mvc.preference.custom

import android.content.Context
import android.text.InputType
import android.util.AttributeSet
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.Preference.SummaryProvider
import kittoku.mvc.preference.MvcPreference
import kittoku.mvc.preference.accessor.getStringPrefValue


internal abstract class NonEmptyStringPreference(context: Context, attrs: AttributeSet) : EditTextPreference(context, attrs) {
    abstract val mvcPreference: MvcPreference
    abstract val preferenceTitle: String
    open val emptyNotice = "[No Value Entered]"
    open val textType = InputType.TYPE_CLASS_TEXT
    open val provider = SummaryProvider<Preference> {
        val currentValue = getStringPrefValue(mvcPreference, it.sharedPreferences)

        if (currentValue.isEmpty()) {
            emptyNotice
        } else {
            currentValue
        }
    }

    override fun onAttached() {
        super.onAttached()

        title = preferenceTitle
        summaryProvider = provider

        setOnBindEditTextListener { editText ->
            editText.inputType = textType
        }
    }
}

internal class HomeHostnamePreference(context: Context, attrs: AttributeSet) : NonEmptyStringPreference(context, attrs) {
    override val mvcPreference = MvcPreference.HOME_HOSTNAME
    override val preferenceTitle = "Hostname"
}

internal class HomeUsernamePreference(context: Context, attrs: AttributeSet) : NonEmptyStringPreference(context, attrs) {
    override val mvcPreference = MvcPreference.HOME_USERNAME
    override val preferenceTitle = "Username"
}

internal class HomePasswordPreference(context: Context, attrs: AttributeSet) : NonEmptyStringPreference(context, attrs) {
    override val mvcPreference = MvcPreference.HOME_PASSWORD
    override val preferenceTitle = "Password"
    override val textType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

    override val provider = SummaryProvider<Preference> {
        val currentValue = getStringPrefValue(mvcPreference, it.sharedPreferences)

        if (currentValue.isEmpty()) {
            emptyNotice
        } else {
            "[Password Entered]"
        }
    }
}
