package com.github.trivialloop.scorehub.utils

/**
 * Shared icon catalogue. Rules:
 *  - one entry per EMOJI, named after the emoji itself (DEER, DROPLET, HOUSE…), never after a game concept;
 *  - an icon + background that works in one game can be reused in any other game;
 *  - a game that needs another background overrides it locally with `.copy(color = …)`.
 */
object GameSymbols {

    const val TOTAL = "Σ"      // final total of one-shot games (round-based games keep the written "Total")
    const val SUBTOTAL = "="   // calculated subtotal row

    const val LABEL_COL_DP_ROUNDS = 65
    const val LABEL_COL_DP_ONE_SHOT = 56

    data class RowStyle(val emoji: String, val color: Int, val dialogEmoji: String = emoji)

    private fun s(emoji: String, color: Long) = RowStyle(emoji, color.toInt())

    // ── Nature ────────────────────────────────────────────────────────────────
    val DECIDUOUS_TREE = s("🌳", 0xFF1B5E20)
    val EVERGREEN_TREE = s("🌲", 0xFF1B5E20)
    val PALM_TREE      = s("🌴", 0xFF1B5E20)
    val MOUNTAIN       = s("⛰️", 0xFF757575)
    val SHEAF_OF_RICE  = s("🌾", 0xFFF9A825)
    val DROPLET        = s("💧", 0xFF0288D1)
    val WATER_WAVE     = s("🌊", 0xFF0277BD)
    val BLOSSOM        = s("🌸", 0xFFC2185B)

    // ── Animals / food ────────────────────────────────────────────────────────
    val DEER    = s("🦌", 0xFFEF6C00)
    val PEACOCK = s("🦚", 0xFFEF6C00)
    val EGG     = s("🥚", 0xFF8D6E63)
    val BUG     = s("🐛", 0xFFEF6C00)

    // ── People / buildings ────────────────────────────────────────────────────
    val PERSON         = s("🧑", 0xFF8D6E63)
    val HOUSE          = s("🏠", 0xFFC62828)
    val CROWN          = s("👑", 0xFF6A1B9A)
    val TOKYO_TOWER    = s("🗼", 0xFF6D4C41)

    // ── Objects ───────────────────────────────────────────────────────────────
    val CROSSED_SWORDS  = s("⚔️", 0xFFC62828)
    val COIN            = s("🪙", 0xFFC9A227)
    val BALANCE_SCALE   = s("⚖️", 0xFFFBC02D)
    val ALEMBIC         = s("⚗️", 0xFF6A1B9A)
    val MICROSCOPE      = s("🔬", 0xFF2E7D32)
    val COMPASS         = s("🧭", 0xFF1565C0)
    val GEAR            = s("⚙️", 0xFF757575)
    val SCROLL          = s("📜", 0xFF2E7D32)
    val TICKET          = s("🎫", 0xFF00796B)
    val TRAIN           = s("🚃", 0xFF455A64)
    val FLOWER_CARDS    = s("🎴", 0xFF6A1B9A)
    val INBOX_TRAY      = s("📥", 0xFF00796B)
    val CHEQUERED_FLAG  = s("🏁", 0xFF455A64)
    val TROPHY          = s("🏆", 0xFFC9A227)

    // ── Akropolis (emoji only, colored blocks stay local to the game) ─────────
    val STAR   = s("⭐", 0xFF455A64)
    val HOUSES = s("🏘️", 0xFF455A64)
    val ROCK   = s("🪨", 0xFF757575)

    // Dark grey used for the "birds subtotal" style rows
    val SUBTOTAL_ACCENT = 0xFF37474F.toInt()
}
