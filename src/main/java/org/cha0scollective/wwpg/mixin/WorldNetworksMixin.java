package org.cha0scollective.wwpg.mixin;

import net.minecraft.server.level.ServerLevel;
import org.cha0scollective.wwpg.bridge.Bridges;
import org.cha0scollective.wwpg.bridge.ServerElectricalSchedule;
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
    @Inject(method = "runIslandDiscoveryFor", at = @At("HEAD"), cancellable = true)
    private void wwpg$indexedIslands(ElectricalNetwork network, CallbackInfo ci) {
        var world = (WorldNetworks) (Object) this;
        if (org.cha0scollective.wwpg.bridge.RestorationCoordinator.defer(world, network))
            org.cha0scollective.wwpg.bridge.RestorationCoordinator.holdDiscovery(world, network);
        else org.cha0scollective.wwpg.bridge.IndexedIslandDiscovery.run(world, network);
        ci.cancel();
    }
    @Redirect(method = "preTick", at = @At(value = "INVOKE", target = "Lorg/patryk3211/powergrid/electricity/sim/ElectricalNetwork;prepare(I)V"))
    private void wwpg$batchPreparation(ElectricalNetwork network, int steps) {
        if (!org.cha0scollective.wwpg.bridge.RestorationCoordinator.defer((WorldNetworks) (Object) this, network)) network.prepare(steps);
    }
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
        if (ServerElectricalSchedule.preparing()) return;
        var world = (WorldNetworks) (Object) this;
        if (world.world instanceof ServerLevel level) {
            ServerElectricalSchedule.solve(level, network);
        } else network.singleTick();
    }
    @Redirect(method = "preTick", at = @At(value = "INVOKE", target = "Lorg/patryk3211/powergrid/electricity/sim/PerformanceCounter;end()V"))
    private void wwpg$finishPerformance(org.patryk3211.powergrid.electricity.sim.PerformanceCounter counter) {
        if (!ServerElectricalSchedule.preparing()) counter.end();
    }
    @Inject(method = "preTick", at = @At("RETURN"))
    private void wwpg$results(CallbackInfo ci) {
        var world = (WorldNetworks) (Object) this;
        org.cha0scollective.wwpg.bridge.RestorationCoordinator.retainDiscovery(world);
        if (!ServerElectricalSchedule.preparing() && world.world instanceof ServerLevel level)
            Bridges.get(level).finishSolving();
    }
}
