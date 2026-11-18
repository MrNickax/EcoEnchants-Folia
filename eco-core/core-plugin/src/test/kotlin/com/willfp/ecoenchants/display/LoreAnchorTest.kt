package com.willfp.ecoenchants.display

import kotlin.test.Test
import kotlin.test.assertEquals

class LoreAnchorTest {
    private val pad = "§z"

    @Test
    fun `the block goes under the anchored lines, padded only next to a line with text`() {
        assertEquals(
            listOf("A", pad, "E1", "E2", "", "B"),
            LoreAnchor.place(listOf("A", "", "B"), listOf("E1", "E2"), 1, pad)
        )
    }

    @Test
    fun `an anchor past the end of the lore puts the block last`() {
        assertEquals(listOf("A", pad, "E1"), LoreAnchor.place(listOf("A"), listOf("E1"), 99, pad))
    }

    @Test
    fun `an anchor of zero or below puts the block first`() {
        assertEquals(listOf("E1", pad, "A"), LoreAnchor.place(listOf("A"), listOf("E1"), 0, pad))
        assertEquals(listOf("E1", pad, "A"), LoreAnchor.place(listOf("A"), listOf("E1"), -3, pad))
    }

    @Test
    fun `a neighbour holding only colour codes counts as blank`() {
        assertEquals(
            listOf("A", "§f§r", "E1", "§f", "B"),
            LoreAnchor.place(listOf("A", "§f§r", "§f", "B"), listOf("E1"), 2, pad)
        )
    }

    @Test
    fun `no enchantments leave the lore untouched`() {
        assertEquals(listOf("A", "", "B"), LoreAnchor.place(listOf("A", "", "B"), emptyList(), 1, pad))
    }

    @Test
    fun `an empty lore shows the block alone`() {
        assertEquals(listOf("E1"), LoreAnchor.place(emptyList(), listOf("E1"), 1, pad))
    }
}
