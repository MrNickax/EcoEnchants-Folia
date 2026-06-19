package com.willfp.ecoenchants.mechanics

import com.willfp.eco.core.drops.DropQueue
import com.willfp.eco.core.fast.fast
import com.willfp.ecoenchants.enchant.EcoEnchants
import com.willfp.ecoenchants.enchant.wrap
import com.willfp.ecoenchants.plugin
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.AnvilInventory
import org.bukkit.inventory.ItemStack
import org.geysermc.cumulus.form.ModalForm
import org.geysermc.floodgate.api.FloodgateApi
import java.util.UUID
import java.util.function.Consumer

/**
 * Bedrock-only anvil workaround.
 *
 * Bedrock (Geyser) clients render the anvil output client-side and ignore the server-authoritative
 * result, so custom EcoEnchants applied via the anvil never appear for them. We also can't show a
 * Floodgate form over the open anvil (it just queues) nor force the client anvil closed from the
 * server (the Bedrock anvil UI is client-authoritative).
 *
 * So: when a Bedrock player closes an anvil that holds a target item + an enchanted book with an
 * applicable custom enchantment, we take the inputs into custody and show a native confirmation form.
 * On confirm the enchantment is applied (free, one book consumed) and the item handed back via
 * telekinesis; on cancel/dismiss the items are returned untouched.
 */
object BedrockBookApplySupport : Listener {
    // Ticks to wait after the anvil closed before sending the form, so the close settles on the client.
    private const val FORM_DELAY_TICKS = 3L

    private val colorCodes = Regex("[§&][0-9A-Fa-fK-Ok-oRr]")

    // Items taken into custody while a confirmation form is open, keyed by player.
    private class Pending(
        val target: ItemStack,
        val book: ItemStack,
        val enchants: Map<Enchantment, Int>
    )

    private val pending = mutableMapOf<UUID, Pending>()

    private val floodgatePresent: Boolean by lazy {
        Bukkit.getPluginManager().getPlugin("floodgate") != null
    }

    private fun isBedrock(player: Player): Boolean {
        if (!floodgatePresent) {
            return false
        }

        return try {
            FloodgateApi.getInstance().isFloodgatePlayer(player.uniqueId)
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * The custom (EcoEnchants) enchantments on [book] that [target] can receive and doesn't already have.
     */
    private fun applicableEnchants(target: ItemStack, book: ItemStack): Map<Enchantment, Int> {
        if (book.type != Material.ENCHANTED_BOOK) {
            return emptyMap()
        }

        if (target.type == Material.AIR || target.type == Material.ENCHANTED_BOOK || target.type == Material.BOOK) {
            return emptyMap()
        }

        val existing = target.fast().getEnchants(true).keys
        return book.fast().getEnchants(true).filter { (enchant, _) ->
            EcoEnchants.getByID(enchant.key.key) != null &&     // only custom EcoEnchants
                    enchant !in existing &&                     // not already on the item
                    enchant.wrap().canEnchantItem(target)       // valid target, conflicts, limits
        }
    }

    @EventHandler
    fun onAnvilClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        val anvil = event.inventory as? AnvilInventory ?: return

        if (!isBedrock(player) || pending.containsKey(player.uniqueId)) {
            return
        }

        val item = anvil.getItem(0) ?: return
        val book = anvil.getItem(1) ?: return

        val applicable = applicableEnchants(item, book)
        if (applicable.isEmpty()) {
            return
        }

        val target = item.clone()
        val bookClone = book.clone()

        // Take the inputs into custody so vanilla doesn't also return them to the player (avoid dupe).
        anvil.setItem(0, null)
        anvil.setItem(1, null)

        pending[player.uniqueId] = Pending(target, bookClone, applicable)

        // Send the form a few ticks after the close settles on the client.
        player.scheduler.runDelayed(
            plugin,
            {
                if (pending.containsKey(player.uniqueId)) {
                    sendConfirmation(player, applicable)
                }
            },
            {},
            FORM_DELAY_TICKS
        )
    }

    private fun confirm(player: Player) {
        player.scheduler.run {
            val p = pending.remove(player.uniqueId) ?: return@run

            val existing = p.target.fast().getEnchants(true).keys
            val valid = p.enchants.filter { (enchant, _) ->
                enchant !in existing && enchant.wrap().canEnchantItem(p.target)
            }

            if (valid.isEmpty()) {
                // Shouldn't happen, but never eat the items.
                give(player, p.target, p.book)
                player.sendMessage("§cNo se pudo aplicar; se te han devuelto los objetos.")
                return@run
            }

            val meta = p.target.itemMeta
            if (meta == null) {
                give(player, p.target, p.book)
                return@run
            }
            for ((enchant, level) in valid) {
                meta.addEnchant(enchant, level, true)
            }
            p.target.itemMeta = meta

            // One book is consumed; hand back the enchanted item plus any leftover books in the stack.
            give(player, p.target)
            if (p.book.amount > 1) {
                give(player, p.book.clone().apply { amount = p.book.amount - 1 })
            }

            player.sendMessage("§a✔ Encantamiento aplicado.")
        }
    }

    private fun returnItems(player: Player) {
        player.scheduler.run {
            val p = pending.remove(player.uniqueId) ?: return@run
            give(player, p.target, p.book)
        }
    }

    private fun give(player: Player, vararg items: ItemStack?) {
        val list = items.filterNotNull().filter { it.type != Material.AIR && it.amount > 0 }
        if (list.isEmpty()) {
            return
        }

        DropQueue(player)
            .addItems(list)
            .forceTelekinesis()
            .push()
    }

    private fun sendConfirmation(player: Player, applicable: Map<Enchantment, Int>) {
        val content = buildString {
            append("§7¿Aplicar los siguientes encantamientos a tu objeto?\n\n")
            for ((enchant, level) in applicable) {
                val name = colorCodes.replace(enchant.wrap().rawDisplayName, "").trim()
                append("§f• $name §7(nivel $level)\n")
            }
            append("\n§8Se consumirá el libro.")
        }

        val form = ModalForm.builder()
            .title("§5Aplicar Encantamiento")
            .content(content)
            .button1("§aConfirmar")
            .button2("§cCancelar")
            .validResultHandler(Consumer { response ->
                if (response.clickedButtonId() == 0) {
                    confirm(player)
                } else {
                    returnItems(player)
                }
            })
            // If the player dismisses the form, hand the items back so nothing is lost.
            .closedOrInvalidResultHandler(Runnable { returnItems(player) })
            .build()

        FloodgateApi.getInstance().sendForm(player.uniqueId, form)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        // Never lose items held in custody: put them back in the inventory (saved on quit), dropping
        // only what genuinely doesn't fit.
        val p = pending.remove(event.player.uniqueId) ?: return
        val player = event.player
        val leftover = player.inventory.addItem(p.target, p.book)
        if (leftover.isNotEmpty()) {
            val location = player.location
            location.world?.let { world ->
                for (item in leftover.values) {
                    world.dropItemNaturally(location, item)
                }
            }
        }
    }
}
