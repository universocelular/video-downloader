package com.deniscerri.ytdl.receiver

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.MainActivity
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.ResultItem
import com.deniscerri.ytdl.database.viewmodel.CookieViewModel
import com.deniscerri.ytdl.database.viewmodel.DownloadCardViewModel
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.database.viewmodel.HistoryViewModel
import com.deniscerri.ytdl.database.viewmodel.ResultViewModel
import com.deniscerri.ytdl.ui.BaseActivity
import com.deniscerri.ytdl.ui.home.SimpleDownloadHost
import com.deniscerri.ytdl.ui.home.SimpleDownloadSheet
import com.deniscerri.ytdl.util.Extensions.extractURL
import com.deniscerri.ytdl.whatsapp.GlassTheme
import com.deniscerri.ytdl.whatsapp.SplitForWhatsAppActivity
import com.deniscerri.ytdl.util.ThemeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.properties.Delegates


class ShareActivity : BaseActivity(), SimpleDownloadHost {

    lateinit var context: Context
    private lateinit var resultViewModel: ResultViewModel
    private lateinit var historyViewModel: HistoryViewModel
    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var cookieViewModel: CookieViewModel
    private lateinit var downloadCardViewModel: DownloadCardViewModel
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var navController: NavController
    private var quickDownload by Delegates.notNull<Boolean>()

    private lateinit var wm: WindowManager
    private lateinit var myView: View


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeUtil.updateTheme(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { v, insets ->
            v.setPadding(0, 0, 0, 0)
            insets
        }

        if (Settings.canDrawOverlays(this)){
            val params = WindowManager.LayoutParams(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                },
                PixelFormat.TRANSLUCENT
            )
            wm = getSystemService(WINDOW_SERVICE) as WindowManager

            val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
            myView = inflater.inflate(R.layout.activity_share, null)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            wm.addView(myView, params)

            // window.addFlags(
            //     WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            //             or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            //             or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            // )
            //
            // val params = window.attributes
            // params.alpha = 0f
            // window.attributes = params
            setContentView(R.layout.activity_share)

        }else{
            window.run {
                setBackgroundDrawable(ColorDrawable(0))
                setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
                } else {
                    setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT)
                }
            }

            setContentView(R.layout.activity_share)
        }

        context = baseContext
        resultViewModel = ViewModelProvider(this)[ResultViewModel::class.java]
        historyViewModel = ViewModelProvider(this)[HistoryViewModel::class.java]
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        cookieViewModel = ViewModelProvider(this)[CookieViewModel::class.java]
        downloadCardViewModel = ViewModelProvider(this)[DownloadCardViewModel::class.java]
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        cookieViewModel.updateCookiesFile()
        val intent = intent
        handleIntents(intent)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntents(intent)
    }

    private fun handleIntents(intent: Intent) {
        askPermissions()

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.frame_layout) as NavHostFragment
        navController = navHostFragment.findNavController()
        navController.addOnDestinationChangedListener(object: NavController.OnDestinationChangedListener{
            @SuppressLint("RestrictedApi")
            override fun onDestinationChanged(
                controller: NavController,
                destination: NavDestination,
                arguments: Bundle?
            ) {
                navController.removeOnDestinationChangedListener(this)
                CoroutineScope(SupervisorJob()).launch {
                    navController.currentBackStack.collectLatest {
                        if (it.isEmpty()){
                            this@ShareActivity.finish()
                        }
                    }
                }
            }
        })

        val action = intent.action
        Log.e("aa", intent.toString())
        if (Intent.ACTION_SEND == action || Intent.ACTION_VIEW == action) {
            if (intent.getStringExtra(Intent.EXTRA_TEXT) == null && Intent.ACTION_SEND == action){
                intent.setClass(this, MainActivity::class.java)
                startActivity(intent)
                finishAffinity()
                return
            }

            runCatching { supportFragmentManager.popBackStack() }

            quickDownload = intent.getBooleanExtra("quick_download", sharedPreferences.getBoolean("quick_download", false) || sharedPreferences.getString("preferred_download_type", "video") == "command")
            val data = when(action){
                Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)!!
                else -> intent.dataString!!
            }

            val inputQuery = data.extractURL()
            val ai = packageManager.getActivityInfo(componentName, PackageManager.GET_META_DATA)

            val type = intent.getStringExtra("TYPE")
            val background = intent.getBooleanExtra("BACKGROUND", ai.metaData?.getBoolean("quick_run_background", false) == true)

            lifecycleScope.launch {
                val result: ResultItem
                val existingResults = withContext(Dispatchers.IO){
                    resultViewModel.getAllByURL(inputQuery)
                }

                if (existingResults.isEmpty() || existingResults.size > 1) {
                    resultViewModel.deleteAll()
                    result = downloadViewModel.createEmptyResultItem(inputQuery)
                }else{
                    result = existingResults.first()
                }

                val downloadType = DownloadType.valueOf(type ?: downloadViewModel.getDownloadType(url = result.url).toString())
                if (sharedPreferences.getBoolean("download_card", true) && !background){
                    // the redesign's short sheet; "Más opciones" still leads to the full one
                    showShareSheet(result, downloadType)
                }else{
                    Toast.makeText(this@ShareActivity, "${getString(R.string.downloading)} $inputQuery", Toast.LENGTH_SHORT).show()

                    lifecycleScope.launch(Dispatchers.IO){
                        val downloadItem = downloadViewModel.createDownloadItemFromResult(
                            result = result,
                            givenType = downloadType)

                        downloadViewModel.queueDownloads(listOf(downloadItem))
                    }
                    this@ShareActivity.finish()
                }
            }
        }
    }
    // The sheet of diseño/imagenes/02-5-compartir.png over the app the link came from
    private var shareState by mutableStateOf<ShareUiState?>(null)
    private var shareView: ComposeView? = null
    private var sharedResult: ResultItem? = null
    private var sharedType = DownloadType.video

    private fun showShareSheet(result: ResultItem, type: DownloadType) {
        sharedResult = result
        sharedType = type
        val state = ShareUiState(result.url, sharedPreferences.getStringSet("hide_thumbnails", emptySet())!!.contains("home"))
        shareState = state
        if (shareView == null) {
            val view = ComposeView(this).apply { setContent { GlassTheme { shareState?.let { ShareSheet(it, shareActions) } } } }
            addContentView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            shareView = view
        }
        // a link read before needs no second look
        if (result.title.isNotBlank() && result.formats.isNotEmpty()) {
            state.result = result
            return
        }
        // read the link as Inicio does, so the sheet shows the video and the simple download offers its qualities
        lifecycleScope.launch(Dispatchers.IO) {
            resultViewModel.parseQueries(listOf(result.url)) { found ->
                lifecycleScope.launch(Dispatchers.Main) {
                    if (shareState !== state) return@launch
                    val videos = found.filterNotNull()
                    when {
                        videos.size == 1 -> state.result = videos.first()
                        // a playlist: only the full sheet can choose its videos
                        videos.size > 1 -> openFullDownloadSheet(result, type)
                        else -> state.failed = true
                    }
                }
            }
        }
    }

    private val shareActions = ShareActions(
        onDownload = {
            val video = shareState?.result
            if (video != null) {
                downloadCardViewModel.setResultItem(video)
                downloadCardViewModel.setDownloadItem(null)
                if (supportFragmentManager.findFragmentByTag(SIMPLE_SHEET) == null) SimpleDownloadSheet().show(supportFragmentManager, SIMPLE_SHEET)
            } else {
                downloadAsIs()
            }
        },
        onSplit = {
            shareState?.url?.let { url ->
                startActivity(
                    Intent(this, SplitForWhatsAppActivity::class.java)
                        .setAction(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
                )
            }
            finish()
        },
        onMore = { sharedResult?.let { openFullDownloadSheet(shareState?.result ?: it, sharedType) } },
        onDismiss = { finish() },
    )

    /** "Descargar" when the link could not be read: queued as it is, with the user's preferences */
    private fun downloadAsIs() {
        val result = sharedResult ?: return
        val type = sharedType
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                downloadViewModel.queueDownloads(listOf(downloadViewModel.createDownloadItemFromResult(result = result, givenType = type)))
            }
            Toast.makeText(this@ShareActivity, "${getString(R.string.downloading)} ${result.url}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun openFullDownloadSheet(result: ResultItem, type: DownloadType) {
        shareState?.visible = false
        downloadCardViewModel.setResultItem(result)
        downloadCardViewModel.setDownloadItem(null)
        navController.setGraph(R.navigation.share_nav_graph, Bundle().apply { putSerializable("type", type) })
    }

    override fun onDownloadQueued() = finish()

    override fun onConfigurationChanged(newConfig: Configuration) {
        startActivity(Intent(this, MainActivity::class.java))
        super.onConfigurationChanged(newConfig)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::wm.isInitialized && ::myView.isInitialized) {
            try {
                wm.removeView(myView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        )
    }

    companion object {
        private const val SIMPLE_SHEET = "simpleDownload"
    }
}
