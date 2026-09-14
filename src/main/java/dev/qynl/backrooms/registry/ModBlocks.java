package dev.qynl.backrooms.registry;

import dev.qynl.backrooms.BackroomsMod;
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
