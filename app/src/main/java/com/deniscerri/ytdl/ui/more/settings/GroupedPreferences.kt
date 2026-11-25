package com.deniscerri.ytdl.ui.more.settings

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceGroupAdapter
import androidx.preference.PreferenceViewHolder
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.google.android.material.color.MaterialColors
import kotlin.math.roundToInt

/**
 * The settings pages drawn like the Ajustes tab (the user asked for it on 24/11/2026): the settings of a section sit
 * together in one raised card with a hairline border and hairlines between rows, under the section's title.
 */
class GroupedPreferenceAdapter(group: PreferenceGroup) : PreferenceGroupAdapter(group) {

    override fun onBindViewHolder(holder: PreferenceViewHolder, position: Int) {
        super.onBindViewHolder(holder, position)
        val view = holder.itemView
        val context = view.context
        val margin = context.dp(16)
        (view.layoutParams as? RecyclerView.LayoutParams)?.let {
            if (it.marginStart != margin || it.marginEnd != margin) {
                it.marginStart = margin
                it.marginEnd = margin
                view.layoutParams = it
            }
        }
        val item = getItem(position)
        compact(holder, item is PreferenceCategory)
        if (item == null || item is PreferenceCategory) return
        val previous = if (position > 0) getItem(position - 1) else null
        val next = if (position < itemCount - 1) getItem(position + 1) else null
        view.background = rowBackground(context, top = previous == null || previous is PreferenceCategory, bottom = next == null || next is PreferenceCategory)
    }

    /**
     * Rows as compact as the Ajustes tab (the user found the settings pages too spaced out on 25/11/2026): less padding,
     * a 15 sp title and 13 sp summary, and a narrower icon column. Section titles sit close to their card.
     */
    private fun compact(holder: PreferenceViewHolder, category: Boolean) {
        val view = holder.itemView
        val context = view.context
        val title = holder.findViewById(android.R.id.title) as? TextView
        val summary = holder.findViewById(android.R.id.summary) as? TextView
        if (category) {
            // the section's name stands out (asked on 25/11/2026): main color, semibold, with a short bar before it
            view.setPaddingRelative(context.dp(4), context.dp(18), context.dp(4), context.dp(8))
            view.minimumHeight = 0
            title?.apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = ResourcesCompat.getFont(context, R.font.inter_semibold)
                letterSpacing = 0.02f
                val accent = MaterialColors.getColor(this, R.attr.vdColorPrimaryText)
                setTextColor(accent)
                val bar = GradientDrawable().apply {
                    cornerRadius = context.dp(2).toFloat()
                    setColor(accent)
                    setSize(context.dp(4), context.dp(16))
                }
                setCompoundDrawablesRelativeWithIntrinsicBounds(bar, null, null, null)
                compoundDrawablePadding = context.dp(8)
            }
            return
        }
        // the row's own 16 dp above and below the text sits on the box that holds title and summary
        view.setPaddingRelative(context.dp(12), 0, context.dp(12), 0)
        view.minimumHeight = context.dp(52)
        (title?.parent as? View)?.let { it.setPaddingRelative(it.paddingStart, context.dp(9), it.paddingEnd, context.dp(9)) }
        title?.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        summary?.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        (holder.findViewById(android.R.id.icon_frame) ?: holder.findViewById(androidx.preference.R.id.icon_frame))?.let {
            it.minimumWidth = context.dp(44)
            it.setPaddingRelative(0, 0, context.dp(12), 0)
        }
    }

    /** A piece of the card: rounded where the card starts or ends, with a hairline to the next row inside it */
    private fun rowBackground(context: Context, top: Boolean, bottom: Boolean): Drawable {
        val radius = context.dp(16).toFloat()
        val corners = floatArrayOf(
            if (top) radius else 0f, if (top) radius else 0f, if (top) radius else 0f, if (top) radius else 0f,
            if (bottom) radius else 0f, if (bottom) radius else 0f, if (bottom) radius else 0f, if (bottom) radius else 0f,
        )
        val line = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOutlineVariant, 0)
        val card = GradientDrawable().apply {
            cornerRadii = corners
            setColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceContainer, 0))
            setStroke(context.dp(1), line)
        }
        val divider = GradientDrawable().apply { setColor(line) }
        val layers = LayerDrawable(arrayOf(card, divider))
        // the card's border shows only on its outer edges: inner edges are pushed outside the row and clipped
        val hidden = context.dp(2)
        layers.setLayerInset(0, 0, if (top) 0 else -hidden, 0, if (bottom) 0 else -hidden)
        if (bottom) {
            layers.setLayerSize(1, 0, 0)
        } else {
            layers.setLayerGravity(1, Gravity.BOTTOM or Gravity.FILL_HORIZONTAL)
            layers.setLayerHeight(1, maxOf(1, context.dp(1) / 2))
            layers.setLayerInsetStart(1, context.dp(60))
        }
        val mask = GradientDrawable().apply { cornerRadii = corners; setColor(0xFFFFFFFF.toInt()) }
        val ripple = MaterialColors.getColor(context, com.google.android.material.R.attr.colorControlHighlight, 0x22FFFFFF)
        return RippleDrawable(ColorStateList.valueOf(ripple), layers, mask)
    }
}

/**
 * Every icon at the same size as the rows of the Ajustes tab, whether it is a 48 dp color drawing or a 24 dp line icon,
 * and section titles flush with the cards instead of indented for an icon they do not have.
 */
fun PreferenceGroup.prepareForCards(context: Context) {
    for (i in 0 until preferenceCount) {
        val preference = getPreference(i)
        if (preference is PreferenceGroup) {
            if (preference is PreferenceCategory) preference.isIconSpaceReserved = false
            preference.prepareForCards(context)
        } else {
            preference.sizeIcon(context)
        }
    }
}

private class SizedIcon(icon: Drawable) : LayerDrawable(arrayOf(icon))

private fun Preference.sizeIcon(context: Context) {
    val original = icon ?: return
    if (original is SizedIcon) return
    val side = context.dp(32)
    icon = SizedIcon(original).apply { setLayerSize(0, side, side) }
}

private fun Context.dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
