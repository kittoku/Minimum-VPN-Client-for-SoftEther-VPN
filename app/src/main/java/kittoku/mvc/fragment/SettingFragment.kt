package kittoku.mvc.fragment

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import kittoku.mvc.R
import kittoku.mvc.preference.MvcPreference
import kittoku.mvc.preference.accessor.setStringPrefValue
import kittoku.mvc.preference.custom.LogDirectoryPreference


internal class SettingFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.setting, rootKey)

        setLogDirListener()
    }

    private fun setLogDirListener() {
        val launcher = registerForActivityResult(StartActivityForResult()) { result ->
            val uri = if (result.resultCode == Activity.RESULT_OK) {
                result?.data?.data?.also {
                    context?.contentResolver?.takePersistableUriPermission(
                        it, Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                }
            } else null

            setStringPrefValue(
                uri?.toString() ?: "",
                MvcPreference.LOG_DIRECTORY,
                preferenceManager.sharedPreferences
            )
        }

        findPreference<LogDirectoryPreference>(MvcPreference.LOG_DIRECTORY.name)!!.also {
            it.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                intent.flags = Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                launcher.launch(intent)

                true
            }
        }
    }
}
