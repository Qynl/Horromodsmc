package dev.qynl.backrooms.registry;

import dev.qynl.backrooms.BackroomsMod;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModItems {

    public static final Item WALLPAPER = register("wallpaper", new BlockItem(ModBlocks.WALLPAPER, new Item.Settings()));
    public static final Item CARPET = register("carpet", new BlockItem(ModBlocks.CARPET, new Item.Settings()));
    public static final Item CEILING_TILE = register("ceiling_tile", new BlockItem(ModBlocks.CEILING_TILE, new Item.Settings()));
    public static final Item FLUORESCENT_LIGHT = register("fluorescent_light", new BlockItem(ModBlocks.FLUORESCENT_LIGHT, new Item.Settings()));

    private static Item register(String name, Item item) {
        return Registry.register(Registries.ITEM, Identifier.of(BackroomsMod.MOD_ID, name), item);
    }

    private ModItems() {
    }
}
