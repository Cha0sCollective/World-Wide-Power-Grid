package org.cha0scollective.wwpg.mixin;

import org.patryk3211.powergrid.electricity.sim.solver.NativeMNA;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.nio.ByteBuffer;

/** Access to PG's current scalar-state buffer and allocated row count. */
@Mixin(value = NativeMNA.class, remap = false)
public interface PgNativeStateAccessor {
    @Accessor("stateBuffer") ByteBuffer wwpg$stateBuffer();
    @Accessor("size") int wwpg$stateSize();
}
