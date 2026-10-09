package org.cha0scollective.wwpg.mixin;

import com.george_vi.electroenergetics.content.electrical_panel.attachments.TriPolarEnergyMeterAttachment;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.Node;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = TriPolarEnergyMeterAttachment.class, remap = false)
public abstract class TriPolarMeterResultsMixin {
    // CEE 1.1.3 uses attachment-local ordinals as physical panel terminal IDs.
    @Redirect(method = "postTick", require = 5, at = @At(value = "INVOKE",
            target = "Lcom/george_vi/electroenergetics/simulation/SimulationResults;getVoltages(Lcom/george_vi/electroenergetics/foundation/nodes/Node;[D)[D"))
    private double[] wwpg$attachmentHistory(SimulationResults results, Node node, double[] history) {
        var attachment = (TriPolarEnergyMeterAttachment) (Object) this;
        return results.getVoltages(attachment.nodes[((InWorldNode) node).id()], history);
    }

    // Match the single-phase meter's consumed-energy sign on both orientations.
    @ModifyVariable(method = "postTick", at = @At("STORE"), name = "thisPower", require = 2)
    private double wwpg$consumedPower(double power) {
        return ((PanelMeterAccessor) this).wwpg$isInverted() ? power : -power;
    }
}
