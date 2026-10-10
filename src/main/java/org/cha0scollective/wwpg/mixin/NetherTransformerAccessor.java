package org.cha0scollective.wwpg.mixin;

import org.patryk3211.powergrid.electricity.transformer.NetherTransformerBlockEntity;
import org.patryk3211.powergrid.electricity.sim.special.SplitTransformerControllerWire;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = NetherTransformerBlockEntity.class, remap = false)
public interface NetherTransformerAccessor {
    @Accessor("wire") SplitTransformerControllerWire wwpg$controller();
}
