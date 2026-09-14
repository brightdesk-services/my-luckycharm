package com.brightdesk.myluckycharm.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brightdesk.myluckycharm.render.BUILT_IN_CHARMS
import com.brightdesk.myluckycharm.system.CharmImageStore
import kotlin.math.roundToInt

private val TILE_SHAPE = RoundedCornerShape(14.dp)

/** Keeps every tab the same height, so switching tabs doesn't resize the sheet under the finger. */
private val GRID_HEIGHT_DP = 264.dp

/**
 * Sized so four columns fit a phone once the grid's own padding and gaps are
 * taken out (360dp - 32 - 24 = 304, four 72dp tiles fit with room to spare);
 * anything larger silently drops to three and makes the tiles look oversized.
 */
private val TILE_MIN_DP = 72.dp

private val SHEET_SHAPE = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

/** How far the sheet has to be dragged down before letting go dismisses it. */
private val DISMISS_DRAG_DP = 90.dp

/** Fling speed (px/s) that dismisses regardless of how far the drag got. */
private const val DISMISS_VELOCITY = 1200f

private const val SHEET_ANIM_MS = 240

/**
 * The charm picker: a sheet over the home screen, one tab per source (bundled
 * art, emoji, your own photo) and a named tile per charm.
 *
 * A sheet rather than a Settings section because the charm hangs in the top
 * half of the screen and the sheet covers only the bottom — so a pick swaps
 * what you are looking at, live, with no confirm step and nothing to navigate
 * back from. Nothing here is a draft: every tile writes straight through
 * [onChange], and the sheet stays open afterwards so charms can be tried on.
 *
 * **Hand-rolled rather than `ModalBottomSheet`, and that is the point.** The
 * Material sheet puts itself in its own window, and standing that window up
 * costs a ~117ms frame on every open — measured against 36ms for swapping in
 * the whole Settings screen, which composes far more. That one long frame is
 * exactly the hitch this sheet was reported as having. Living in the host's
 * composition removes the window entirely; it also means [visible] drives an
 * ordinary enter/exit transition, with no sheet state machine whose anchors
 * have to be reasoned about. (Its half-expanded anchor was a second bug: the
 * content is deliberately shorter than half the screen, so that anchor sat
 * below the sheet with nothing to settle onto, and the drag handle's
 * "collapse one step" click had only *hide* left to do — which is why tapping
 * the handle closed it.)
 *
 * Dismissal is therefore spelled out here: the scrim, the back gesture, and a
 * downward drag on the header. Tapping the handle deliberately does nothing.
 */
@Composable
fun CharmSheet(
    visible: Boolean,
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onPickPhoto: () -> Unit,
    onDismiss: () -> Unit,
) {
    var tabIndex by remember { mutableIntStateOf(CharmTab.of(settings.charmType).ordinal) }
    val tab = CharmTab.entries[tabIndex]
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val dismissDragPx = with(LocalDensity.current) { DISMISS_DRAG_DP.toPx() }

    // Reset when the sheet is *shown*, never when it is hidden — keying these
    // on `visible` instead (`remember(visible)`) resets them the instant
    // dismissal starts, while the exit animation is still running: the sheet
    // snaps back up to its resting place and then slides away a second time,
    // and the tab swaps underneath it mid-slide. Dragging the sheet down to
    // close it made that double-close obvious. Letting the offset stand until
    // the next showing means the exit simply continues from wherever the
    // finger left it.
    //
    // Reopening picks the tab the current charm came from, so the sheet shows
    // the thing that is already hanging rather than always starting at Art.
    LaunchedEffect(visible) {
        if (visible) {
            tabIndex = CharmTab.of(settings.charmType).ordinal
            dragOffset = 0f
        }
    }

    BackHandler(enabled = visible, onBack = onDismiss)

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(SHEET_ANIM_MS)),
            exit = fadeOut(tween(SHEET_ANIM_MS)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f))
                    .pointerInput(Unit) { detectTapGestures { onDismiss() } },
            )
        }

        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(SHEET_ANIM_MS)) { it },
            exit = slideOutVertically(tween(SHEET_ANIM_MS)) { it },
        ) {
            Surface(
                modifier = Modifier.offset { IntOffset(0, dragOffset.roundToInt()) },
                shape = SHEET_SHAPE,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp,
            ) {
                // The sheet draws behind the gesture bar, which otherwise eats
                // the grid's last row.
                Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
                    SheetHeader(
                        // Only the header drags: the grid below it has to keep
                        // its own vertical scroll.
                        modifier = Modifier.draggable(
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { delta ->
                                // Downward only — dragging up must not lift the
                                // sheet off the bottom of the screen.
                                dragOffset = (dragOffset + delta).coerceAtLeast(0f)
                            },
                            onDragStopped = { velocity ->
                                if (dragOffset > dismissDragPx || velocity > DISMISS_VELOCITY) {
                                    onDismiss()
                                } else {
                                    animate(dragOffset, 0f) { value, _ -> dragOffset = value }
                                }
                            },
                        ),
                    )

                    PrimaryTabRow(selectedTabIndex = tabIndex) {
                        CharmTab.entries.forEach { entry ->
                            Tab(
                                selected = entry == tab,
                                onClick = { tabIndex = entry.ordinal },
                                text = { Text(entry.label) },
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = GRID_HEIGHT_DP, max = GRID_HEIGHT_DP),
                    ) {
                        when (tab) {
                            CharmTab.ART -> ArtGrid(settings, onChange)
                            CharmTab.EMOJI -> EmojiGrid(settings, onChange)
                            CharmTab.PHOTO -> PhotoTab(settings, onChange, onPickPhoto)
                        }
                    }
                }
            }
        }
    }
}

/** The grab area: a handle bar and the title, both of which drag the sheet down. */
@Composable
private fun SheetHeader(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .padding(vertical = 12.dp)
                .align(Alignment.CenterHorizontally)
                .size(width = 32.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
        )
        Text(
            "Charm",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 20.dp, bottom = 12.dp),
        )
    }
}

private enum class CharmTab(val label: String) {
    ART("Art"),
    EMOJI("Emoji"),
    PHOTO("Photo"),
    ;

    companion object {
        fun of(type: CharmType): CharmTab = when (type) {
            CharmType.BUILT_IN -> ART
            CharmType.EMOJI -> EMOJI
            CharmType.CUSTOM_IMAGE -> PHOTO
        }
    }
}

@Composable
private fun CharmGrid(content: LazyGridScope.() -> Unit) {
    LazyVerticalGrid(
        // Adaptive rather than a fixed count: the same tile size then gives
        // four columns on a phone and more on anything wider, instead of
        // stretching four tiles across a tablet.
        columns = GridCells.Adaptive(minSize = TILE_MIN_DP),
        modifier = Modifier
            .fillMaxSize()
            .selectableGroup(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun ArtGrid(settings: AppSettings, onChange: ((AppSettings) -> AppSettings) -> Unit) {
    CharmGrid {
        items(BUILT_IN_CHARMS, key = { it.id }) { charm ->
            val isSelected = settings.charmType == CharmType.BUILT_IN &&
                settings.builtInCharmId == charm.id
            CharmTile(
                label = charm.label,
                isSelected = isSelected,
                onClick = {
                    onChange { it.copy(charmType = CharmType.BUILT_IN, builtInCharmId = charm.id) }
                },
            ) {
                Image(
                    painter = painterResource(charm.drawableRes),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(10.dp),
                )
            }
        }
    }
}

@Composable
private fun EmojiGrid(settings: AppSettings, onChange: ((AppSettings) -> AppSettings) -> Unit) {
    // Anything outside the curated set can only have come from the keyboard,
    // so the typed tile owns it — and picking a curated tile deselects that
    // one for free, and vice versa.
    val isTyped = settings.charmType == CharmType.EMOJI &&
        settings.emojiChar !in CHARM_EMOJI_CHARS

    CharmGrid {
        item(key = "typed") {
            TypedEmojiTile(
                value = if (isTyped) settings.emojiChar else "",
                isSelected = isTyped,
                onPicked = { emoji ->
                    onChange { it.copy(charmType = CharmType.EMOJI, emojiChar = emoji) }
                },
            )
        }
        items(CHARM_EMOJI, key = { it.character }) { entry ->
            val isSelected = settings.charmType == CharmType.EMOJI &&
                settings.emojiChar == entry.character
            CharmTile(
                label = entry.label,
                isSelected = isSelected,
                onClick = {
                    onChange {
                        it.copy(charmType = CharmType.EMOJI, emojiChar = entry.character)
                    }
                },
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(entry.character, fontSize = 30.sp)
                }
            }
        }
    }
}

@Composable
private fun PhotoTab(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onPickPhoto: () -> Unit,
) {
    val path = settings.customImagePath
    // Decoded off the composition's hot path by keying on the path itself —
    // this only re-reads the file when a different photo is picked.
    val image = remember(path) { path?.let { CharmImageStore.load(it)?.asImageBitmap() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (image != null) {
            // Selectable in its own right, so a photo picked earlier can be put
            // back on the rope without going through the system picker again.
            CharmTile(
                label = "Your photo",
                isSelected = settings.charmType == CharmType.CUSTOM_IMAGE,
                onClick = { onChange { it.copy(charmType = CharmType.CUSTOM_IMAGE) } },
                // Width, not size: the tile is square via aspectRatio, so a fixed
                // height would swallow the row the label needs underneath it.
                modifier = Modifier.width(120.dp),
            ) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp)
                        .clip(RoundedCornerShape(10.dp)),
                )
            }
        } else {
            Text(
                "No photo yet — pick one and it hangs on the rope.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }

        TextButton(onClick = onPickPhoto, modifier = Modifier.padding(top = 8.dp)) {
            Text(if (image == null) "Choose a photo" else "Choose a different photo")
        }

        Text(
            "The photo is copied into this app and shrunk to 512px. " +
                "Only the copy is used, so moving or deleting the original is fine.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/**
 * One square of charm above its name. The name is the point of the grid —
 * "which crescent is that" is not obvious from a 40dp glyph alone.
 */
@Composable
private fun CharmTile(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        // Clipped before selectable so the press ripple follows the tile's
        // rounded shape instead of painting a hard rectangle behind the label.
        modifier = modifier
            .clip(TILE_SHAPE)
            .selectable(
                selected = isSelected,
                onClick = onClick,
                role = Role.RadioButton,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TileFrame(isSelected = isSelected, content = content)
        TileLabel(label, isSelected)
    }
}

@Composable
private fun TileFrame(
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(TILE_SHAPE)
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                },
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = TILE_SHAPE,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun TileLabel(label: String, isSelected: Boolean) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    )
}

/**
 * A text field styled to match the other tiles rather than a button, because
 * there's nothing to navigate to — typing, including through the keyboard's own
 * emoji tab, *is* the selection, applied as soon as anything is entered. It
 * keeps the curated palette from being the only choice.
 */
@Composable
private fun TypedEmojiTile(value: String, isSelected: Boolean, onPicked: (String) -> Unit) {
    var fieldValue by remember(value) {
        mutableStateOf(TextFieldValue(value, selection = TextRange(value.length)))
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TileFrame(isSelected = isSelected) {
            BasicTextField(
                value = fieldValue,
                onValueChange = { new ->
                    fieldValue = new
                    if (new.text.isNotEmpty()) onPicked(new.text)
                },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 30.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.fillMaxSize(),
                decorationBox = { innerField ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (fieldValue.text.isEmpty()) {
                            Text(
                                "⌨",
                                fontSize = 28.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        innerField()
                    }
                },
            )
        }
        TileLabel("Type one", isSelected)
    }
}
