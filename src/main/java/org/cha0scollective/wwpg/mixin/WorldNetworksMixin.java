package org.cha0scollective.wwpg.mixin;

import net.minecraft.server.level.ServerLevel;
import org.cha0scollective.wwpg.bridge.Bridges;
import org.patryk3211.powergrid.electricity.WorldNetworks;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.level.ChunkPos;
import org.objectweb.asm.Opcodes;

@Mixin(value = WorldNetworks.class, remap = false)
public abstract class WorldNetworksMixin {
    @ModifyExpressionValue(method = "postTick", at = @At(value = "FIELD",
            target = "Lorg/patryk3211/powergrid/electricity/WorldNetworks$CheckChunk;ticks:I",
            opcode = Opcodes.GETFIELD))
    private int wwpg$waitForWireEntities(int age, @Local ChunkPos chunk) {
        var world = ((WorldNetworks) (Object) this).world;
        // The upstream post-increment writes zero when this returns -1. Keep
        // the check queued, with its full ten-tick grace period still available.
        return world instanceof ServerLevel level && !level.areEntitiesLoaded(chunk.toLong()) ? -1 : age;
    }
    @Inject(method = "preTick", at = @At(value = "INVOKE", target = "Lorg/patryk3211/powergrid/electricity/sim/PerformanceCounter;start()V"))
    private void wwpg$begin(CallbackInfo ci) {
        var world = (WorldNetworks) (Object) this;
        if (world.world instanceof ServerLevel level) Bridges.get(level).beginSolving(world);
    }
    @Redirect(method = "preTick", at = @At(value = "INVOKE", target = "Lorg/patryk3211/powergrid/electricity/sim/ElectricalNetwork;singleTick()V"))
    private void wwpg$substep(ElectricalNetwork network) {
        var world = (WorldNetworks) (Object) this;
        if (world.world instanceof ServerLevel level) {
            var bridge = Bridges.get(level);
            bridge.beforeSolve(network);
            network.singleTick();
            bridge.afterSolve(network);
        } else network.singleTick();
    }
    @Inject(method = "preTick", at = @At("RETURN"))
    private void wwpg$results(CallbackInfo ci) {
        var world = (WorldNetworks) (Object) this;
        if (world.world instanceof ServerLevel level) Bridges.get(level).finishSolving();
    }
}
