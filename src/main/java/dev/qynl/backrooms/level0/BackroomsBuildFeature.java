package dev.qynl.backrooms.level0;

import dev.qynl.backrooms.light.FluorescentLightBlock;
import dev.qynl.backrooms.light.LightFlickerSystem;
import dev.qynl.backrooms.registry.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.state.property.IntProperty;
import net.minecraft.structure.StructureWorldAccess;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * Materialises one chunk of a Backrooms level from {@link Level0Layout}.
 *
 * <p>Everything the level is (walls, rooms, pillars, lights, stains) is decided by the layout core;
 * this feature is a dumb painter that turns its per-column answers into blocks. It runs once per
 * chunk in the flat dimension, and because the layout is a pure function of (seed, drift, level,
 * column), chunks agree on their shared borders without any cross-chunk state. The block palette is
 * chosen by {@code levelId}: Level 0 is the yellow wallpaper maze, Level 1 a damp concrete
 * warehouse, Level 2 a narrow brick-and-steel utility tunnel, Level 3 a wired brick electrical
 * station, Level 4 an empty office.
 */
public class BackroomsBuildFeature extends Feature<DefaultFeatureConfig> {

    private static final IntProperty VARIANT = VariantBlock.VARIANT;
    private final int levelId;

    public BackroomsBuildFeature(int levelId) {
        super(DefaultFeatureConfig.CODEC);
        this.levelId = levelId;
    }

    @Override
    public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
        StructureWorldAccess world = context.getWorld();
        BlockPos origin = context.getOrigin();
        Level0Layout layout = Level0Holder.get(world.getSeed(), levelId);

        int cx = origin.getX() >> 4;
        int cz = origin.getZ() >> 4;
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = (cx << 4) + lx;
                int z = (cz << 4) + lz;
                paintColumn(world, mutable, layout, x, z);
            }
        }
        return true;
    }

    private void paintColumn(StructureWorldAccess world, BlockPos.Mutable mutable,
                             Level0Layout layout, int x, int z) {
        int bits = layout.tileBits(x, z);
        boolean solid = (bits & Level0Layout.BIT_SOLID) != 0;
        boolean pillar = (bits & Level0Layout.BIT_PILLAR) != 0;
        int surface = layout.surface(x, z);
        int lightState = layout.lightState(x, z);
        int ceilingY = layout.ceilingY(x, z);
        boolean level0 = levelId == 0;

        // The walkable floor always exists, even under walls, so there is never a hole.
        mutable.set(x, Level0Layout.FLOOR_Y, z);
        world.setBlockState(mutable, floorState(surface, level0), 3);

        // Ways down that dress as part of the level (flickering wall, door, elevator).
        if (solid) {
            Block exit = exitBlockAt(x, z);
            if (exit != null) {
                BlockState es = exit.getDefaultState();
                for (int y = Level0Layout.WALL_MIN_Y; y <= ceilingY; y++) {
                    mutable.set(x, y, z);
                    world.setBlockState(mutable, es, 3);
                }
                return;
            }
        }

        if (solid || pillar) {
            BlockState wall = (pillar && (levelId == 2 || levelId == 3 || levelId == 6))
                    ? ModBlocks.METAL.getDefaultState()
                    : (pillar && levelId == 4)
                            ? ModBlocks.CONCRETE.getDefaultState()
                            : wallState(surface, level0, x, z, pillar);
            for (int y = Level0Layout.WALL_MIN_Y; y <= ceilingY; y++) {
                mutable.set(x, y, z);
                world.setBlockState(mutable, wall, 3);
            }
            return;
        }

        // Open space: air inside (flooded on Level 7), ceiling on top.
        for (int y = Level0Layout.WALL_MIN_Y; y < ceilingY; y++) {
            mutable.set(x, y, z);
            if (levelId == 7 && y < ceilingY - 1) {
                // Thalassophobia: the corridor is ocean; leave an air gap to surface into.
                world.setBlockState(mutable, Blocks.WATER.getDefaultState(), 3);
            } else {
                world.setBlockState(mutable, Blocks.AIR.getDefaultState(), 3);
            }
        }
        mutable.set(x, ceilingY, z);
        if (lightState != Level0Layout.LIGHT_NONE) {
            boolean lit = lightState != Level0Layout.LIGHT_DEAD;
            world.setBlockState(mutable,
                    ModBlocks.FLUORESCENT_LIGHT.getDefaultState().with(FluorescentLightBlock.LIT, lit), 3);
            if (lightState == Level0Layout.LIGHT_FLICKERING) {
                LightFlickerSystem.registerFlicker(world.getRegistryKey(), mutable);
            }
        } else {
            world.setBlockState(mutable, ceilingState(surface, level0), 3);
        }

        // Level 1 -> Level 2: walk a corridor that runs on far longer than it should.
        if (levelId == 1 && layout.style(x, z) == Level0Layout.Style.LONG && deepExitRoll(x, z)) {
            mutable.set(x, Level0Layout.WALL_MIN_Y, z);
            world.setBlockState(mutable, ModBlocks.DEEP_EXIT.getDefaultState(), 3);
            return;
        }

        // Level 3: rusty bars blocking the hall (passable, but you feel them).
        if (levelId == 3 && metalBarsRoll(x, z)) {
            BlockState bars = ModBlocks.METAL_BARS.getDefaultState();
            for (int y = Level0Layout.WALL_MIN_Y; y < ceilingY; y++) {
                mutable.set(x, y, z);
                world.setBlockState(mutable, bars, 3);
            }
            return;
        }

        // Level-specific floor dressing and clutter.
        int prop = layout.propAt(x, z);
        if (prop != Level0Layout.PROP_NONE) {
            mutable.set(x, Level0Layout.WALL_MIN_Y, z);
            world.setBlockState(mutable, propBlock(prop).getDefaultState(), 3);
        } else if (layout.puddleAt(x, z)) {
            // Level 1: a shallow pool of stagnant water on the concrete.
            mutable.set(x, Level0Layout.WALL_MIN_Y, z);
            world.setBlockState(mutable, ModBlocks.PUDDLE.getDefaultState(), 3);
        }
        if (layout.cameraAt(x, z) && ceilingY - 1 > Level0Layout.WALL_MIN_Y) {
            mutable.set(x, ceilingY - 1, z);
            world.setBlockState(mutable, ModBlocks.SECURITY_CAMERA.getDefaultState(), 3);
        }
    }

    private BlockState wallState(int surface, boolean level0, int x, int z, boolean pillar) {
        if (levelId == 4) {
            // Office drywall, with the odd blacked-out window.
            return (wallWindowRoll(x, z) ? ModBlocks.OFFICE_WINDOW : ModBlocks.OFFICE_WALL).getDefaultState();
        }
        if (levelId == 5) {
            return ModBlocks.HOTEL_WALL.getDefaultState();
        }
        if (levelId == 6) {
            // Lights Out: cold metal, here and there wrapped in piping.
            return (!pillar && wallPipeRoll(x, z) ? ModBlocks.PIPE_WALL : ModBlocks.METAL).getDefaultState();
        }
        if (levelId == 7 || levelId == 8) {
            return ModBlocks.CONCRETE.getDefaultState();  // rock: ocean islands / cave walls
        }
        if (!level0) {
            // Levels 2 and 3 carry heavy piping and wiring along their brick walls.
            if ((levelId == 2 || levelId == 3) && !pillar && wallPipeRoll(x, z)) {
                return ModBlocks.PIPE_WALL.getDefaultState();
            }
            return (levelId == 1 ? ModBlocks.CONCRETE : ModBlocks.BRICK).getDefaultState();
        }
        return withVariant(ModBlocks.WALLPAPER.getDefaultState(), surface);
    }

    private BlockState floorState(int surface, boolean level0) {
        if (levelId == 4) {
            return ModBlocks.OFFICE_CARPET.getDefaultState();
        }
        if (levelId == 5 || levelId == 7) {
            // Terror Hotel floor; the carpeted ocean floor of Level 7.
            return ModBlocks.HOTEL_CARPET.getDefaultState();
        }
        return level0 ? withVariant(ModBlocks.CARPET.getDefaultState(), surface)
                : ModBlocks.CONCRETE.getDefaultState();
    }

    private BlockState ceilingState(int surface, boolean level0) {
        if (levelId == 4) {
            return ModBlocks.OFFICE_WALL.getDefaultState();
        }
        if (levelId == 5) {
            return ModBlocks.HOTEL_WALL.getDefaultState();
        }
        if (levelId == 6) {
            return ModBlocks.METAL.getDefaultState();
        }
        return level0 ? withVariant(ModBlocks.CEILING_TILE.getDefaultState(), surface)
                : ModBlocks.CONCRETE.getDefaultState();
    }

    /** Deterministic per-column roll so chunk borders agree on where pipes run. */
    private static boolean wallPipeRoll(int x, int z) {
        long h = ((long) x * 0x9E3779B97F4A7C15L) ^ ((long) z * 0xC2B2AE3D27D4EB4FL);
        return Math.floorMod(h >>> 33, 100) < 22;
    }

    private Block propBlock(int prop) {
        if (levelId == 1) {
            return switch (prop) {
                case Level0Layout.PROP_CHAIR, Level0Layout.PROP_BOX -> ModBlocks.WOOD_CRATE;
                case Level0Layout.PROP_BARREL -> ModBlocks.METAL_BARREL;
                case Level0Layout.PROP_DESK -> ModBlocks.DEBRIS_PILE;
                default -> ModBlocks.WOOD_CRATE;
            };
        }
        if (levelId == 2) {
            return switch (prop) {
                case Level0Layout.PROP_CHAIR -> ModBlocks.MACHINERY;
                case Level0Layout.PROP_DESK -> ModBlocks.METAL_BARREL;
                case Level0Layout.PROP_BARREL -> ModBlocks.WOOD_CRATE;
                case Level0Layout.PROP_BOX -> ModBlocks.DEBRIS_PILE;
                default -> ModBlocks.CARDBOARD_BOX;
            };
        }
        if (levelId == 3) {
            return switch (prop) {
                case Level0Layout.PROP_CHAIR -> ModBlocks.MACHINERY;
                case Level0Layout.PROP_DESK -> ModBlocks.METAL_BARREL;
                case Level0Layout.PROP_BARREL -> ModBlocks.WOOD_CRATE;
                case Level0Layout.PROP_BOX -> ModBlocks.DEBRIS_PILE;
                default -> ModBlocks.MACHINERY;
            };
        }
        if (levelId == 4) {
            return switch (prop) {
                case Level0Layout.PROP_CHAIR -> ModBlocks.OFFICE_CHAIR;
                case Level0Layout.PROP_DESK -> ModBlocks.DESK;
                case Level0Layout.PROP_BARREL -> ModBlocks.WATER_COOLER;
                case Level0Layout.PROP_BOX -> ModBlocks.CARDBOARD_BOX;
                default -> ModBlocks.VENDING_MACHINE;
            };
        }
        if (levelId == 5) {
            return switch (prop) {
                case Level0Layout.PROP_CHAIR -> ModBlocks.OFFICE_CHAIR;
                case Level0Layout.PROP_DESK -> ModBlocks.DESK;
                case Level0Layout.PROP_BARREL -> ModBlocks.WOOD_CRATE;
                case Level0Layout.PROP_BOX -> ModBlocks.CARDBOARD_BOX;
                default -> ModBlocks.VENDING_MACHINE;
            };
        }
        if (levelId == 6) {
            return switch (prop) {
                case Level0Layout.PROP_BARREL -> ModBlocks.METAL_BARREL;
                default -> ModBlocks.DEBRIS_PILE;
            };
        }
        if (levelId == 7) {
            // Drifting wreckage on the sea floor.
            return prop == Level0Layout.PROP_BARREL ? ModBlocks.METAL_BARREL : ModBlocks.WOOD_CRATE;
        }
        if (levelId == 8) {
            // Cave rubble and stalagmites.
            return ModBlocks.DEBRIS_PILE;
        }
        return switch (prop) {
            case Level0Layout.PROP_CHAIR -> ModBlocks.OFFICE_CHAIR;
            case Level0Layout.PROP_DESK -> ModBlocks.DESK;
            case Level0Layout.PROP_BARREL -> ModBlocks.METAL_BARREL;
            case Level0Layout.PROP_BOX -> ModBlocks.CARDBOARD_BOX;
            default -> ModBlocks.VENDING_MACHINE;
        };
    }

    /** The way down for this level, or null where this column is ordinary wall. */
    private Block exitBlockAt(int x, int z) {
        if (levelId == 0 && flickerWallRoll(x, z)) return ModBlocks.FLICKER_WALL;
        if (levelId == 2 && exitDoorRoll(x, z)) return ModBlocks.EXIT_DOOR;
        if (levelId == 3 && elevatorRoll(x, z)) return ModBlocks.ELEVATOR;
        if (levelId == 4 && stairDoorRoll(x, z)) return ModBlocks.STAIR_DOOR;
        if (levelId == 5 && boilerDoorRoll(x, z)) return ModBlocks.BOILER_DOOR;
        if (levelId == 6 && stairDownRoll(x, z)) return ModBlocks.STAIR_DOWN;
        if (levelId == 7 && caveMouthRoll(x, z)) return ModBlocks.CAVE_MOUTH;
        return null;
    }

    /** Level 6 -> 7: the staircase down to the ocean (wiki: stairs from Level 6). */
    private static boolean stairDownRoll(int x, int z) {
        long h = ((long) x * 0x9E3779B97F4A7C15L) ^ ((long) z * 0x7EA5E7L);
        return Math.floorMod(h >>> 21, 1000) < 4;
    }

    /** Level 7 -> 8: the underwater cave mouth (wiki: cave in an underwater mountain). */
    private static boolean caveMouthRoll(int x, int z) {
        long h = ((long) x * 0xCA4E17L) ^ ((long) z * 0xC6BC279692B5C323L);
        return Math.floorMod(h >>> 23, 1000) < 3;
    }

    private static boolean stairDoorRoll(int x, int z) {
        long h = ((long) x * 0x9E3779B97F4A7C15L) ^ ((long) z * 0x100000001B3L);
        return Math.floorMod(h >>> 23, 1000) < 4;
    }

    private static boolean boilerDoorRoll(int x, int z) {
        long h = ((long) x * 0xBF58476D1CE4E5B9L) ^ ((long) z * 0xC6BC279692B5C323L);
        return Math.floorMod(h >>> 19, 1000) < 3;
    }

    /** Rare, deterministic, so chunk borders agree on where the way down appears. */
    private static boolean flickerWallRoll(int x, int z) {
        long h = ((long) x * 0xD1B54A32D192ED03L) ^ ((long) z * 0x0AEF1750FE6D5C3L);
        return Math.floorMod(h >>> 29, 1000) < 4;
    }

    private static boolean deepExitRoll(int x, int z) {
        long h = ((long) x * 0x9E3779B97F4A7C15L) ^ ((long) z * 0xBF58476D1CE4E5B9L);
        return Math.floorMod(h >>> 31, 1000) < 6;
    }

    private static boolean exitDoorRoll(int x, int z) {
        long h = ((long) x * 0xC6BC279692B5C323L) ^ ((long) z * 0x9E3779B97F4A7C15L);
        return Math.floorMod(h >>> 27, 1000) < 4;
    }

    private static boolean elevatorRoll(int x, int z) {
        long h = ((long) x * 0x100000001B3L) ^ ((long) z * 0xC2B2AE3D27D4EB4FL);
        return Math.floorMod(h >>> 25, 1000) < 3;
    }

    private static boolean metalBarsRoll(int x, int z) {
        long h = ((long) x * 0xD6E8FEB86659FD93L) ^ ((long) z * 0x9E3779B97F4A7C15L);
        return Math.floorMod(h >>> 37, 1000) < 5;
    }

    private static boolean wallWindowRoll(int x, int z) {
        long h = ((long) x * 0x8DA6B343D192ED03L) ^ ((long) z * 0xD1B54A32D192ED03L);
        return Math.floorMod(h >>> 41, 1000) < 60;
    }

    private static BlockState withVariant(BlockState state, int variant) {
        return state.with(VARIANT, Math.max(0, Math.min(3, variant)));
    }
}
