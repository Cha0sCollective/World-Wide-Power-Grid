package org.cha0scollective.wwpg.mixin;

import com.george_vi.electroenergetics.simulation.simulator.SimulationTicker;
import org.cha0scollective.wwpg.bridge.Bridges;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SimulationTicker.class, remap = false)
public abstract class SimulationTickerMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void wwpg$prepare(CallbackInfo ci) {
        var ticker = (SimulationTicker) (Object) this;
        Bridges.get(ticker.level).prepare(ticker);
        ci.cancel();
    }
    @Inject(method = "endTick", at = @At("HEAD"), cancellable = true)
    private void wwpg$commit(CallbackInfo ci) {
        var ticker = (SimulationTicker) (Object) this;
        Bridges.get(ticker.level).commit(ticker);
        ci.cancel();
    }
    @Inject(method = {"runServer", "stopServer"}, at = @At("HEAD"), cancellable = true)
    private static void wwpg$singleSolver(CallbackInfo ci) { ci.cancel(); }
}
