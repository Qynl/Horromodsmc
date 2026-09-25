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
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

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

    /**
     * While anywhere in the player's inventory, The Watcher cannot focus on
     * them. Every time it tries, the totem strains and cracks — it only
     * endures three such wards before crumbling to dust.
     */
    public static final Item WARDING_TOTEM = register("warding_totem", new Item(new Item.Settings()
            .maxCount(1)
            .maxDamage(3)
            .rarity(Rarity.UNCOMMON)
            .fireproof()) {
        @Override
        public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
            tooltip.add(Text.translatable("item.hollow.warding_totem.tooltip")
                    .formatted(Formatting.GRAY, Formatting.ITALIC));
        }

        @Override
        public boolean isItemBarVisible(ItemStack stack) {
            return stack.getDamage() > 0 || super.isItemBarVisible(stack);
        }
    });

    /** While held, reveals your Dread and makes nearby Watchers glow. */
    public static final Item THIRD_EYE = register("third_eye", new Item(new Item.Settings()
            .maxCount(1)
            .rarity(Rarity.RARE)) {
        @Override
        public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
            tooltip.add(Text.translatable("item.hollow.third_eye.tooltip")
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
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS)
                .register(entries -> entries.add(THIRD_EYE));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS)
                .register(entries -> entries.add(WATCHER_SPAWN_EGG));
    }

    private static Item register(String name, Item item) {
        return Registry.register(Registries.ITEM, Identifier.of(Hollow.MOD_ID, name), item);
    }

    public static boolean hasWardingTotem(PlayerEntity player) {
        return player.getInventory().contains(stack -> stack.isOf(WARDING_TOTEM));
    }

    public static boolean holdsThirdEye(PlayerEntity player) {
        return player.getMainHandStack().isOf(THIRD_EYE) || player.getOffHandStack().isOf(THIRD_EYE);
    }

    /**
     * The totem absorbs one attempt by The Watcher to focus on its bearer.
     * It cracks visibly, and on the third ward it shatters forever.
     */
    public static void wardAttempt(PlayerEntity player) {
        World world = player.getWorld();
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isOf(WARDING_TOTEM)) {
                continue;
            }
            int damage = stack.getDamage() + 1;
            if (damage >= stack.getMaxDamage()) {
                stack.decrement(1);
                world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ITEM_BREAK,
                        SoundCategory.PLAYERS, 0.9f, 0.8f);
                player.sendMessage(Text.translatable("item.hollow.warding_totem.broken")
                        .formatted(Formatting.RED), true);
            } else {
                stack.setDamage(damage);
                world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE,
                        SoundCategory.PLAYERS, 0.8f, 1.1f);
                player.sendMessage(Text.translatable("item.hollow.warding_totem.flare")
                        .formatted(Formatting.AQUA, Formatting.ITALIC), true);
            }
            return;
        }
    }
}
