package org.cha0scollective.wwpg.mixin;

import com.george_vi.electroenergetics.foundation.nodes.AttachedNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "com.george_vi.electroenergetics.simulation.infrastructure.WireElectrocutionModule$ElectrocutionEntry", remap = false)
public interface ElectrocutionEntryAccessor {
    @Accessor("centralNode") AttachedNode wwpg$centralNode();
}
