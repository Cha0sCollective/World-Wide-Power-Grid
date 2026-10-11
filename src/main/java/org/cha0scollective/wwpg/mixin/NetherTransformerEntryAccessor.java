package org.cha0scollective.wwpg.mixin;

import org.patryk3211.powergrid.electricity.sim.special.SplitTransformerControllerWire;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "org.patryk3211.powergrid.electricity.transformer.NetherTransformerBlockEntity$TransformerEntry", remap = false)
public interface NetherTransformerEntryAccessor {
    @Accessor("wire1") SplitTransformerControllerWire wwpg$primary();
    @Accessor("wire2") SplitTransformerControllerWire wwpg$secondary();
    @Invoker("setWire") void wwpg$setWire(boolean secondary, SplitTransformerControllerWire wire);
    @Invoker("isEmpty") boolean wwpg$isEmpty();
}
