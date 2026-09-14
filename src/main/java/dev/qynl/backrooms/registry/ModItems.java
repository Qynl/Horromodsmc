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

    public static final Item OFFICE_CHAIR = register("office_chair", new BlockItem(ModBlocks.OFFICE_CHAIR, new Item.Settings()));
    public static final Item DESK = register("desk", new BlockItem(ModBlocks.DESK, new Item.Settings()));
    public static final Item METAL_BARREL = register("metal_barrel", new BlockItem(ModBlocks.METAL_BARREL, new Item.Settings()));
    public static final Item CARDBOARD_BOX = register("cardboard_box", new BlockItem(ModBlocks.CARDBOARD_BOX, new Item.Settings()));
    public static final Item VENDING_MACHINE = register("vending_machine", new BlockItem(ModBlocks.VENDING_MACHINE, new Item.Settings()));
    public static final Item SECURITY_CAMERA = register("security_camera", new BlockItem(ModBlocks.SECURITY_CAMERA, new Item.Settings()));

    private static Item register(String name, Item item) {
        return Registry.register(Registries.ITEM, Identifier.of(BackroomsMod.MOD_ID, name), item);
    }

    private ModItems() {
    }
}
