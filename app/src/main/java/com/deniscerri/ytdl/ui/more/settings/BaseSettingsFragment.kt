package com.deniscerri.ytdl.ui.more.settings

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceGroupAdapter
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.util.ApkInstallUtil


/**
 * Scrolls once to the preference the Ajustes tab asked for ([SettingsActivity.EXTRA_SCROLL_TO]), if it is on this screen.
 * It lands at the top, with its section title, instead of at the bottom edge where [scrollToPreference] leaves it.
 */
fun PreferenceFragmentCompat.scrollToRequestedPreference() {
    val intent = activity?.intent ?: return
    val key = intent.getStringExtra(SettingsActivity.EXTRA_SCROLL_TO) ?: return
    val adapter = listView.adapter as? PreferenceGroupAdapter ?: return
    var position = adapter.getPreferenceAdapterPosition(key)
    if (position == RecyclerView.NO_POSITION) return
    intent.removeExtra(SettingsActivity.EXTRA_SCROLL_TO)
    if (position > 0 && adapter.getItem(position - 1) is PreferenceCategory) position--
    (listView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(position, 0)
}

abstract class BaseSettingsFragment : PreferenceFragmentCompat(), SettingHost {
    abstract val title: Int

    private lateinit var installLauncher: ActivityResultLauncher<Intent>

    override fun findPref(key: String) = findPreference<Preference>(key)
    @SuppressLint("NotifyDataSetChanged")
    override fun refreshUI() {
        listView.adapter?.notifyDataSetChanged()
    }
    override fun getHostContext() = requireActivity()
    override fun getAppInstallLauncher() = installLauncher
    override val activityResultDelegate = PreferenceActivityResultDelegate(this)
    override fun requestGetParentFragmentManager() = parentFragmentManager
    override fun requestRecreateActivity() = requireActivity().recreate()
    override fun requestNavigate(id: Int) = findNavController().navigate(id)
    override val hostViewModelStoreOwner by lazy {
        this
    }
    override val hostLifecycleOwner by lazy {
        this
    }
    override val hostView by lazy {
        view
    }
    override fun onStart() {
        super.onStart()
        (activity as? SettingsActivity)?.changeTopAppbarTitle(getString(title))
    }

    fun getPreferences(p: Preference, list: MutableList<Preference>) : List<Preference> {
        if (p is PreferenceCategory || p is PreferenceScreen) {
            val pGroup: PreferenceGroup = p as PreferenceGroup
            val pCount: Int = pGroup.preferenceCount
            for (i in 0 until pCount) {
                getPreferences(pGroup.getPreference(i), list) // recursive call
            }
        } else {
            list.add(p)
        }
        return list
    }

    fun resetPreferences(editor: SharedPreferences.Editor, key: Int) {
        getPreferences(preferenceScreen, mutableListOf()).forEach {
            editor.remove(it.key)
        }
        editor.apply()
        PreferenceManager.setDefaultValues(requireActivity().applicationContext, key, true)
    }

    //Thanks libretube
    override fun onDisplayPreferenceDialog(preference: Preference) {
        val shownCustomDialog = DefaultPreferenceActions.onPreferenceDisplayDialog(requireActivity(), preference) {}
        if (!shownCustomDialog) {
            super.onDisplayPreferenceDialog(preference)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installLauncher = ApkInstallUtil.registerInstallLauncher(this)
    }

    override fun onCreateAdapter(preferenceScreen: PreferenceScreen): RecyclerView.Adapter<*> {
        preferenceScreen.prepareForCards(requireContext())
        return GroupedPreferenceAdapter(preferenceScreen)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // no line between sections: the cards already separate them
        setDivider(null)
        scrollToRequestedPreference()

        ViewCompat.setOnApplyWindowInsetsListener(listView) { v, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )


            v.setPadding(
                v.paddingLeft,
                v.paddingTop,
                v.paddingRight,
                v.paddingBottom + systemBars.bottom
            )

            insets
        }

        val keyToHighlight = arguments?.getString("highlight_key")
        if (keyToHighlight != null) {
            val adapter = listView.adapter as? PreferenceGroupAdapter
            val position = adapter?.getPreferenceAdapterPosition(keyToHighlight) ?: -1

            if (position != -1) {
                listView.postDelayed({
                    listView.smoothScrollToPosition(position + 1)

                    listView.postDelayed({
                        val holder = listView.findViewHolderForAdapterPosition(position)
                        holder?.itemView?.let { itemView ->
                            val originalColor = itemView.background
                            itemView.setBackgroundColor(requireContext().getColor(android.R.color.system_control_highlight_light))
                            itemView.postDelayed({
                                itemView.background = originalColor
                            }, 1000)
                        }
                    }, 300)
                }, 200)
            }
        }
    }
}