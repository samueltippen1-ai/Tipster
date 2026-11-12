package dev.randomised.dispensertest.mixin;

import dev.randomised.dispensertest.BlockKey;
import dev.randomised.dispensertest.ForcedSlots;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The ONLY server-side touchpoint. Vanilla picks the dispensed slot in getRandomSlot(); if (and only if)
 * the client side armed a slot for this exact block a moment ago, that slot is returned instead.
 * Every other dispenser/dropper, and every call without an armed entry, runs vanilla code untouched.
 * The mod is client-environment only, so this never loads on a dedicated server.
 */
@Mixin(DispenserBlockEntity.class)
public abstract class DispenserBlockEntityMixin {

    @Inject(method = "getRandomSlot", at = @At("HEAD"), cancellable = true, require = 0)
    private void rdt$forceSlot(RandomSource random, CallbackInfoReturnable<Integer> cir) {
        DispenserBlockEntity self = (DispenserBlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level == null || level.isClientSide()) return;

        int slot = ForcedSlots.consume(BlockKey.of(level, self.getBlockPos()));
        if (slot >= 0 && slot < self.getContainerSize() && !self.getItem(slot).isEmpty()) {
            cir.setReturnValue(slot);
        }
    }
}
