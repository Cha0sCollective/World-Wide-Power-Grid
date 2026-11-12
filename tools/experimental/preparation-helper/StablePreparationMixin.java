package org.cha0scollective.wwpg.benchmark.mixin;

import java.util.Map;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Gives the benchmark plugin an explicit, flag-gated target for section probes. */
@Mixin(targets = "org.cha0scollective.wwpg.bridge.WorldBridge$StableDescription", remap = false)
public abstract class StablePreparationMixin {
    @Shadow @Final private Map<?, ?> anchors;
}
