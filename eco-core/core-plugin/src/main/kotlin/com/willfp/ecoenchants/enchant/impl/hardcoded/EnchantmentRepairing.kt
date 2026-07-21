package com.willfp.ecoenchants.enchant.impl.hardcoded

import com.willfp.eco.util.DurabilityUtils
import com.willfp.ecoenchants.enchant.impl.HardcodedEcoEnchant
import com.willfp.ecoenchants.target.EnchantFinder.getItemsWithEnchantActive
import com.willfp.ecoenchants.target.EnchantFinder.hasEnchantActive
import com.willfp.libreforge.slot.impl.SlotTypeArmor
import com.willfp.libreforge.slot.impl.SlotTypeHands
import org.bukkit.Bukkit
import org.bukkit.entity.Player

object EnchantmentRepairing : HardcodedEcoEnchant(
    "repairing"
) {
    override fun onRegister() {
        val frequency = config.getInt("frequency").toLong()

        plugin.scheduler.runTimer(frequency, frequency) {
            // Folia: repairing reads and mutates each player's items, so hop to each
            // player's own region scheduler rather than touching them from the global thread.
            for (player in Bukkit.getOnlinePlayers()) {
                player.scheduler.run(plugin, { handleRepairing(player) }, null)
            }
        }
    }

    private fun handleRepairing(player: Player) {
        if (!player.hasEnchantActive(this)) {
            return
        }

        val notWhileHolding = config.getBool("not-while-holding")
        val repairPerLevel = config.getIntFromExpression("repair-per-level", player)

        for ((item, level) in player.getItemsWithEnchantActive(this)) {
            if (notWhileHolding) {
                val isHolding = item in SlotTypeHands.getItems(player)
                val isEquipped = item in SlotTypeArmor.getItems(player)

                if (isHolding || isEquipped) {
                    continue
                }
            }

            DurabilityUtils.repairItem(item, level * repairPerLevel)
        }
    }
}
