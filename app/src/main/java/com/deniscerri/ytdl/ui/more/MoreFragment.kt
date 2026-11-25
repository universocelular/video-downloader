package com.deniscerri.ytdl.ui.more

import com.deniscerri.ytdl.ui.more.settings.BasicSettings
import org.xmlpull.v1.XmlPullParser
import android.widget.Toast
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.MainActivity
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.ui.more.settings.SettingsActivity
import com.deniscerri.ytdl.ui.more.terminal.TerminalActivity
import androidx.activity.addCallback
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.deniscerri.ytdl.whatsapp.GlassTheme
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

class MoreFragment : Fragment() {
    private lateinit var mainSharedPreferences: SharedPreferences
    private lateinit var mainSharedPreferencesEditor: SharedPreferences.Editor
    private lateinit var terminal: TextView
    private lateinit var logs: TextView
    private lateinit var commandTemplates: TextView
    private lateinit var downloadQueue: TextView
    private lateinit var downloads: TextView
    private lateinit var cookies: TextView
    private lateinit var observeSources: TextView
    private lateinit var terminateApp: TextView
    private lateinit var settings: TextView
    private lateinit var mainActivity: MainActivity
    private lateinit var downloadViewModel: DownloadViewModel

    // The new "Ajustes" (SettingsHome.kt) covers the old list of this screen
    private val settingsState = SettingsUiState()

    private fun setupSettingsHome(root: View) {
        val compose = ComposeView(requireContext()).apply {
            // the old rows underneath must not get touches through empty spots
            isClickable = true
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { GlassTheme { SettingsHome(settingsState, settingsActions) } }
        }
        (root as ViewGroup).addView(compose, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        // Back from "Avanzado" returns to the list
        val backToList = requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, false) {
            settingsState.page = SettingsPage.List
        }
        viewLifecycleOwner.lifecycleScope.launch {
            snapshotFlow { settingsState.page }.collect { backToList.isEnabled = it != SettingsPage.List }
        }
    }

    /** Tapping the Ajustes tab again goes back to the list. */
    fun showSettingsList() {
        settingsState.page = SettingsPage.List
    }

    private val settingsActions = object : SettingsActions {
        override fun onCategory(category: SettingsCategory) {
            // the settings screens live in SettingsActivity, opened straight at the category
            val (destination, scrollTo) = when (category) {
                SettingsCategory.Appearance -> R.id.appearanceSettingsFragment to null
                SettingsCategory.Home -> R.id.appearanceSettingsFragment to "search_engine"
                SettingsCategory.Downloads -> R.id.downloadSettingsFragment to null
                SettingsCategory.Folders -> R.id.folderSettingsFragment to null
                SettingsCategory.Media -> R.id.processingSettingsFragment to null
                SettingsCategory.Engine -> R.id.updateSettingsFragment to null
                SettingsCategory.Backup -> R.id.mainSettingsFragment to "backup"
                SettingsCategory.Advanced -> R.id.advancedSettingsFragment to null
                SettingsCategory.About -> R.id.mainSettingsFragment to "package_name"
            }
            val intent = Intent(requireContext(), SettingsActivity::class.java)
                .putExtra(SettingsActivity.EXTRA_DESTINATION, destination)
            scrollTo?.let { intent.putExtra(SettingsActivity.EXTRA_SCROLL_TO, it) }
            startActivity(intent)
        }

        override fun onTerminal() = startActivity(Intent(requireContext(), TerminalActivity::class.java))

        override fun onTemplates() {
            findNavController().navigate(R.id.commandTemplatesFragment)
        }

        override fun onLogs() {
            findNavController().navigate(R.id.downloadLogListFragment)
        }

        override fun onWatchedChannels() {
            findNavController().navigate(R.id.observeSourcesFragment)
        }

        override fun onCloseApp() = showTerminateConfirmationDialog()

        override fun onExpert(expert: Boolean) {
            settingsState.expert = expert
            mainSharedPreferences.edit().putBoolean(BasicSettings.EXPERT_MODE, expert).apply()
        }

        override fun onResetAdvanced() {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.vd_expert_reset_confirm_title)
                .setMessage(R.string.vd_expert_reset_confirm_text)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.reset) { _, _ ->
                    resetAdvancedSettings(requireContext())
                    Toast.makeText(requireContext(), R.string.vd_expert_reset_done, Toast.LENGTH_LONG).show()
                }
                .show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        mainActivity = activity as MainActivity
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        return inflater.inflate(R.layout.fragment_more, container, false)
    }
    override fun onResume() {
        super.onResume()
        // "Mostrar opciones avanzadas" in a settings page also unlocks Avanzado here
        if (this::mainSharedPreferences.isInitialized) settingsState.expert = mainSharedPreferences.getBoolean(BasicSettings.EXPERT_MODE, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mainSharedPreferences =  PreferenceManager.getDefaultSharedPreferences(requireContext())
        mainSharedPreferencesEditor = mainSharedPreferences.edit()
        settingsState.expert = mainSharedPreferences.getBoolean(BasicSettings.EXPERT_MODE, false)
        terminal = view.findViewById(R.id.terminal)
        logs = view.findViewById(R.id.logs)
        commandTemplates = view.findViewById(R.id.command_templates)
        downloads = view.findViewById(R.id.downloads)
        downloadQueue = view.findViewById(R.id.download_queue)
        cookies = view.findViewById(R.id.cookies)
        observeSources = view.findViewById(R.id.observe_sources)
        terminateApp = view.findViewById(R.id.terminate)
        settings = view.findViewById(R.id.settings)

        val appIcon = view.findViewById<ImageView>(R.id.app_icon)
        appIcon.backgroundTintList = null

        // Archivos in the bar already holds the downloads and the queue
        terminal.isVisible = true
        downloads.isVisible = false
        downloadQueue.isVisible = false

        terminal.setOnClickListener {
            val intent = Intent(context, TerminalActivity::class.java)
            startActivity(intent)
        }

        logs.setOnClickListener {
            findNavController().navigate(R.id.downloadLogListFragment)
        }

        commandTemplates.setOnClickListener {
            findNavController().navigate(R.id.commandTemplatesFragment)
        }

        downloads.setOnClickListener {
            findNavController().navigate(R.id.historyFragment)
        }

        downloadQueue.setOnClickListener {
            findNavController().navigate(R.id.downloadQueueMainFragment)
        }

        cookies.setOnClickListener {
            findNavController().navigate(R.id.cookiesFragment)
        }

        observeSources.setOnClickListener {
            findNavController().navigate(R.id.observeSourcesFragment)
        }

        terminateApp.setOnClickListener {
            showTerminateConfirmationDialog()
        }
        terminateApp.setOnLongClickListener {
            showTerminateConfirmationDialog(skipPreference = true)
            true
        }

        settings.setOnClickListener {
            val intent = Intent(context, SettingsActivity::class.java)
            startActivity(intent)
        }

        setupSettingsHome(view)

    }

    fun showTerminateConfirmationDialog(skipPreference: Boolean = false) {
        val shouldAskToTerminate = mainSharedPreferences.getBoolean("ask_terminate_app", true)
        if (!shouldAskToTerminate && !skipPreference) {
            terminateApp.isEnabled = false
            terminateApp()
            return
        }

        var doNotShowAgainFinalState = !shouldAskToTerminate

        lateinit var dialog: AlertDialog
        val terminateDialog = MaterialAlertDialogBuilder(requireContext())
        terminateDialog.setTitle(getString(R.string.kill_app))
        val dialogView = layoutInflater.inflate(R.layout.dialog_terminate_app, null)
        val checkbox = dialogView.findViewById<CheckBox>(R.id.doNotShowAgain)
        terminateDialog.setView(dialogView)

        checkbox.isChecked = doNotShowAgainFinalState
        checkbox.setOnCheckedChangeListener { _, isChecked ->
            doNotShowAgainFinalState = isChecked
        }

        terminateDialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface, _ ->
            dialogInterface.cancel()
        }

        terminateDialog.setPositiveButton(getString(R.string.ok), null)
        dialog = terminateDialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            dialog.setCanceledOnTouchOutside(false)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = false
            mainSharedPreferencesEditor.putBoolean("ask_terminate_app", !doNotShowAgainFinalState).commit()
            terminateApp()
        }
    }

    fun terminateApp() {
        lifecycleScope.launch {
            downloadViewModel.pauseAllDownloads()
            mainActivity.finishAndRemoveTask()
            mainActivity.finishAffinity()
            exitProcess(0)
        }
    }

    companion object {
        const val TAG = "MoreFragment"
    }

}


/**
 * Puts every setting of the advanced screen (res/xml/advanced_preferences.xml) back as it came with the app, as its own
 * "Restablecer" row does. The keys are read from the XML, so a setting added there is covered too.
 */
fun resetAdvancedSettings(context: Context) {
    val keys = mutableListOf<String>()
    context.resources.getXml(R.xml.advanced_preferences).use { parser ->
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            val key = parser.getAttributeValue("http://schemas.android.com/apk/res-auto", "key")
                ?: parser.getAttributeValue("http://schemas.android.com/apk/res/android", "key")
            if (!key.isNullOrBlank()) keys.add(key)
        }
    }
    val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    preferences.edit().apply { keys.forEach { remove(it) } }.commit()
    PreferenceManager.setDefaultValues(context, R.xml.advanced_preferences, true)
}
