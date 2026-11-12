package org.cha0scollective.wwpg.clientbenchmark.mixin;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Observation only; never changes rendering or the world under measurement. */
@Mixin(LevelRenderer.class)
public interface RendererAccess {
    @Accessor("visibleSections") ObjectArrayList<SectionRenderDispatcher.RenderSection> benchmark$visible();
    @Accessor("sectionOcclusionGraph") SectionOcclusionGraph benchmark$occlusion();
}
