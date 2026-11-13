package org.cha0scollective.wwpg.mixin;
import com.george_vi.electroenergetics.content.electronic_components.inductor.InductorDevice;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import net.minecraft.nbt.CompoundTag;
import org.cha0scollective.wwpg.compat.cee.*;
import org.cha0scollective.wwpg.WorldWidePowerGrid;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Preserve direction and L*I^2/2 across the native current-convention change. */
@Mixin(value=InductorDevice.class,remap=false)
public abstract class CeeInductorHistoryMixin {
    @Shadow public double lastCurrent;
    @Unique private boolean wwpg$ambiguousCurrent;
    @Unique private boolean wwpg$currentPrepared;
    @Inject(method="read",at=@At("TAIL"))
    private void wwpg$readCurrent(CompoundTag tag,CallbackInfo ci){
        var device=(InductorDevice)(Object)this;
        var reading=CeeSavedCurrent.read(tag,CeeSaveConventions.source(device.level),CeeSaveConventions.current(),System.getProperty("wwpg.cee.unmarkedSource",""));
        lastCurrent=reading.current();wwpg$ambiguousCurrent=reading.ambiguous();wwpg$currentPrepared=false;
        if(wwpg$ambiguousCurrent)WorldWidePowerGrid.LOGGER.warn("CEE inductor at {} has charged history without a known source convention; retained unchanged and held unavailable. Confirm its origin before setting wwpg.cee.unmarkedSource=1.1 or 1.2",device.pos);
    }
    @Inject(method="write",at=@At("TAIL"))
    private void wwpg$markCurrent(CompoundTag tag,CallbackInfo ci){CeeSavedCurrent.mark(tag,CeeSaveConventions.current(),wwpg$ambiguousCurrent);}
    @Inject(method="postTick",at=@At("HEAD"),cancellable=true)
    private void wwpg$onlyPreparedHistory(com.george_vi.electroenergetics.simulation.SimulationResults results,CallbackInfo ci){
        if(wwpg$ambiguousCurrent||!wwpg$currentPrepared)ci.cancel();
    }
    @Inject(method="preTick",at=@At("HEAD"),cancellable=true)
    private void wwpg$holdAmbiguous(BridgeCollector bridges,CallbackInfo ci){
        if(!wwpg$ambiguousCurrent){wwpg$currentPrepared=true;return;}
        var device=(InductorDevice)(Object)this;
        // Use the ordinary checked isolation path. Do not throw from native read(),
        // whose loader would remove a device, or overwrite its charged history.
        bridges.builder(device.pos).node(2).connect(0,2,ElectricalProperties.resistor(Double.NaN)).resistor(1,2,.1);
        ci.cancel();
    }
}
