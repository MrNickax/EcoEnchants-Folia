package com.willfp.ecoenchants.display

/**
 * Lets any plugin choose where in an item's lore the enchantments are drawn.
 *
 * An item that carries the integer persistent-data key `ecoenchants:lore_anchor` gets its
 * enchantment lines drawn after the first `N` lines of its own lore, instead of above or below the
 * whole lore (`display.enchantments-below-lore`). Plugins need no dependency on EcoEnchants to use it:
 *
 * ```java
 * meta.getPersistentDataContainer().set(
 *     NamespacedKey.fromString("ecoenchants:lore_anchor"), PersistentDataType.INTEGER, 1);
 * ```
 *
 * The anchor is clamped to the lore, so a value past its end draws the enchantments last and zero
 * or below draws them first.
 */
object LoreAnchor {
    /** The key under EcoEnchants' namespace: `ecoenchants:lore_anchor`. */
    const val KEY = "lore_anchor"

    private val colourCode = Regex("§.")

    /**
     * Splices the enchantment lines into the lore after its first [anchor] lines.
     *
     * A [separator] line keeps the block apart from a neighbouring line that has text in it; a
     * neighbour that is already blank, colour codes aside, is left to do that on its own.
     *
     * @param lore the item's own lore
     * @param enchantLore the enchantment lines; when empty, [lore] is returned as it is
     * @param anchor how many of the item's lines go above the enchantments
     * @param separator the blank line to pad with; the display prefix, so it is stripped on revert
     * @return the lore with the enchantments in it
     */
    fun place(lore: List<String>, enchantLore: List<String>, anchor: Int, separator: String): List<String> {
        if (enchantLore.isEmpty()) {
            return lore
        }

        val at = anchor.coerceIn(0, lore.size)
        val above = lore.subList(0, at)
        val below = lore.subList(at, lore.size)

        val placed = ArrayList<String>(lore.size + enchantLore.size + 2)
        placed.addAll(above)
        if (above.isNotEmpty() && !above.last().isBlankLine()) {
            placed.add(separator)
        }
        placed.addAll(enchantLore)
        if (below.isNotEmpty() && !below.first().isBlankLine()) {
            placed.add(separator)
        }
        placed.addAll(below)
        return placed
    }

    private fun String.isBlankLine(): Boolean = colourCode.replace(this, "").isBlank()
}
