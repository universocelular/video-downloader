package com.deniscerri.ytdl.util

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.MainActivity
import com.deniscerri.ytdl.R


object ThemeUtil {

    private val activities = mutableListOf<Activity>()

    fun init(app: Application) {
        app.registerActivityLifecycleCallbacks(object: Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(p0: Activity, p1: Bundle?) {
                activities.add(p0)
            }

            override fun onActivityStarted(p0: Activity) {

            }

            override fun onActivityResumed(p0: Activity) {

            }

            override fun onActivityPaused(p0: Activity) {

            }

            override fun onActivityStopped(p0: Activity) {

            }

            override fun onActivitySaveInstanceState(p0: Activity, p1: Bundle) {

            }

            override fun onActivityDestroyed(p0: Activity) {
                activities.remove(p0)
            }
        })

    }

    sealed class AppIcon(
        @StringRes val nameResource: Int,
        @DrawableRes val iconResource: Int,
        val activityAlias: String
    ) {
        object Default : AppIcon(R.string.auto, R.mipmap.ic_launcher, "Default")
        object Light : AppIcon(R.string.light, R.mipmap.ic_launcher_light, "LightIcon")
        object Dark : AppIcon(R.string.dark, R.mipmap.ic_launcher_dark, "DarkIcon")
        object Blue : AppIcon(R.string.blue, R.mipmap.ic_launcher_blue, "BlueIcon")
        object Green : AppIcon(R.string.green, R.mipmap.ic_launcher_green, "GreenIcon")
    }

    // Blue and green are no longer offered. Their aliases stay in the manifest and are still switched off,
    // so an older choice falls back to the default icon instead of leaving two icons on the launcher.
    val availableIcons = listOf(
        AppIcon.Default,
        AppIcon.Light,
        AppIcon.Dark,
    )
    private val allIcons = availableIcons + listOf(AppIcon.Blue, AppIcon.Green)

    // "Color principal" choices. Older builds stored other accents (blue, red, Material You...); they become violet.
    enum class Accent(val key: String, @StyleRes val theme: Int) {
        Violet("violet", R.style.BaseTheme),
        Turquoise("turquoise", R.style.Theme_Turquoise),
        Pink("pink", R.style.Theme_Pink),
        Orange("orange", R.style.Theme_Orange),
        Lime("lime", R.style.Theme_Lime);

        companion object {
            fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: Violet
        }
    }

    fun recreateMain() {
        activities.firstOrNull { it.javaClass == MainActivity::class.java }?.recreate()
    }

    fun recreateAllActivities() {
        activities.forEach {
            it.recreate()
        }
    }

    fun updateThemes() {
        activities.forEach {
            updateTheme(it)
            it.recreate()
        }
    }

    fun updateTheme(activity: Activity) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)

        //update accent
        activity.setTheme(Accent.fromKey(sharedPreferences.getString("theme_accent", Accent.Violet.key)).theme)

        //high contrast theme
        if (sharedPreferences.getBoolean("high_contrast",false)) {
            activity.theme.applyStyle(R.style.Pure, true)
        }

        val theme = sharedPreferences.getString("ytdlnis_theme", "System")!!
        when (theme) {
            "Light" -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
            "Dark" -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
            // or "System"
            else -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }


        val iconMode = sharedPreferences.getString("ytdlnis_icon", "Default")!!
        updateAppIcon(activity,theme, iconMode)
    }

    fun getAppName(context: Context): String = context.getString(R.string.app_name)


    fun updateAppIcon(activity: Activity, theme: String, appIconMode: String) {
        //disable old icons
        for (appIcon in allIcons) {
            val activityClass = "com.deniscerri.ytdl." + appIcon.activityAlias

            // remove old icons
            activity.packageManager.setComponentEnabledSetting(
                ComponentName(activity.packageName, activityClass),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }

        var iconMode = appIconMode
        if (appIconMode == "Default") {
            iconMode = theme
        }

        when (iconMode) {
            "LightIcon" -> {
                activity.packageManager.setComponentEnabledSetting(
                    ComponentName(activity.packageName, "com.deniscerri.ytdl.LightIcon"),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
            "DarkIcon" -> {
                activity.packageManager.setComponentEnabledSetting(
                    ComponentName(activity.packageName, "com.deniscerri.ytdl.DarkIcon"),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
            // or "System"
            else -> {
                //set dynamic icon
                activity.packageManager.setComponentEnabledSetting(
                    ComponentName(activity.packageName, "com.deniscerri.ytdl.Default"),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        }
    }
}