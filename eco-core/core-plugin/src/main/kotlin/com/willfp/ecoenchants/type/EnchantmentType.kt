package com.willfp.ecoenchants.type

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.registry.Registrable
import com.willfp.ecoenchants.enchant.infiniteIfNegative
import com.willfp.ecoenchants.libreforge.TriggerEnchantType
import com.willfp.ecoenchants.plugin
import com.willfp.libreforge.triggers.Triggers
import java.util.Objects

class EnchantmentType(
    internal val config: Config
) : Registrable {
    val id = config.getString("id")
    val format = config.getString("format")
    val limit = config.getInt("limit").infiniteIfNegative()
    val highLevelBias = config.getDouble("high-level-bias").coerceAtMost(0.999)
    val noGrindstone = config.getBool("no-grindstone")

    /**
     * How many enchantments of this type run at once on one item; the rest are carried but off.
     * Unset or below 1 means all of them run, as before active selection existed.
     */
    val activeLimit = config.getInt("active-limit").infiniteIfNegative()

    /**
     * Format of an active enchantment of this type, once the item carries more than [activeLimit].
     */
    val activeFormat: String = config.getStringOrNull("active-format") ?: format

    /**
     * Format of an inactive enchantment of this type.
     */
    val inactiveFormat: String = config.getStringOrNull("inactive-format") ?: format

    /**
     * If the owner of an item picks which enchantments of this type run (see
     * [com.willfp.ecoenchants.enchant.ActiveEnchants]).
     */
    val isSelectable = activeLimit != Int.MAX_VALUE

    /**
     * Item PDC key holding the ids of the active enchantments of this type.
     */
    internal val activeKey = plugin.namespacedKeyFactory.create("active_$id")

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }

        if (other !is EnchantmentType) {
            return false
        }

        return other.id == this.id
    }

    override fun onRegister() {
        Triggers.register(TriggerEnchantType(this))
    }

    override fun getID(): String {
        return this.id
    }

    override fun hashCode(): Int {
        return Objects.hash(id)
    }

    override fun toString(): String {
        return "EnchantmentType{$id}"
    }
}