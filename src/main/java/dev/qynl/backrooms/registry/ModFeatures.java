package dev.qynl.backrooms.registry;

import dev.qynl.backrooms.BackroomsMod;
import dev.qynl.backrooms.hole.RealityHoleFeature;
import dev.qynl.backrooms.level0.BackroomsBuildFeature;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;

public final class ModFeatures {

    public static final Feature<DefaultFeatureConfig> LEVEL0_BUILD =
            register("level0_build", new BackroomsBuildFeature(0));

    public static final Feature<DefaultFeatureConfig> LEVEL1_BUILD =
            register("level1_build", new BackroomsBuildFeature(1));

    public static final Feature<DefaultFeatureConfig> LEVEL2_BUILD =
            register("level2_build", new BackroomsBuildFeature(2));

    public static final Feature<DefaultFeatureConfig> LEVEL3_BUILD =
            register("level3_build", new BackroomsBuildFeature(3));

    public static final Feature<DefaultFeatureConfig> LEVEL4_BUILD =
            register("level4_build", new BackroomsBuildFeature(4));

    public static final Feature<DefaultFeatureConfig> LEVEL5_BUILD =
            register("level5_build", new BackroomsBuildFeature(5));

    public static final Feature<DefaultFeatureConfig> LEVEL6_BUILD =
            register("level6_build", new BackroomsBuildFeature(6));

    public static final Feature<DefaultFeatureConfig> REALITY_HOLE =
            register("reality_hole", new RealityHoleFeature());

    private static Feature<DefaultFeatureConfig> register(String name, Feature<DefaultFeatureConfig> feature) {
        return Registry.register(Registries.FEATURE, Identifier.of(BackroomsMod.MOD_ID, name), feature);
    }

    private ModFeatures() {
    }
}
