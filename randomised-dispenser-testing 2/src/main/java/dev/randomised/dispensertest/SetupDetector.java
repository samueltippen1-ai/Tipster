package dev.randomised.dispensertest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;

import java.util.Optional;

/**
 * Detects: [container] [solid block] [container] in a straight line on the X or Z axis,
 * with a lever attached to the solid middle block. Runs on demand (lever pull / container open),
 * so there is no world scanning and no per-tick cost.
 */
public final class SetupDetector {

    private SetupDetector() {}

    public record Setup(BlockPos a, BlockPos b, BlockPos middle, BlockPos lever) {}

    private static final Direction[] AXES = {Direction.EAST, Direction.SOUTH};

    public static boolean isContainer(BlockState s) {
        return s.is(Blocks.DISPENSER) || s.is(Blocks.DROPPER);
    }

    public static boolean isLever(BlockState s) {
        return s.is(Blocks.LEVER);
    }

    /** Direction from the lever towards the block it is attached to. */
    public static Direction attachedDirection(BlockState lever) {
        AttachFace face = lever.getValue(FaceAttachedHorizontalDirectionalBlock.FACE);
        return switch (face) {
            case FLOOR -> Direction.DOWN;
            case CEILING -> Direction.UP;
            default -> lever.getValue(HorizontalDirectionalBlock.FACING).getOpposite();
        };
    }

    /** Start from a lever the player just used. */
    public static Optional<Setup> fromLever(BlockGetter level, BlockPos leverPos) {
        BlockState ls = level.getBlockState(leverPos);
        if (!isLever(ls)) return Optional.empty();
        return fromMiddle(level, leverPos.relative(attachedDirection(ls)), leverPos);
    }

    /** Start from a container the player opened. */
    public static Optional<Setup> fromContainer(BlockGetter level, BlockPos container) {
        if (!isContainer(level.getBlockState(container))) return Optional.empty();
        for (Direction axis : AXES) {
            for (Direction d : new Direction[] {axis, axis.getOpposite()}) {
                BlockPos mid = container.relative(d);
                BlockPos other = mid.relative(d);
                if (!isContainer(level.getBlockState(other))) continue;
                if (!isSolid(level, mid)) continue;
                Optional<BlockPos> lever = findLever(level, mid);
                if (lever.isPresent()) return Optional.of(new Setup(container, other, mid, lever.get()));
            }
        }
        return Optional.empty();
    }

    private static Optional<Setup> fromMiddle(BlockGetter level, BlockPos mid, BlockPos lever) {
        if (!isSolid(level, mid)) return Optional.empty();
        for (Direction axis : AXES) {
            BlockPos a = mid.relative(axis);
            BlockPos b = mid.relative(axis.getOpposite());
            if (isContainer(level.getBlockState(a)) && isContainer(level.getBlockState(b))) {
                return Optional.of(new Setup(a, b, mid, lever));
            }
        }
        return Optional.empty();
    }

    private static boolean isSolid(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).isCollisionShapeFullBlock(level, pos);
    }

    private static Optional<BlockPos> findLever(BlockGetter level, BlockPos mid) {
        for (Direction d : Direction.values()) {
            BlockPos p = mid.relative(d);
            BlockState s = level.getBlockState(p);
            if (isLever(s) && attachedDirection(s) == d.getOpposite()) return Optional.of(p);
        }
        return Optional.empty();
    }
}
