package com.noop.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// MARK: - Saħħa looks (fork-only)
//
// Two dark looks over the Slate layout and screens: SAGE (calm-but-powerful: jade energy, a gold ensō) and
// CONQUEROR (armoured: violet and crimson, a hex reticle). There is no Classic look any more: on first launch
// the Slate layout is switched on (its saved-prefs bundle, see SlateLook) and CONQUEROR is chosen, which is
// what the Slate look looked like. Pure presentation: no metric, stored value or backup key changes.

enum class AppLook { SAGE, CONQUEROR }

object SahhaLook {
    private const val KEY_LOOK = "fork.sahhaLook"

    /** Read by every Slate token getter, so switching recomposes the Slate screens in the other look. */
    var look by mutableStateOf(AppLook.CONQUEROR)
        private set

    val sage: Boolean get() = look == AppLook.SAGE

    /** Call after [SlateLook.load]: makes sure the Slate layout is on, then restores the chosen look. */
    fun load(ctx: Context) {
        if (!SlateLook.enabled) SlateLook.setEnabled(ctx, true)
        look = runCatching { AppLook.valueOf(NoopPrefs.of(ctx).getString(KEY_LOOK, null) ?: "CONQUEROR") }
            .getOrDefault(AppLook.CONQUEROR)
    }

    fun select(ctx: Context, next: AppLook) {
        NoopPrefs.of(ctx).edit().putString(KEY_LOOK, next.name).apply()
        look = next
    }
}
