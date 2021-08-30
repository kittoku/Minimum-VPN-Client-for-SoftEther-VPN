package kittoku.mvc.fragment

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import kittoku.mvc.R


internal class AboutFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.about, rootKey)
    }
}
