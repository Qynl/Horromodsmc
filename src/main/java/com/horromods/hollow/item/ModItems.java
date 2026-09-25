package com.horromods.hollow.item;

import com.horromods.hollow.Hollow;
import com.horromods.hollow.entity.ModEntities;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Rarity;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.List;

public final class ModItems {
    public static final Item SHADOW_FRAGMENT = register("shadow_fragment", new Item(new Item.Settings()
            .rarity(Rarity.UNCOMMON)) {
        @Override
        public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
            tooltip.add(Text.translatable("item.hollow.shadow_fragment.tooltip")
                    .formatted(Formatting.GRAY, Formatting.ITALIC));
        }
    });

    /** While anywhere in the player's inventory, The Watcher cannot focus on them. */
    public static final Item WARDING_TOTEM = register("warding_totem", new Item(new Item.Settings()
            .maxCount(1)
            .rarity(Rarity.UNCOMMON)
            .fireproof()) {
        @Override
        public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
            tooltip.add(Text.translatable("item.hollow.warding_totem.tooltip")
                    .formatted(Formatting.GRAY, Formatting.ITALIC));
        }
    });

    /** For testing and brave summoning: spawns The Watcher. */
    public static final Item WATCHER_SPAWN_EGG = new SpawnEggItem(
            ModEntities.WATCHER, 0x0D0A12, 0x8A5CD8, new Item.Settings().maxCount(64));

    private ModItems() {
    }

    public static void register() {
        Registry.register(Registries.ITEM, Identifier.of(Hollow.MOD_ID, "watcher_spawn_egg"),
                WATCHER_SPAWN_EGG);

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS)
                .register(entries -> entries.add(SHADOW_FRAGMENT));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT)
                .register(entries -> entries.add(WARDING_TOTEM));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS)
                .register(entries -> entries.add(WATCHER_SPAWN_EGG));
    }

    private static Item register(String name, Item item) {
        return Registry.register(Registries.ITEM, Identifier.of(Hollow.MOD_ID, name), item);
    }

    public static boolean hasWardingTotem(PlayerEntity player) {
        return player.getInventory().contains(stack -> stack.isOf(WARDING_TOTEM));
    }
}
