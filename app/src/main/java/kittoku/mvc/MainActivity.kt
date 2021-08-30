package kittoku.mvc

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import kittoku.mvc.databinding.ActivityMainBinding
import kittoku.mvc.fragment.AboutFragment
import kittoku.mvc.fragment.HomeFragment
import kittoku.mvc.fragment.SettingFragment


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = "${getText(R.string.app_name)}: ${BuildConfig.VERSION_NAME}"


        object : FragmentStateAdapter(this) {
            private val homeFragment = HomeFragment()
            private val settingFragment = SettingFragment()
            private val aboutFragment = AboutFragment()

            override fun getItemCount() = 3

            override fun createFragment(position: Int): Fragment {
                return when (position) {
                    0 -> homeFragment
                    1 -> settingFragment
                    2 -> aboutFragment
                    else -> throw NotImplementedError()
                }
            }
        }.also {
            binding.pager.adapter = it
        }


        TabLayoutMediator(binding.tabBar, binding.pager) { tab, position ->
            tab.text = when (position) {
                0 -> "HOME"
                1 -> "SETTING"
                2 -> "ABOUT"
                else -> throw NotImplementedError()
            }
        }.attach()
    }
}
