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

    // Structural palette, so every buildable block is reachable from the creative tab.
    public static final Item CONCRETE = register("concrete", new BlockItem(ModBlocks.CONCRETE, new Item.Settings()));
    public static final Item BRICK = register("brick", new BlockItem(ModBlocks.BRICK, new Item.Settings()));
    public static final Item METAL = register("metal", new BlockItem(ModBlocks.METAL, new Item.Settings()));
    public static final Item PIPE_WALL = register("pipe_wall", new BlockItem(ModBlocks.PIPE_WALL, new Item.Settings()));
    public static final Item PUDDLE = register("puddle", new BlockItem(ModBlocks.PUDDLE, new Item.Settings()));
    public static final Item WOOD_CRATE = register("wood_crate", new BlockItem(ModBlocks.WOOD_CRATE, new Item.Settings()));
    public static final Item DEBRIS_PILE = register("debris_pile", new BlockItem(ModBlocks.DEBRIS_PILE, new Item.Settings()));
    public static final Item MACHINERY = register("machinery", new BlockItem(ModBlocks.MACHINERY, new Item.Settings()));
    public static final Item METAL_BARS = register("metal_bars", new BlockItem(ModBlocks.METAL_BARS, new Item.Settings()));
    public static final Item OFFICE_WALL = register("office_wall", new BlockItem(ModBlocks.OFFICE_WALL, new Item.Settings()));
    public static final Item OFFICE_CARPET = register("office_carpet", new BlockItem(ModBlocks.OFFICE_CARPET, new Item.Settings()));
    public static final Item OFFICE_WINDOW = register("office_window", new BlockItem(ModBlocks.OFFICE_WINDOW, new Item.Settings()));
    public static final Item WATER_COOLER = register("water_cooler", new BlockItem(ModBlocks.WATER_COOLER, new Item.Settings()));
    public static final Item HOTEL_CARPET = register("hotel_carpet", new BlockItem(ModBlocks.HOTEL_CARPET, new Item.Settings()));
    public static final Item HOTEL_WALL = register("hotel_wall", new BlockItem(ModBlocks.HOTEL_WALL, new Item.Settings()));

    /** Every placeable block item, in creative-tab order. */
    public static final Item[] ALL = {
            WALLPAPER, CARPET, CEILING_TILE, FLUORESCENT_LIGHT,
            CONCRETE, BRICK, METAL, PIPE_WALL, METAL_BARS, OFFICE_WALL, OFFICE_CARPET, OFFICE_WINDOW,
            HOTEL_WALL, HOTEL_CARPET, PUDDLE,
            OFFICE_CHAIR, DESK, WATER_COOLER, CARDBOARD_BOX, WOOD_CRATE, DEBRIS_PILE, METAL_BARREL,
            MACHINERY, VENDING_MACHINE, SECURITY_CAMERA,
    };

    private static Item register(String name, Item item) {
        return Registry.register(Registries.ITEM, Identifier.of(BackroomsMod.MOD_ID, name), item);
    }

    private ModItems() {
    }
}
