package com.horromods.hollow.block;

import com.horromods.hollow.Hollow;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.List;

public final class ModBlocks {
    /** A blessed light that calms Dread and keeps The Watcher at bay. */
    public static final Block HALLOWED_LANTERN = new HallowedLanternBlock(AbstractBlock.Settings.create()
            .strength(1.2f, 6.0f)
            .luminance(state -> 15)
            .sounds(BlockSoundGroup.METAL));

    private ModBlocks() {
    }

    public static void register() {
        Registry.register(Registries.BLOCK, Identifier.of(Hollow.MOD_ID, "hallowed_lantern"), HALLOWED_LANTERN);
        Registry.register(Registries.ITEM, Identifier.of(Hollow.MOD_ID, "hallowed_lantern"),
                new BlockItem(HALLOWED_LANTERN, new Item.Settings()) {
                    @Override
                    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip,
                            TooltipType type) {
                        tooltip.add(Text.translatable("block.hollow.hallowed_lantern.tooltip")
                                .formatted(Formatting.GRAY, Formatting.ITALIC));
                    }
                });

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.BUILDING_BLOCKS)
                .register(entries -> entries.add(HALLOWED_LANTERN));
    }
}
