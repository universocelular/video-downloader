package com.deniscerri.ytdl.util

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.google.android.material.navigation.NavigationBarView


object NavbarUtil {

    lateinit var settings : SharedPreferences

    fun init(context: Context){
        settings = PreferenceManager.getDefaultSharedPreferences(context)
    }

    // The bar is fixed: Inicio, Archivos and Ajustes. Archivos opens Listos (history), and its selector switches to
    // En curso (the queue), so both keep the bar visible.
    private val barDestinations = setOf(
        R.id.homeFragment,
        R.id.historyFragment,
        R.id.downloadQueueMainFragment,
        R.id.moreFragment
    )

    fun isBarDestination(destinationId: Int?) = destinationId in barDestinations

    fun NavigationBarView.setLabelVisibility() {
        val labelVisibilityMode = when (
            settings.getString("label_visibility", "always")
        ) {
            "always" -> NavigationBarView.LABEL_VISIBILITY_LABELED
            "selected" -> NavigationBarView.LABEL_VISIBILITY_SELECTED
            "never" -> NavigationBarView.LABEL_VISIBILITY_UNLABELED
            else -> NavigationBarView.LABEL_VISIBILITY_AUTO
        }
        this.labelVisibilityMode = labelVisibilityMode
    }
}
