package com.deniscerri.ytdl.ui.downloads

import android.view.View
import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.deniscerri.ytdl.R
import com.google.android.material.button.MaterialButtonToggleGroup

// "En curso | Listos" at the top of Archivos. Both lists stay separate destinations under the same bar tab;
// switching replaces one with the other, so Back still leaves Archivos.
object FilesSelector {
    fun bind(fragment: Fragment, view: View, @IdRes currentDestination: Int) {
        val selector = view.findViewById<MaterialButtonToggleGroup>(R.id.files_selector)
        selector.check(if (currentDestination == R.id.downloadQueueMainFragment) R.id.files_in_progress else R.id.files_ready)
        selector.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val target = if (checkedId == R.id.files_in_progress) R.id.downloadQueueMainFragment else R.id.historyFragment
            if (target == currentDestination) return@addOnButtonCheckedListener
            fragment.findNavController().navigate(target, null, navOptions {
                popUpTo(currentDestination) { inclusive = true }
                launchSingleTop = true
            })
        }
    }
}
