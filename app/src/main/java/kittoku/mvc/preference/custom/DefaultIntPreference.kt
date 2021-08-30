package kittoku.mvc.preference.custom

import android.content.Context
import android.text.InputType
import android.util.AttributeSet
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.Preference.SummaryProvider
import kittoku.mvc.preference.MvcPreference
import kittoku.mvc.preference.accessor.getIntPrefValue


internal abstract class DefaultIntPreference(context: Context, attrs: AttributeSet) : EditTextPreference(context, attrs) {
    abstract val mvcPreference: MvcPreference
    abstract val preferenceTitle: String
    private val provider = SummaryProvider<Preference> {
        getIntPrefValue(mvcPreference, it.sharedPreferences).toString()
    }

    override fun onAttached() {
        super.onAttached()

        title = preferenceTitle
        summaryProvider = provider

        setOnBindEditTextListener { editText ->
            editText.inputType = InputType.TYPE_CLASS_NUMBER
        }
    }
}

internal class SslPortPreference(context: Context, attrs: AttributeSet) : DefaultIntPreference(context, attrs) {
    override val mvcPreference = MvcPreference.SSL_PORT
    override val preferenceTitle = "Port Number"
}
