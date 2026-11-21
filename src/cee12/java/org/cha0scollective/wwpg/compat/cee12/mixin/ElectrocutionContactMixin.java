package org.cha0scollective.wwpg.compat.cee12.mixin;

import com.george_vi.electroenergetics.foundation.nodes.AttachedNode;
import com.george_vi.electroenergetics.foundation.nodes.AttachedNodeGenerator;
import com.george_vi.electroenergetics.simulation.CircuitBuilder;
import com.george_vi.electroenergetics.simulation.infrastructure.WireElectrocutionModule;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import java.util.Map;

/** Match CEE's persistent ground/cut identities with a persistent body identity for the same contact. */
@Mixin(value=WireElectrocutionModule.class,remap=false)
abstract class ElectrocutionContactMixin {
    @Shadow @Final private Map<Entity,?> electrocutions;

    @WrapOperation(method="computeElectrocutionFor",at=@At(value="INVOKE",ordinal=0,
            target="Lcom/george_vi/electroenergetics/foundation/nodes/AttachedNodeGenerator;newNode()Lcom/george_vi/electroenergetics/foundation/nodes/AttachedNode;"),require=1)
    private AttachedNode wwpg$retainContactBody(AttachedNodeGenerator generator,Operation<AttachedNode> original,
                                               Entity entity,CircuitBuilder builder) {
        var previous=electrocutions.get(entity);
        return previous==null?original.call(generator):((ElectrocutionEntryAccessor)previous).wwpg$centralNode();
    }
}
