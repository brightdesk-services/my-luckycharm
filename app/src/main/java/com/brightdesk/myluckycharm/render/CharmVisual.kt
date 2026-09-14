package com.brightdesk.myluckycharm.render

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.brightdesk.myluckycharm.settings.AppSettings
import com.brightdesk.myluckycharm.settings.CharmType
import com.brightdesk.myluckycharm.system.CharmImageStore

/** The three charm sources from spec §8, resolved to something drawable. */
sealed interface CharmVisual {
    /**
     * Carries the [BuiltInCharm] alongside its painter because the art is no
     * longer square and no longer shares one ring position — the renderer needs
     * this charm's own aspect ratio and attach point to place it on the rope.
     */
    data class Art(val painter: Painter, val charm: BuiltInCharm) : CharmVisual
    data class Photo(val image: ImageBitmap) : CharmVisual
    data class Emoji(val character: String) : CharmVisual
}

@Composable
fun rememberCharmVisual(settings: AppSettings): CharmVisual {
    // painterResource has to run on every composition, so the built-in art is
    // resolved unconditionally and doubles as the fallback.
    val builtIn = builtInCharmOrDefault(settings.builtInCharmId)
    val builtInPainter = painterResource(builtIn.drawableRes)

    val customPath = settings.customImagePath
    val customImage: ImageBitmap? = remember(customPath) {
        customPath?.let { CharmImageStore.load(it)?.asImageBitmap() }
    }

    return when (settings.charmType) {
        CharmType.BUILT_IN -> CharmVisual.Art(builtInPainter, builtIn)
        CharmType.EMOJI -> CharmVisual.Emoji(settings.emojiChar)
        // If the file is gone (cleared app data, say) show a charm rather than nothing.
        CharmType.CUSTOM_IMAGE ->
            customImage?.let { CharmVisual.Photo(it) } ?: CharmVisual.Art(builtInPainter, builtIn)
    }
}
