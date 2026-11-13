package org.cha0scollective.wwpg.compat.cee12.mixin;
import com.george_vi.electroenergetics.content.electronic_components.capacitor.CapacitorDevice;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** CEE's new integrator needs current history as well as saved voltage. */
@Mixin(value=CapacitorDevice.class,remap=false)
public abstract class CapacitorSavedHistoryMixin {
    @Unique private boolean wwpg$preparedHistory;
    @Inject(method="read",at=@At("TAIL"))
    private void wwpg$readCurrent(CompoundTag tag,CallbackInfo ci){wwpg$preparedHistory=false;((CapacitorDevice)(Object)this).properties.lastCurrent=tag.getDouble("WWPGCapacitorLastCurrent");}
    @Inject(method="preTick",at=@At("HEAD"))
    private void wwpg$prepared(com.george_vi.electroenergetics.simulation.BridgeCollector collector,CallbackInfo ci){wwpg$preparedHistory=true;}
    @Inject(method="postTick",at=@At("HEAD"),cancellable=true)
    private void wwpg$onlyPrepared(com.george_vi.electroenergetics.simulation.SimulationResults results,CallbackInfo ci){if(!wwpg$preparedHistory)ci.cancel();}
    @Inject(method="write",at=@At("TAIL"))
    private void wwpg$saveCurrent(CompoundTag tag,CallbackInfo ci){tag.putDouble("WWPGCapacitorLastCurrent",((CapacitorDevice)(Object)this).properties.lastCurrent);}
}
