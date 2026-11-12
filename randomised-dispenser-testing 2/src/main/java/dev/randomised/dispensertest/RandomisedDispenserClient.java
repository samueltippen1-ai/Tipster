package dev.randomised.dispensertest;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Client entrypoint. Wires Fabric events to the tracker (inspection order) and to the lever handler
 * (outcome computation). Everything here runs on the client thread.
 *
 * Active only while playing a single-player world: on a remote server the client cannot influence
 * dispensing, so the mod stays completely inert there.
 */
public class RandomisedDispenserClient implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("RandomisedDispenserTesting");

    private final ContainerTracker tracker = new ContainerTracker();

    @Override
    public void onInitializeClient() {
        RdtConfig.load();

        UseBlockCallback.EVENT.register(this::onUseBlock);
        ClientTickEvents.END_CLIENT_TICK.register(tracker::onTick);

        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!isActive(client)) return;
            if (screen instanceof AbstractContainerScreen<?> acs && acs.getMenu() instanceof DispenserMenu) {
                if (tracker.onScreenInit(client, screen)) {
                    ScreenEvents.remove(screen).register(tracker::onScreenClosed);
                }
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            tracker.clear();
            ForcedSlots.clear();
        });

        LOGGER.info("Randomised Dispenser Testing loaded (client-side, single-player only).");
    }

    private static boolean isActive(Minecraft mc) {
        return RdtConfig.get().enabled && mc.getSingleplayerServer() != null;
    }

    private InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide() || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        Minecraft mc = Minecraft.getInstance();
        if (!isActive(mc)) return InteractionResult.PASS;

        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);

        if (SetupDetector.isContainer(state)) {
            tracker.onUse(level, pos);
        } else if (SetupDetector.isLever(state)) {
            onLever(mc, level, pos, state);
        }
        return InteractionResult.PASS; // never cancel or alter vanilla interaction
    }

    private void onLever(Minecraft mc, Level level, BlockPos leverPos, BlockState state) {
        if (state.getValue(BlockStateProperties.POWERED)) return; // lever is being switched OFF: nothing dispenses

        Optional<SetupDetector.Setup> found = SetupDetector.fromLever(level, leverPos);
        if (found.isEmpty()) return; // ordinary lever, not our arrangement
        SetupDetector.Setup setup = found.get();

        RdtConfig cfg = RdtConfig.get();
        BlockKey keyA = BlockKey.of(level, setup.a());
        BlockKey keyB = BlockKey.of(level, setup.b());
        int[] valsA = tracker.snapshot(keyA);
        int[] valsB = tracker.snapshot(keyB);

        if (valsA == null || valsB == null) {
            Feedback.send("Open both containers at least once before pulling the lever (vanilla behaviour used).");
            return;
        }
        if (cfg.requireFullContainers && (!full(valsA) || !full(valsB))) {
            Feedback.send("Both containers must have all 9 slots filled (requireFullContainers is on).");
            return;
        }

        boolean aIsRecent = tracker.seqOf(keyA) > tracker.seqOf(keyB);
        int[] recent = aIsRecent ? valsA : valsB;
        int[] other = aIsRecent ? valsB : valsA;

        OutcomeEngine.Result r = OutcomeEngine.roll(recent, other, cfg.strictGreater, cfg.spread,
                ThreadLocalRandom.current());
        if (r == null) {
            Feedback.send("One of the containers is empty (vanilla behaviour used).");
            return;
        }

        int slotA = aIsRecent ? r.slotRecent() : r.slotOther();
        int slotB = aIsRecent ? r.slotOther() : r.slotRecent();

        if (cfg.applyToIntegratedServer) {
            ForcedSlots.arm(keyA, slotA, cfg.armTtlMillis);
            ForcedSlots.arm(keyB, slotB, cfg.armTtlMillis);
        }

        BlockPos recentPos = aIsRecent ? setup.a() : setup.b();
        Feedback.send("Most recently inspected: " + recentPos.toShortString()
                + " -> slot " + (r.slotRecent() + 1) + " (value " + r.valueRecent() + ")"
                + " | other -> slot " + (r.slotOther() + 1) + " (value " + r.valueOther() + ")"
                + (r.satisfied() ? "" : " [rule impossible with these items]")
                + (cfg.applyToIntegratedServer ? "" : " [preview only]"));
    }

    private static boolean full(int[] values) {
        for (int v : values) if (v <= 0) return false;
        return true;
    }
}
