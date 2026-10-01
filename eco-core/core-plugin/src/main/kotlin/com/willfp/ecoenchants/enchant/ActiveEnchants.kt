package com.willfp.ecoenchants.enchant

import com.willfp.eco.core.fast.fast
import com.willfp.ecoenchants.display.getFormattedDescription
import com.willfp.ecoenchants.type.EnchantmentType
import com.willfp.libreforge.forceRefreshHolders
import com.willfp.libreforge.toDispatcher
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.LivingEntity
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

/**
 * How an enchantment of a selectable type renders on an item.
 */
enum class SelectionState {
    /**
     * The item carries no more of the type than may run at once, so there is no choice to show.
     */
    NONE,

    /**
     * One of the enchantments the owner chose to run.
     */
    ACTIVE,

    /**
     * Carried, but switched off: it shows dimmed and never reaches libreforge.
     */
    INACTIVE
}

/**
 * Active-enchantment selection for types that cap how many of their enchantments work at once.
 *
 * A type with `active-limit` lets an item carry more of its enchantments (`limit`) than may run
 * together. The chosen ones are pinned in the item's PDC under `ecoenchants:active_<type>`; the
 * rest stay on the item, render with the type's `inactive-format` and are skipped by
 * [com.willfp.ecoenchants.target.EnchantFinder], so their effects never fire.
 *
 * Resolution is deterministic, so every reader (effects, lore, other plugins through this API)
 * agrees without anything having to be written first: valid pins first, then the remaining
 * enchantments of the type in id order. An item that predates this feature, or whose pinned
 * enchantment was taken off in a grindstone, therefore still resolves without migration.
 *
 * Enchanted books never have an active selection: they only carry enchantments to apply.
 */
object ActiveEnchants {
    private val stringList = PersistentDataType.LIST.strings()

    /**
     * Get if [enchantment] runs on [item].
     *
     * Always true for types without `active-limit` and for books, so callers can gate every
     * enchantment through this without checking the type first.
     */
    @JvmStatic
    fun isActive(item: ItemStack, enchantment: Enchantment): Boolean {
        val type = enchantment.wrap().type

        if (!type.isSelectable || item.type == Material.ENCHANTED_BOOK) {
            return true
        }

        val members = item.fast().enchants.keys.filter { it.wrap().type == type }

        return enchantment in resolve(item, type, members)
    }

    /**
     * Get the enchantments on [item] that belong to a selectable type, sorted by id.
     *
     * Empty for books.
     */
    @JvmStatic
    fun getSelectable(item: ItemStack): List<Enchantment> {
        if (item.type == Material.ENCHANTED_BOOK) {
            return emptyList()
        }

        return item.fast().enchants.keys
            .filter { it.wrap().type.isSelectable }
            .sortedBy { it.key.key }
    }

    /**
     * Get if [item] carries more enchantments of some selectable type than may run at once,
     * that is, if picking the active one means anything.
     */
    @JvmStatic
    fun hasChoice(item: ItemStack): Boolean {
        if (item.type == Material.ENCHANTED_BOOK) {
            return false
        }

        return groupSelectable(item.fast().enchants.keys).any { (type, members) -> members.size > type.activeLimit }
    }

    /**
     * Make [enchantment] one of the active enchantments of its type on [item].
     *
     * The item is edited in place; write it back to its inventory slot if it is not a live
     * mirror. Once [ActiveEnchants.isActive] changes for a held item, call [refresh] on the
     * holder so libreforge stops running the old effects straight away instead of when its
     * caches expire.
     *
     * @return the enchantments this pushed out of the active set, in the order they were pinned;
     * empty if [enchantment] was already active.
     * @throws IllegalArgumentException if [enchantment] is not of a selectable type, the item is
     * a book, or the item does not carry it.
     */
    @JvmStatic
    fun setActive(item: ItemStack, enchantment: Enchantment): List<Enchantment> {
        val type = enchantment.wrap().type

        require(type.isSelectable) { "${enchantment.key} is not of a type with an active-limit" }
        require(item.type != Material.ENCHANTED_BOOK) { "books have no active enchantments" }

        val members = item.fast().enchants.keys.filter { it.wrap().type == type }

        require(enchantment in members) { "item does not carry ${enchantment.key}" }

        val before = resolve(item, type, members)

        if (enchantment in before) {
            return emptyList()
        }

        val after = resolveIds(
            members.map { it.key.key },
            listOf(enchantment.key.key) + before.map { it.key.key },
            type.activeLimit
        )

        item.editMeta { it.persistentDataContainer.set(type.activeKey, stringList, after) }

        return before.filter { it.key.key !in after }
    }

    /**
     * Get the description lines [enchantment] shows in lore at [level], fully formatted, as
     * legacy section-sign strings.
     *
     * Exposed so a selection menu can explain each option exactly as the lore would, without
     * reaching into EcoEnchants' display internals.
     */
    @JvmStatic
    fun describe(enchantment: Enchantment, level: Int): List<String> {
        return enchantment.wrap().getFormattedDescription(level)
    }

    /**
     * Drop [entity]'s cached holders and recompute its effects now.
     *
     * libreforge caches holders for half a second and EcoEnchants' level lookup for a second;
     * without this, switching the active enchantment leaves the old one's effects running and the
     * new one's off until both expire.
     */
    @JvmStatic
    fun refresh(entity: LivingEntity) {
        entity.toDispatcher().forceRefreshHolders()
    }

    /**
     * Get the enchantments among [enchants] that [item] carries but has switched off.
     *
     * [enchants] is the item's own enchantment set, passed in because the effect lookup has
     * already read it. Allocates nothing for items without a selectable type.
     */
    internal fun findInactive(item: ItemStack, enchants: Collection<Enchantment>): Set<Enchantment> {
        if (item.type == Material.ENCHANTED_BOOK) {
            return emptySet()
        }

        var inactive: MutableSet<Enchantment>? = null

        for ((type, members) in groupSelectable(enchants)) {
            if (members.size <= type.activeLimit) {
                continue
            }

            val active = resolve(item, type, members)

            for (member in members) {
                if (member !in active) {
                    if (inactive == null) {
                        inactive = mutableSetOf()
                    }

                    inactive += member
                }
            }
        }

        return inactive ?: emptySet()
    }

    /**
     * Get how each enchantment of a selectable type among [enchants] should render on [item].
     *
     * Enchantments missing from the result render as [SelectionState.NONE]: they are of a normal
     * type, or the item does not carry enough of their type for a choice to exist, which keeps an
     * item with a single limited enchantment looking exactly as it did before.
     */
    internal fun displayStates(item: ItemStack, enchants: Collection<Enchantment>): Map<Enchantment, SelectionState> {
        if (item.type == Material.ENCHANTED_BOOK) {
            return emptyMap()
        }

        val states = mutableMapOf<Enchantment, SelectionState>()

        for ((type, members) in groupSelectable(enchants)) {
            if (members.size <= type.activeLimit) {
                continue
            }

            val active = resolve(item, type, members)

            for (member in members) {
                states[member] = if (member in active) SelectionState.ACTIVE else SelectionState.INACTIVE
            }
        }

        return states
    }

    /**
     * Capture the resolved active ids of every selectable type on [item].
     *
     * Paired with [pin] around an anvil merge, so the merged item keeps the selection of the item
     * being worked on rather than letting a sacrifice's enchantments reshuffle the id order.
     */
    internal fun snapshot(item: ItemStack): Map<EnchantmentType, List<String>> {
        if (item.type == Material.ENCHANTED_BOOK) {
            return emptyMap()
        }

        return groupSelectable(item.fast().enchants.keys)
            .mapValues { (type, members) -> resolve(item, type, members).map { it.key.key } }
    }

    /**
     * Write the selection of every selectable type on [item] that now has a choice, starting from
     * [previous] (see [snapshot]).
     *
     * Pinning explicitly means adding an enchantment never changes which one is active, however
     * the ids sort.
     */
    internal fun pin(item: ItemStack, previous: Map<EnchantmentType, List<String>>) {
        if (item.type == Material.ENCHANTED_BOOK) {
            return
        }

        val choices = groupSelectable(item.fast().enchants.keys)
            .filter { (type, members) -> members.size > type.activeLimit }

        if (choices.isEmpty()) {
            return
        }

        item.editMeta { meta ->
            for ((type, members) in choices) {
                val ids = resolveIds(members.map { it.key.key }, previous[type] ?: emptyList(), type.activeLimit)
                meta.persistentDataContainer.set(type.activeKey, stringList, ids)
            }
        }
    }

    /**
     * Pick the active ids of one type: valid [pinned] ids first, in pin order, then the rest of
     * [present] in id order, up to [limit].
     *
     * Pure on purpose, so the rule every reader relies on lives in one place.
     */
    internal fun resolveIds(present: Collection<String>, pinned: List<String>, limit: Int): List<String> {
        if (present.size <= limit) {
            return present.sorted()
        }

        val active = pinned.filter { it in present }.distinct().take(limit).toMutableList()

        for (id in present.sorted()) {
            if (active.size >= limit) {
                break
            }

            if (id !in active) {
                active += id
            }
        }

        return active
    }

    /**
     * Resolve the active enchantments of [type] among [members], the item's enchantments of that
     * type, in pin order.
     */
    private fun resolve(item: ItemStack, type: EnchantmentType, members: List<Enchantment>): List<Enchantment> {
        val pinned = item.persistentDataContainer.get(type.activeKey, stringList) ?: emptyList()
        val ids = resolveIds(members.map { it.key.key }, pinned, type.activeLimit)

        return ids.mapNotNull { id -> members.firstOrNull { it.key.key == id } }
    }

    /**
     * Group [enchants] by selectable type, skipping every other type. Allocates nothing when none
     * is selectable, which is the case for nearly every item.
     */
    private fun groupSelectable(enchants: Collection<Enchantment>): Map<EnchantmentType, List<Enchantment>> {
        var grouped: MutableMap<EnchantmentType, MutableList<Enchantment>>? = null

        for (enchant in enchants) {
            val type = enchant.wrap().type

            if (!type.isSelectable) {
                continue
            }

            if (grouped == null) {
                grouped = mutableMapOf()
            }

            grouped.getOrPut(type) { mutableListOf() } += enchant
        }

        return grouped ?: emptyMap()
    }
}
