package dev.randomised.dispensertest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-thread-only state: what each dispenser/dropper contained when last inspected, and in which
 * order they were inspected.
 *
 * <p>The client never knows which block a container GUI belongs to, so a right-click on a container
 * records a "pending" position (UseBlockCallback). The next dispenser GUI to open claims it. If the
 * pending click is stale (server refused to open the GUI) the crosshair target is used as a fallback.
 *
 * <p>Ordering uses a monotonically increasing sequence number rather than timestamps, so opening and
 * closing both containers many times in the same tick or in quick succession is still ordered exactly.
 */
public final class ContainerTracker {

    public static final int SLOTS = 9;

    private final Map<BlockKey, int[]> snapshots = new HashMap<>();
    private final Map<BlockKey, Long> inspectSeq = new HashMap<>();
    private long seq = 0;
    private long tick = 0;

    private BlockKey pending;
    private long pendingTick;

    private Screen currentScreen;
    private BlockKey currentKey;

    /** Player right-clicked a container block (client side, before the GUI opens). */
    public void onUse(Level level, BlockPos pos) {
        pending = BlockKey.of(level, pos);
        pendingTick = tick;
    }

    /** @return true if this is a newly opened container screen (false for resize re-inits). */
    public boolean onScreenInit(Minecraft mc, Screen screen) {
        if (screen == currentScreen) return false;
        currentScreen = screen;
        currentKey = resolveKey(mc);
        if (currentKey != null) {
            inspectSeq.put(currentKey, ++seq);
            refresh(screen);
        }
        return true;
    }

    public void onScreenClosed(Screen screen) {
        if (screen != currentScreen) return;
        if (currentKey != null) refresh(screen); // final snapshot
        currentScreen = null;
        currentKey = null;
    }

    /** Keeps the snapshot current while the GUI is open (contents arrive after the GUI opens). */
    public void onTick(Minecraft mc) {
        tick++;
        if (currentScreen != null && currentKey != null && mc.screen == currentScreen) {
            refresh(currentScreen);
        }
    }

    public int[] snapshot(BlockKey key) {
        return snapshots.get(key);
    }

    /** 0 if never inspected; higher = more recent. */
    public long seqOf(BlockKey key) {
        return inspectSeq.getOrDefault(key, 0L);
    }

    public void clear() {
        snapshots.clear();
        inspectSeq.clear();
        pending = null;
        currentScreen = null;
        currentKey = null;
        seq = 0;
    }

    // ------------------------------------------------------------------

    private BlockKey resolveKey(Minecraft mc) {
        Level level = mc.level;
        if (level == null) return null;

        BlockKey key = null;
        if (pending != null && tick - pendingTick <= RdtConfig.get().pendingOpenTimeoutTicks) {
            key = pending;
        } else if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            key = BlockKey.of(level, hit.getBlockPos());
        }
        pending = null;

        if (key == null || !SetupDetector.isContainer(level.getBlockState(key.blockPos()))) return null;
        return key;
    }

    private void refresh(Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?> acs)) return;
        AbstractContainerMenu menu = acs.getMenu();
        if (!(menu instanceof DispenserMenu)) return;

        RdtConfig.ValueMode mode = RdtConfig.get().valueMode;
        int[] values = new int[SLOTS];
        for (int i = 0; i < SLOTS && i < menu.slots.size(); i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            if (!stack.isEmpty()) {
                values[i] = mode == RdtConfig.ValueMode.COUNT ? stack.getCount() : i + 1;
            }
        }
        snapshots.put(currentKey, values);
    }
}
