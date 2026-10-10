package com.willfp.ecoenchants.enchant.impl.hardcoded

import com.willfp.ecoenchants.enchant.EcoEnchant
import com.willfp.ecoenchants.enchant.impl.HardcodedEcoEnchant
import com.willfp.ecoenchants.target.EnchantFinder.hasEnchantActive
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.block.data.Ageable
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack

object EnchantmentReplenish : HardcodedEcoEnchant(
    "replenish"
) {
    private var handler = ReplenishHandler(this)

    override fun onRegister() {
        plugin.eventManager.registerListener(handler)
    }

    override fun onRemove() {
        plugin.eventManager.unregisterListener(handler)
    }

    private class ReplenishHandler(
        private val enchant: EcoEnchant
    ) : Listener {
        // MONITOR so the replant is only scheduled once the break is final. At NORMAL, a later
        // listener (region protection, BlockRegen,...) could still cancel the break, and the
        // replant would reset the crop that was never harvested back to age 0.
        @EventHandler(
            priority = EventPriority.MONITOR,
            ignoreCancelled = true
        )
        fun handle(event: BlockBreakEvent) {
            val player = event.player

            if (!player.hasEnchantActive(enchant)) {
                return
            }

            val block = event.block
            val type = block.type

            if (type in arrayOf(
                    Material.GLOW_BERRIES,
                    Material.SWEET_BERRY_BUSH,
                    Material.CACTUS,
                    Material.BAMBOO,
                    Material.CHORUS_FLOWER,
                    Material.SUGAR_CANE
                )
            ) {
                return
            }

            val data = block.blockData

            if (data !is Ageable) {
                return
            }

            // Replenish only replants fully-grown crops. Breaking an immature crop is a plain,
            // normal break (drops and all): the upstream behaviour of cancelling drops and
            // resetting the crop to age 0 effectively made unripe crops unbreakable, which is
            // not wanted here. Checked before the seed is taken, so an unripe break costs none.
            if (data.age != data.maximumAge) {
                return
            }

            val seed = if (enchant.config.getBool("consume-seeds")) {
                val item = when (type) {
                    Material.WHEAT -> Material.WHEAT_SEEDS
                    Material.POTATOES -> Material.POTATO
                    Material.CARROTS -> Material.CARROT
                    Material.BEETROOTS -> Material.BEETROOT_SEEDS
                    Material.COCOA -> Material.COCOA_BEANS
                    else -> type
                }

                val hasSeeds = player.inventory.removeItem(ItemStack(item)).isEmpty()

                if (!hasSeeds) {
                    return
                }

                item
            } else {
                null
            }

            data.age = 0

            // The break lands after this event returns, so the replant must run on the next
            // tick — and on Folia/Canvas it must run on the region that owns the block. The
            // previous plugin.scheduler.run goes through the GLOBAL region scheduler, and any
            // block write from there fails the tick-thread check ("Cannot read world
            // asynchronously"), so the replant never happened at all.
            //
            // The player can hop regions within that tick, so their main-hand item is captured
            // here, on the event's (correct) thread, instead of being read inside the task.
            val handItem = player.inventory.itemInMainHand

            Bukkit.getRegionScheduler().run(plugin, block.location) { _ ->
                // Something else already replanted the spot within this tick — EcoPlus' Second
                // Harvest does, at three quarters grown, and on Paper its Bukkit task runs before
                // this region task. Writing the crop again would wipe that growth back to age 0
                // and charge a second seed, so leave it standing and give this seed back.
                if (!block.type.isAir) {
                    if (seed != null) {
                        refundSeed(player, seed)
                    }
                    return@run
                }

                block.type = type
                block.blockData = data

                // Improves compatibility with other plugins.
                @Suppress("UnstableApiUsage")
                Bukkit.getPluginManager().callEvent(
                    BlockPlaceEvent(
                        block,
                        block.state,
                        block.getRelative(BlockFace.DOWN),
                        handItem,
                        player,
                        true,
                        EquipmentSlot.HAND
                    )
                )
            }
        }

        // Runs on the player's own scheduler: the replant task belongs to the block's region,
        // and on Folia the player's inventory must only be touched from the player's.
        private fun refundSeed(player: Player, seed: Material) {
            player.scheduler.run(plugin, { _ ->
                player.inventory.addItem(ItemStack(seed)).values.forEach {
                    player.world.dropItemNaturally(player.location, it)
                }
            }, null)
        }
    }
}
