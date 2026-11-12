package dev.randomised.dispensertest;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Dimension + position key, identical on the client and the integrated server. */
public record BlockKey(String dimension, long pos) {

    public static BlockKey of(Level level, BlockPos p) {
        return new BlockKey(level.dimension().toString(), p.asLong());
    }

    public BlockPos blockPos() {
        return BlockPos.of(pos);
    }
}
