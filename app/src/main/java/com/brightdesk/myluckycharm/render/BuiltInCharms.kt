package com.brightdesk.myluckycharm.render

import androidx.annotation.DrawableRes
import com.brightdesk.myluckycharm.R

/**
 * Bundled charm art (spec §8), as raster PNGs in `drawable-nodpi`.
 *
 * Unlike the vector charms these replaced, the art is neither square nor drawn
 * with its ring at a shared position, so each charm carries its own geometry
 * instead of the renderer assuming one constant:
 *
 * - [aspectRatio] is width / height of the *trimmed* art. Every source image
 *   was cropped to its opaque bounds before export, so there is no transparent
 *   padding to absorb a wrong ratio — drawing these into a square would visibly
 *   stretch them.
 * - [attachXFraction] / [attachYFraction] locate the centre of the hanging ring
 *   within that trimmed art. They were measured from the alpha channel rather
 *   than eyeballed: scan down from the first opaque row until the content stops
 *   widening, which is the ring's widest row and therefore its centre.
 *
 * `drawable-nodpi` is deliberate — the charm is scaled to the user's size
 * setting at draw time, so letting Android density-scale it first would only
 * resample it twice.
 */
data class BuiltInCharm(
    val id: String,
    val label: String,
    @param:DrawableRes val drawableRes: Int,
    val aspectRatio: Float,
    val attachXFraction: Float,
    val attachYFraction: Float,
)

val BUILT_IN_CHARMS: List<BuiltInCharm> = listOf(
    BuiltInCharm("lucky_cat", "Lucky Cat", R.drawable.charm_lucky_cat, 0.6597f, 0.4980f, 0.0727f),
    BuiltInCharm("horseshoe", "Horseshoe", R.drawable.charm_horseshoe, 0.7412f, 0.5060f, 0.0794f),
    BuiltInCharm("evil_eye", "Evil Eye", R.drawable.charm_evil_eye, 0.8551f, 0.5103f, 0.0919f),
    BuiltInCharm("hamsa", "Hamsa", R.drawable.charm_hamsa, 0.6761f, 0.5021f, 0.0817f),
    BuiltInCharm("scarab", "Scarab", R.drawable.charm_scarab, 0.6821f, 0.5023f, 0.0957f),
    BuiltInCharm("daruma", "Daruma", R.drawable.charm_daruma, 0.7794f, 0.5055f, 0.0745f),
    BuiltInCharm("oni_mask", "Oni Mask", R.drawable.charm_oni_mask, 0.8102f, 0.5056f, 0.0873f),
    BuiltInCharm("temple_bell", "Temple Bell", R.drawable.charm_temple_bell, 0.7980f, 0.5000f, 0.0847f),
    BuiltInCharm("lucky_knot", "Lucky Knot", R.drawable.charm_lucky_knot, 0.4778f, 0.5180f, 0.0764f),
    BuiltInCharm("ash_gourd", "Ash Gourd", R.drawable.charm_ash_gourd, 0.5635f, 0.4877f, 0.0470f),
    BuiltInCharm("chilli_lemon", "Chilli & Lemon", R.drawable.charm_chilli_lemon, 0.6518f, 0.5150f, 0.0836f),
    BuiltInCharm("om_medallion", "Om Medallion", R.drawable.charm_om_medallion, 0.7798f, 0.5020f, 0.1101f),
    BuiltInCharm("om_wood", "Wooden Om", R.drawable.charm_om_wood, 0.7861f, 0.5283f, 0.1083f),
    BuiltInCharm("crescent_star", "Moon & Star", R.drawable.charm_crescent_star, 0.6925f, 0.4479f, 0.1043f),
    BuiltInCharm("786", "786", R.drawable.charm_786, 0.7629f, 0.4880f, 0.1094f),
    BuiltInCharm("cross_wood", "Wooden Cross", R.drawable.charm_cross_wood, 0.5840f, 0.4932f, 0.1013f),
    BuiltInCharm("cross_stone", "Stone Cross", R.drawable.charm_cross_stone, 0.6046f, 0.4858f, 0.0946f),
)

/**
 * Tolerates an id written by an older build that no longer ships that charm —
 * which now includes every id from the four vector charms these replaced, so
 * an existing install lands on the first charm rather than drawing nothing.
 */
fun builtInCharmOrDefault(id: String): BuiltInCharm =
    BUILT_IN_CHARMS.firstOrNull { it.id == id } ?: BUILT_IN_CHARMS.first()
