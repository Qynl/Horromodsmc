package dev.qynl.backrooms.level0;

import net.minecraft.util.math.BlockPos;

/** Finds the nearest walkable column to a target point, for a quiet arrival. */
public final class BackroomsSpawn {

    public static BlockPos find(Level0Layout layout, int targetX, int targetZ) {
        for (int r = 0; r < 48; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    if (!layout.isSolid(targetX + dx, targetZ + dz)) {
                        return new BlockPos(targetX + dx, Level0Layout.FLOOR_Y + 1, targetZ + dz);
                    }
                }
            }
        }
        return new BlockPos(targetX, Level0Layout.FLOOR_Y + 1, targetZ);
    }

    private BackroomsSpawn() {
    }
}
