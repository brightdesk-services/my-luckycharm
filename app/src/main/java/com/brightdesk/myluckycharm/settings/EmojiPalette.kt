package com.brightdesk.myluckycharm.settings

/** One palette entry: the character that hangs on the rope, and what to call it. */
data class CharmEmoji(val character: String, val label: String)

/**
 * A curated set is enough here (spec §8) — pulling in a full emoji library for
 * a decorative charm would be a lot of weight for no benefit. Each carries a
 * short label because the picker shows names under every tile, and the one
 * word also answers "which moon is that" at a glance.
 */
val CHARM_EMOJI: List<CharmEmoji> = listOf(
    CharmEmoji("🪶", "Feather"),
    CharmEmoji("🌙", "Moon"),
    CharmEmoji("⭐", "Star"),
    CharmEmoji("✨", "Sparkles"),
    CharmEmoji("💫", "Dizzy"),
    CharmEmoji("🔮", "Crystal ball"),
    CharmEmoji("🧿", "Nazar"),
    CharmEmoji("🍀", "Clover"),
    CharmEmoji("🌸", "Blossom"),
    CharmEmoji("🌺", "Hibiscus"),
    CharmEmoji("🌿", "Herb"),
    CharmEmoji("🍂", "Fallen leaf"),
    CharmEmoji("🍁", "Maple"),
    CharmEmoji("🌻", "Sunflower"),
    CharmEmoji("🪷", "Lotus"),
    CharmEmoji("🥀", "Wilted rose"),
    CharmEmoji("🐚", "Shell"),
    CharmEmoji("🦋", "Butterfly"),
    CharmEmoji("🐝", "Bee"),
    CharmEmoji("🕊️", "Dove"),
    CharmEmoji("🐉", "Dragon"),
    CharmEmoji("🪸", "Coral"),
    CharmEmoji("🌊", "Wave"),
    CharmEmoji("💧", "Droplet"),
    CharmEmoji("❄️", "Snowflake"),
    CharmEmoji("🔥", "Fire"),
    CharmEmoji("🌈", "Rainbow"),
    CharmEmoji("☀️", "Sun"),
    CharmEmoji("⚡", "Bolt"),
    CharmEmoji("🎐", "Wind chime"),
    CharmEmoji("🔔", "Bell"),
    CharmEmoji("🗝️", "Key"),
    CharmEmoji("💎", "Gem"),
    CharmEmoji("👑", "Crown"),
    CharmEmoji("🏹", "Bow"),
    CharmEmoji("⚓", "Anchor"),
    CharmEmoji("🪬", "Hamsa"),
    CharmEmoji("🪄", "Wand"),
    CharmEmoji("🎈", "Balloon"),
    CharmEmoji("🪁", "Kite"),
)

/**
 * Just the characters. Anything outside this set can only have come from the
 * keyboard, which is how the picker tells a curated pick from a typed one.
 */
val CHARM_EMOJI_CHARS: Set<String> = CHARM_EMOJI.mapTo(LinkedHashSet()) { it.character }
