package org.cha0scollective.wwpg.mixin;

import net.minecraft.nbt.CompoundTag;
import org.cha0scollective.wwpg.experimental.WireTreeBatch;
import org.patryk3211.powergrid.electricity.sim.special.TransmissionLine;
import org.patryk3211.powergrid.electricity.wire.BaseWireEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

@Mixin(value = BaseWireEntity.class, remap = false)
public abstract class PgWireRestoreScopeMixin implements WireTreeBatch.Reattachment {
    @Unique private TransmissionLine wwpg$reclaimedLine;

    @Override public void wwpg$reclaimedInterior(TransmissionLine line) { wwpg$reclaimedLine = line; }
    @Override public TransmissionLine wwpg$takeReclaimedInterior() {
        var line = wwpg$reclaimedLine;
        wwpg$reclaimedLine = null;
        return line;
    }
    @WrapMethod(method = "readAdditionalSaveData")
    private void wwpg$readingSavedWire(CompoundTag tag, Operation<Void> original) {
        WireTreeBatch.reading((BaseWireEntity) (Object) this, () -> original.call(tag));
    }
}
