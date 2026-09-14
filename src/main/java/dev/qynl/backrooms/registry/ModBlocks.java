package dev.qynl.backrooms.registry;

import dev.qynl.backrooms.BackroomsMod;
import dev.qynl.backrooms.hole.LevelExitBlock;
import dev.qynl.backrooms.hole.RealityTearBlock;
import dev.qynl.backrooms.level0.VariantBlock;
import dev.qynl.backrooms.light.FluorescentLightBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.SoundGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModBlocks {

    public static final Block WALLPAPER = register("wallpaper", new VariantBlock(
            AbstractBlock.Settings.create()
                    .mapColor(MapColor.YELLOW)
                    .strength(1.4f, 3.0f)
                    .sounds(SoundGroup.STONE)));

    public static final Block CARPET = register("carpet", new VariantBlock(
            AbstractBlock.Settings.create()
                    .mapColor(MapColor.YELLOW)
                    .strength(0.4f)
                    .sounds(carpetGroup())));

    public static final Block CEILING_TILE = register("ceiling_tile", new VariantBlock(
            AbstractBlock.Settings.create()
                    .mapColor(MapColor.PALE_YELLOW)
                    .strength(0.8f)
                    .sounds(SoundGroup.STONE)));

    public static final Block FLUORESCENT_LIGHT = register("fluorescent_light", new FluorescentLightBlock(
            AbstractBlock.Settings.create()
                    .mapColor(MapColor.WHITE)
                    .luminance(state -> state.get(FluorescentLightBlock.LIT) ? 15 : 0)
                    .strength(0.6f)
                    .sounds(SoundGroup.GLASS)));

    public static final Block REALITY_TEAR = register("reality_tear", new RealityTearBlock(
            AbstractBlock.Settings.create()
                    .mapColor(MapColor.WHITE)
                    .noCollision()
                    .nonOpaque()
                    .strength(-1.0f, 3600000.0f)
                    .luminance(12)
                    .sounds(SoundGroup.GLASS)));

    // Sparse, lived-in clutter. Non-opaque so they read as objects, not walls.
    public static final Block OFFICE_CHAIR = register("office_chair", prop(0.5f));
    public static final Block DESK = register("desk", prop(0.8f));
    public static final Block METAL_BARREL = register("metal_barrel", prop(0.9f));
    public static final Block CARDBOARD_BOX = register("cardboard_box", prop(0.3f));
    public static final Block VENDING_MACHINE = register("vending_machine", prop(1.5f));
    public static final Block SECURITY_CAMERA = register("security_camera", new Block(
            AbstractBlock.Settings.create()
                    .mapColor(MapColor.GRAY)
                    .noCollision()
                    .nonOpaque()
                    .strength(0.4f)
                    .sounds(SoundGroup.METAL)));

    // Level 1 / Level 2 structural palette: damp concrete warehouse and brick-and-steel utility tunnels.
    public static final Block CONCRETE = register("concrete", new Block(
            AbstractBlock.Settings.create().mapColor(MapColor.GRAY).strength(1.8f, 6.0f).sounds(SoundGroup.STONE)));
    public static final Block BRICK = register("brick", new Block(
            AbstractBlock.Settings.create().mapColor(MapColor.BROWN).strength(2.0f, 6.0f).sounds(SoundGroup.STONE)));
    public static final Block METAL = register("metal", new Block(
            AbstractBlock.Settings.create().mapColor(MapColor.IRON_GRAY).strength(3.0f, 9.0f).sounds(SoundGroup.METAL)));
    public static final Block PIPE_WALL = register("pipe_wall", new Block(
            AbstractBlock.Settings.create().mapColor(MapColor.IRON_GRAY).strength(2.5f, 8.0f).sounds(SoundGroup.METAL)));
    public static final Block PUDDLE = register("puddle", new Block(
            AbstractBlock.Settings.create().mapColor(MapColor.WATER_BLUE)
                    .noCollision().nonOpaque().strength(0.2f).sounds(SoundGroup.WET_GRASS)));
    public static final Block WOOD_CRATE = register("wood_crate", prop(0.8f));
    public static final Block DEBRIS_PILE = register("debris_pile", prop(0.4f));
    public static final Block MACHINERY = register("machinery", new Block(
            AbstractBlock.Settings.create().mapColor(MapColor.IRON_GRAY).strength(2.5f, 8.0f).sounds(SoundGroup.METAL)));

    // Ways down, per the wiki: a flickering wall (Level 0 -> 1) and a too-long corridor (Level 1 -> 2).
    public static final Block FLICKER_WALL = register("flicker_wall", new LevelExitBlock(
            AbstractBlock.Settings.create()
                    .mapColor(MapColor.YELLOW)
                    .noCollision()
                    .strength(-1.0f, 3600000.0f)
                    .sounds(SoundGroup.WOOL), 1));

    public static final Block DEEP_EXIT = register("deep_exit", new LevelExitBlock(
            AbstractBlock.Settings.create()
                    .mapColor(MapColor.BLACK)
                    .noCollision()
                    .strength(-1.0f, 3600000.0f)
                    .sounds(SoundGroup.STONE), 2));

    // Level 3 - Electrical Station: rusty bars that block the halls, wired brick.
    public static final Block METAL_BARS = register("metal_bars", new Block(
            AbstractBlock.Settings.create()
                    .mapColor(MapColor.IRON_GRAY)
                    .noCollision()
                    .nonOpaque()
                    .strength(2.0f, 6.0f)
                    .sounds(SoundGroup.METAL)));

    // Level 4 - Abandoned Office: pale drywall, cool carpet, blacked-out windows, water coolers.
    public static final Block OFFICE_WALL = register("office_wall", new Block(
            AbstractBlock.Settings.create().mapColor(MapColor.WHITE_GRAY).strength(1.2f, 3.0f).sounds(SoundGroup.STONE)));
    public static final Block OFFICE_CARPET = register("office_carpet", new Block(
            AbstractBlock.Settings.create().mapColor(MapColor.BLUE).strength(0.4f).sounds(SoundGroup.WOOL)));
    public static final Block OFFICE_WINDOW = register("office_window", new Block(
            AbstractBlock.Settings.create().mapColor(MapColor.BLACK).strength(0.8f, 3.0f).sounds(SoundGroup.GLASS)));
    public static final Block WATER_COOLER = register("water_cooler", prop(0.6f));

    // Ways down, per the wiki: an unlocked door on Level 2 -> 3, an elevator on Level 3 -> 4.
    public static final Block EXIT_DOOR = register("exit_door", new LevelExitBlock(
            AbstractBlock.Settings.create()
                    .mapColor(MapColor.IRON_GRAY)
                    .noCollision()
                    .strength(-1.0f, 3600000.0f)
                    .sounds(SoundGroup.METAL), 3));

    public static final Block ELEVATOR = register("elevator", new LevelExitBlock(
            AbstractBlock.Settings.create()
                    .mapColor(MapColor.IRON_GRAY)
                    .noCollision()
                    .strength(-1.0f, 3600000.0f)
                    .sounds(SoundGroup.METAL), 4));

    // Level 5 - Terror Hotel: red carpet and ornate wallpaper.
    public static final Block HOTEL_CARPET = register("hotel_carpet", new Block(
            AbstractBlock.Settings.create().mapColor(MapColor.RED).strength(0.4f).sounds(SoundGroup.WOOL)));
    public static final Block HOTEL_WALL = register("hotel_wall", new Block(
            AbstractBlock.Settings.create().mapColor(MapColor.TAN).strength(1.2f, 3.0f).sounds(SoundGroup.WOOD)));

    // Ways down: an office stairway door (Level 4 -> 5) and a boiler-room door (Level 5 -> 6).
    public static final Block STAIR_DOOR = register("stair_door", new LevelExitBlock(
            AbstractBlock.Settings.create()
                    .mapColor(MapColor.IRON_GRAY).noCollision()
                    .strength(-1.0f, 3600000.0f).sounds(SoundGroup.METAL), 5));
    public static final Block BOILER_DOOR = register("boiler_door", new LevelExitBlock(
            AbstractBlock.Settings.create()
                    .mapColor(MapColor.BLACK).noCollision()
                    .strength(-1.0f, 3600000.0f).sounds(SoundGroup.METAL), 6));

    private static Block prop(float strength) {
        return new Block(AbstractBlock.Settings.create()
                .mapColor(MapColor.GRAY)
                .strength(strength)
                .nonOpaque()
                .sounds(SoundGroup.WOOD));
    }

    private static SoundGroup carpetGroup() {
        return new SoundGroup(0.9f, 0.9f,
                ModSoundEvents.CARPET_BREAK,
                ModSoundEvents.CARPET_STEP,
                ModSoundEvents.CARPET_PLACE,
                ModSoundEvents.CARPET_STEP,
                ModSoundEvents.CARPET_BREAK);
    }

    private static Block register(String name, Block block) {
        return Registry.register(Registries.BLOCK, Identifier.of(BackroomsMod.MOD_ID, name), block);
    }

    private ModBlocks() {
    }
}
