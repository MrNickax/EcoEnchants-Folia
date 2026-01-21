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
import org.bukkit.enchantments.Enchantment as BukkitEnchantment
import java.lang.reflect.Modifier
import java.util.IdentityHashMap
import java.util.function.BiFunction

private val enchantmentRegistry =
    (Bukkit.getServer() as CraftServer)
        .server
        .registryAccess()
        .lookupOrThrow(Registries.ENCHANTMENT) as MappedRegistry<Enchantment>

@Suppress("DEPRECATION")
private val bukkitRegistry: org.bukkit.Registry<BukkitEnchantment>
    get() =
        (org.bukkit.Registry.ENCHANTMENT as DelayedRegistry<BukkitEnchantment, *>).delegate()

class ModernEnchantmentRegisterer : ModernEnchantmentRegistererProxy {

    /* ==============================
       Bukkit registry reflection
       ============================== */

    private val minecraftToBukkit = CraftRegistry::class.java
        .getDeclaredField("minecraftToBukkit")
        .apply { isAccessible = true }

    private val cache = CraftRegistry::class.java
        .getDeclaredField("cache")
        .apply { isAccessible = true }

    /* ==============================
       MappedRegistry reflection
       ============================== */

    private val frozenField = MappedRegistry::class.java
        .declaredFields.first { it.type == Boolean::class.javaPrimitiveType }
        .apply { isAccessible = true }

    private val allTagsField = MappedRegistry::class.java
        .declaredFields.first { it.type.name.contains("TagSet") }
        .apply { isAccessible = true }

    private val frozenTagsField = MappedRegistry::class.java
        .declaredFields.first {
            Map::class.java.isAssignableFrom(it.type) &&
                    it.name.contains("frozen", ignoreCase = true)
        }
        .apply { isAccessible = true }

    private val unregisteredIntrusiveHoldersField = MappedRegistry::class.java
        .declaredFields.first {
            Map::class.java.isAssignableFrom(it.type) &&
                    it.name.contains("unregistered", ignoreCase = true)
        }
        .apply { isAccessible = true }

    /* ==============================
       Registry replacement
       ============================== */

    override fun replaceRegistry() {
        val newRegistryMTB =
            BiFunction<NamespacedKey, Enchantment, BukkitEnchantment?> { key, _ ->
                val eco = EcoEnchants.getByID(key.key)
                val isRegistered = enchantmentRegistry.containsKey(
                    CraftNamespacedKey.toMinecraft(key)
                )

                when {
                    eco != null -> eco as BukkitEnchantment
                    isRegistered -> {
                        val holder = enchantmentRegistry
                            .get(CraftNamespacedKey.toMinecraft(key))
                            .get()
                        CraftEnchantment(holder)
                    }
                    else -> null
                }
            }

        @Suppress("UNCHECKED_CAST")
        minecraftToBukkit.set(
            bukkitRegistry,
            RegistryTypeMapper(
                newRegistryMTB as BiFunction<NamespacedKey, Enchantment, BukkitEnchantment>
            )
        )

        cache.set(
            bukkitRegistry,
            mutableMapOf<NamespacedKey, BukkitEnchantment>()
        )
    }

    /* ==============================
       Freeze / Unfreeze (EE-style)
       ============================== */

    private fun unfreezeRegistry() {
        frozenField.setBoolean(enchantmentRegistry, false)

        unregisteredIntrusiveHoldersField.set(
            enchantmentRegistry,
            IdentityHashMap<Enchantment, Holder.Reference<Enchantment>>()
        )
    }

    override fun freezeRegistry() {
        val originalTagSet = allTagsField.get(enchantmentRegistry)
        val frozenTags =
            frozenTagsField.get(enchantmentRegistry) as MutableMap<Any?, Any?>

        // Extraer el mapa interno del TagSet original
        val tagMapField = originalTagSet.javaClass.declaredFields
            .first { Map::class.java.isAssignableFrom(it.type) }
            .apply { isAccessible = true }

        val tagsMap = HashMap(tagMapField.get(originalTagSet) as Map<Any?, Any?>)

        // Asegurar consistencia
        tagsMap.forEach { (k, v) ->
            frozenTags.putIfAbsent(k, v)
        }

        // Unbound (temporal)
        unboundTags()

        // Freeze vanilla
        enchantmentRegistry.freeze()

        // Restaurar tags custom
        frozenTags.forEach { (k, v) ->
            tagsMap.putIfAbsent(k, v)
        }

        // Restaurar TagSet original
        tagMapField.set(originalTagSet, tagsMap)
        allTagsField.set(enchantmentRegistry, originalTagSet)
    }

    private fun unboundTags() {
        val tagSetClass = MappedRegistry::class.java
            .declaredClasses
            .first { it.simpleName.contains("TagSet") }

        val factoryMethod = tagSetClass.declaredMethods
            .first {
                Modifier.isStatic(it.modifiers) &&
                        it.parameterCount == 0
            }
            .apply { isAccessible = true }

        val unboundTagSet = factoryMethod.invoke(null)

        allTagsField.set(enchantmentRegistry, unboundTagSet)
    }



    /* ==============================
       Registration
       ============================== */

    override fun register(enchant: EcoEnchantBase): BukkitEnchantment {
        cache.set(
            bukkitRegistry,
            mutableMapOf<NamespacedKey, BukkitEnchantment>()
        )

        val key = CraftNamespacedKey.toMinecraft(enchant.enchantmentKey)

        if (enchantmentRegistry.containsKey(key)) {
            val holder = enchantmentRegistry[key].orElseThrow()
            return EcoEnchantsCraftEnchantment(enchant, holder)
        }

        unfreezeRegistry()

        val vanilla = vanillaEcoEnchantsEnchantment(enchant)
        val reference = enchantmentRegistry.createIntrusiveHolder(vanilla)

        Registry.register(
            enchantmentRegistry,
            Identifier.withDefaultNamespace(enchant.id),
            vanilla
        )

        return EcoEnchantsCraftEnchantment(enchant, reference)
    }

    override fun unregister(enchant: EcoEnchant) {
        // Igual que EcoEnchants original: no-op
    }
}
