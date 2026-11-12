package org.cha0scollective.wwpg.clientbenchmark.mixin;

import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SectionOcclusionGraph.class)
public interface OcclusionAccess {
    @Accessor("needsFullUpdate") boolean benchmark$needsFullUpdate();
    @Accessor("fullUpdateTask") Future<?> benchmark$fullUpdateTask();
    @Accessor("needsFrustumUpdate") AtomicBoolean benchmark$needsFrustumUpdate();
}
