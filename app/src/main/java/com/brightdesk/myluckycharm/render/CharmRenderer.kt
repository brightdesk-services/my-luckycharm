package com.brightdesk.myluckycharm.render

import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.brightdesk.myluckycharm.physics.RopePoint
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.roundToInt

/** How far the rope runs under a ringless charm, as a fraction of the art size. */
private const val ROPE_TUCK_FRACTION = 0.22f

private val RopeColor = Color(0xFF6E6552)
private val BeadColor = Color(0xFFE8D9B5)

/**
 * Only 7 rope segments exist, so drawing them as straight lines makes every
 * bend a visible kink. This fits a Catmull-Rom spline (expressed as cubic
 * Béziers) through the points: it passes through each simulated point exactly
 * while curving between them, so the rope reads as cord rather than linkage.
 */
fun DrawScope.drawRope(
    points: List<RopePoint>,
    strokeWidth: Float,
    scratch: RopeScratch,
    endInset: Float = 0f,
) {
    if (points.size < 2) return
    val last = points.lastIndex

    // The rope hangs from the charm's *attach point*, which sits at the centre
    // of the ring the art is drawn hanging from — so a rope drawn all the way
    // to it runs through the ring and out the other side, reading as a cord
    // laid over the charm rather than tied to it. Stopping [endInset] short
    // leaves it touching the top of the ring, which is where a real cord ends.
    // A negative [endInset] extends the rope the other way instead, to be
    // covered by art that has no ring to stop at.
    var endX = points[last].x
    var endY = points[last].y
    if (endInset != 0f) {
        val prev = points[last - 1]
        val dx = endX - prev.x
        val dy = endY - prev.y
        val length = sqrt(dx * dx + dy * dy)
        // A deep inset on a short final segment would fold the rope back on
        // itself, so it never pulls past the previous point. A negative inset
        // runs the other way and pushes the end past the charm's attach point.
        val t = if (length > endInset) (length - endInset) / length else 0f
        endX = prev.x + dx * t
        endY = prev.y + dy * t
    }

    fun pointX(index: Int) = if (index == last) endX else points[index].x
    fun pointY(index: Int) = if (index == last) endY else points[index].y

    val path = scratch.path
    path.rewind()
    path.moveTo(points[0].x, points[0].y)
    for (index in 0 until last) {
        val p = (index - 1).coerceAtLeast(0)
        val n = (index + 2).coerceAtMost(last)
        val startX = pointX(index)
        val startY = pointY(index)
        val eX = pointX(index + 1)
        val eY = pointY(index + 1)
        path.cubicTo(
            startX + (eX - pointX(p)) / 6f,
            startY + (eY - pointY(p)) / 6f,
            eX - (pointX(n) - startX) / 6f,
            eY - (pointY(n) - startY) / 6f,
            eX,
            eY,
        )
    }
    drawPath(path = path, color = RopeColor, style = scratch.stroke(strokeWidth))
}

/**
 * Where an emoji's visible ink starts inside its line box, held across frames
 * by the caller.
 *
 * Text layout gives the box, not the drawing. [android.graphics.Paint] is the
 * only cheap way to the ink extent — `getTextBounds` reports it directly rather
 * than making us rasterise the glyph and hunt for its first opaque row — but it
 * allocates nothing only if the [android.graphics.Paint] and
 * [android.graphics.Rect] are reused, and this runs inside a draw that repeats
 * at the display's refresh rate. The measurement is also memoised per character
 * and size, since it changes only when the charm or the size slider does.
 */
class EmojiInk {
    private val paint = Paint()
    private val bounds = Rect()
    private var character: String? = null
    private var fontSize = Float.NaN
    private var cached = 0f

    /**
     * Distance from the baseline up to the first inked row, as a negative
     * number in the same direction as the y axis.
     */
    internal fun topAboveBaseline(character: String, fontSize: Float): Float {
        if (character != this.character || fontSize != this.fontSize) {
            this.character = character
            this.fontSize = fontSize
            paint.textSize = fontSize
            paint.getTextBounds(character, 0, character.length, bounds)
            cached = bounds.top.toFloat()
        }
        return cached
    }
}

/**
 * How far short of the attach point the rope should stop, so it ends where it
 * first meets the charm instead of crossing it.
 *
 * For bundled art this is the radius of its hanging ring, and it comes for
 * free: the art is trimmed to its opaque bounds, so its top edge *is* the top
 * of the ring, which makes the ring centre's distance from that edge —
 * `attachYFraction * height` — the radius.
 *
 * Emoji and photos have no ring — they used to carry a small drawn bead at the
 * attach point standing in for one, which read as an unexplained dot — so the
 * rope has to meet the art itself. Their inset is *negative*: the rope is run a
 * little past the attach point and the charm, drawn immediately afterwards,
 * covers the overlap. That makes the visible end of the rope the art's own
 * silhouette, which is the only way to land on it for a shape whose topmost
 * pixel is nowhere near its centre — a feather lying diagonally across its box,
 * say. Stopping at the attach point instead leaves a gap beside such a glyph
 * however carefully the box is aligned.
 */
fun ropeEndInset(visual: CharmVisual, artSize: Float): Float = when (visual) {
    is CharmVisual.Art -> artSize * visual.charm.attachYFraction
    is CharmVisual.Emoji, is CharmVisual.Photo -> -artSize * ROPE_TUCK_FRACTION
}

/**
 * Per-surface scratch for [drawRope], held across frames by the caller.
 *
 * A [Path] is backed by a native Skia object, so building a fresh one inside a
 * draw that runs at the display's refresh rate was the only allocation left in
 * the frame loop worth removing — everything else in the rope and charm draw is
 * either an inline value class or hoisted behind `remember`. The path is
 * rewound and refilled each frame; the [Stroke] is rebuilt only when the rope
 * thickness actually changes, which happens when a Tune slider moves.
 */
class RopeScratch {
    internal val path = Path()

    private var strokeWidth = Float.NaN
    private var cached = Stroke(width = 0f, cap = StrokeCap.Round, join = StrokeJoin.Round)

    internal fun stroke(width: Float): Stroke {
        if (width != strokeWidth) {
            strokeWidth = width
            cached = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
        }
        return cached
    }
}

/** Small knob marking the draggable anchor, so the grab target is discoverable. */
/**
 * The point the rope hangs from, drawn as a short horizontal dash rather than
 * a knob — it reads as a rail the charm is hung on, and being wide and flat
 * rather than round it needs far less vertical room, which lets the whole
 * charm sit closer to the top of the screen.
 */
fun DrawScope.drawAnchorDash(x: Float, y: Float, width: Float, height: Float) {
    val corner = CornerRadius(height / 2f)
    drawRoundRect(
        color = RopeColor,
        topLeft = Offset(x - width / 2f, y - height / 2f),
        size = Size(width, height),
        cornerRadius = corner,
    )
    drawRoundRect(
        color = BeadColor.copy(alpha = 0.5f),
        topLeft = Offset(x - width / 2f, y - height / 2f),
        size = Size(width, height),
        cornerRadius = corner,
        style = Stroke(width = height * 0.3f),
    )
}

/**
 * Draws the charm hanging from [x],[y], rotated to follow the rope's end
 * segment (spec §8). All art is authored hanging straight down from its attach
 * point, so the incoming angle is offset by 90 degrees before rotating.
 */
fun DrawScope.drawCharm(
    visual: CharmVisual,
    x: Float,
    y: Float,
    angleRadians: Float,
    artSize: Float,
    textMeasurer: TextMeasurer,
    emojiInk: EmojiInk,
) {
    val degrees = (angleRadians * 180f / PI.toFloat()) - 90f
    rotate(degrees = degrees, pivot = Offset(x, y)) {
        when (visual) {
            is CharmVisual.Art -> {
                // The bundled art carries its own ring, so line that up with the
                // rope end. Every charm is taller than it is wide and is trimmed
                // to its opaque bounds, so artSize sets the height and the width
                // follows from this charm's own ratio — forcing a square here
                // would stretch the art by up to 2:1 on the narrowest charm.
                val height = artSize
                val width = artSize * visual.charm.aspectRatio
                translate(
                    left = x - width * visual.charm.attachXFraction,
                    top = y - height * visual.charm.attachYFraction,
                ) {
                    with(visual.painter) { draw(Size(width, height)) }
                }
            }

            is CharmVisual.Photo -> {
                val image = visual.image
                val scale = min(artSize / image.width, artSize / image.height)
                val width = image.width * scale
                val height = image.height * scale
                translate(left = x - width / 2f, top = y) {
                    drawImage(
                        image = image,
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(width.roundToInt(), height.roundToInt()),
                    )
                }
            }

            is CharmVisual.Emoji -> {
                // A glyph does not fill its line box: there is leading above the
                // ascent, and the emoji's own ink usually starts lower still.
                // Hanging the *box* from the attach point therefore leaves a gap
                // between the rope's end and anything you can see. The rope is
                // meant to touch the charm, so the glyph is raised by however
                // far its first inked row sits below the top of the box.
                val layout = textMeasurer.measure(
                    text = AnnotatedString(visual.character),
                    style = TextStyle(
                        fontSize = artSize.toSp(),
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                    ),
                )
                val inkTop = layout.firstBaseline + emojiInk.topAboveBaseline(
                    visual.character,
                    artSize,
                )
                translate(left = x - layout.size.width / 2f, top = y - inkTop) {
                    drawText(textLayoutResult = layout, topLeft = Offset.Zero)
                }
            }
        }
    }
}
