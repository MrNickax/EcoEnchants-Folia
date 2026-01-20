
package com.willfp.ecoenchants.proxy.v1_21_11

import com.willfp.ecoenchants.enchant.EcoEnchant
import com.willfp.ecoenchants.enchant.EcoEnchants
import com.willfp.ecoenchants.enchant.impl.EcoEnchantBase
import com.willfp.ecoenchants.enchant.registration.ModernEnchantmentRegistererProxy
import com.willfp.ecoenchants.proxy.v1_21_11.registration.EcoEnchantsCraftEnchantment
import com.willfp.ecoenchants.proxy.v1_21_11.registration.vanillaEcoEnchantsEnchantment
import io.papermc.paper.registry.entry.RegistryTypeMapper
import io.papermc.paper.registry.legacy.DelayedRegistry
import net.minecraft.core.Holder
import net.minecraft.core.MappedRegistry
import net.minecraft.core.Registry
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.world.item.enchantment.Enchantment
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.craftbukkit.CraftRegistry
import org.bukkit.craftbukkit.CraftServer
import org.bukkit.craftbukkit.enchantments.CraftEnchantment
import org.bukkit.craftbukkit.util.CraftNamespacedKey
import java.util.*
import java.util.function.BiFunction
import org.bukkit.enchantments.Enchantment as BukkitEnchantment

private val enchantmentRegistry =
    (Bukkit.getServer() as CraftServer).server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT) as MappedRegistry<Enchantment>

@Suppress("DEPRECATION")
private val bukkitRegistry: org.bukkit.Registry<BukkitEnchantment>
    get() =
        (org.bukkit.Registry.ENCHANTMENT as DelayedRegistry<BukkitEnchantment, *>).delegate()

class ModernEnchantmentRegisterer : ModernEnchantmentRegistererProxy {

    private val minecraftToBukkit = CraftRegistry::class.java
        .getDeclaredField("minecraftToBukkit")
        .apply { isAccessible = true }

    private val cache = CraftRegistry::class.java
        .getDeclaredField("cache")
        .apply { isAccessible = true }

    override fun replaceRegistry() {
        val newRegistryMTB =
            BiFunction<NamespacedKey, Enchantment, BukkitEnchantment?> { key, _ ->
                val eco = EcoEnchants.getByID(key.key)
                val isRegistered = enchantmentRegistry.containsKey(CraftNamespacedKey.toMinecraft(key))

                if (eco != null) {
                    eco as BukkitEnchantment
                } else if (isRegistered) {
                    val holder = enchantmentRegistry.get(CraftNamespacedKey.toMinecraft(key)).get()
                    CraftEnchantment(holder)
                } else {
                    null
                }
            }

        // Update bukkit registry
        @Suppress("UNCHECKED_CAST")
        minecraftToBukkit.set(
            bukkitRegistry,
            RegistryTypeMapper(newRegistryMTB as BiFunction<NamespacedKey, Enchantment, BukkitEnchantment>)
        )

        // Clear the enchantment cache
        cache.set(bukkitRegistry, mutableMapOf<NamespacedKey, BukkitEnchantment>())
    }

    override fun freezeRegistry() {
        try {
            enchantmentRegistry.freeze()
        } catch (_: Exception) {
            // Ignore if already frozen
        }
    }

    override fun register(enchant: EcoEnchantBase): BukkitEnchantment {
        // Clear the enchantment cache
        cache.set(bukkitRegistry, mutableMapOf<NamespacedKey, BukkitEnchantment>())

        if (enchantmentRegistry.containsKey(CraftNamespacedKey.toMinecraft(enchant.enchantmentKey))) {
            val nms = enchantmentRegistry[CraftNamespacedKey.toMinecraft(enchant.enchantmentKey)]

            if (nms.isPresent) {
                return EcoEnchantsCraftEnchantment(enchant, nms.get())
            } else {
                throw IllegalStateException("Enchantment ${enchant.id} wasn't registered")
            }
        }

        // MUST unfreeze before creating an intrusive holder
        unfreezeRegistry()

        val vanillaEnchantment = vanillaEcoEnchantsEnchantment(enchant)

        // Create a new Holder for the custom enchantment
        val reference = enchantmentRegistry.createIntrusiveHolder(vanillaEnchantment)

        // Add it into Registry
        Registry.register(
            enchantmentRegistry,
            Identifier.withDefaultNamespace(enchant.id),
            vanillaEnchantment
        )

        // Return wrapped in EcoEnchantsCraftEnchantment
        return EcoEnchantsCraftEnchantment(enchant, reference)
    }

    override fun unregister(enchant: EcoEnchant) {
        /*
        You can't unregister from a minecraft registry, so we simply leave the stale reference there.
        This shouldn't cause many issues in production as the bukkit registry is replaced on each reload.
         */
    }

    private fun unfreezeRegistry() {
        try {
            // Get all fields and find the frozen field (boolean type)
            val fields = enchantmentRegistry.javaClass.declaredFields
            val frozenField = fields.find { field ->
                field.type == Boolean::class.javaPrimitiveType
            }

            if (frozenField != null) {
                frozenField.isAccessible = true
                frozenField.setBoolean(enchantmentRegistry, false)
            }

            // Also clear unregistered intrusive holders
            val unregisteredField = fields.find { field ->
                field.type == Map::class.java && field.name.contains("unregistered", ignoreCase = true)
            }

            if (unregisteredField != null) {
                unregisteredField.isAccessible = true
                unregisteredField.set(enchantmentRegistry, IdentityHashMap<Enchantment, Holder.Reference<Enchantment>>())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}