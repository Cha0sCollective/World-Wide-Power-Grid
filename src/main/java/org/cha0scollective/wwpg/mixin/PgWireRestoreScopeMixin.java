package org.cha0scollective.wwpg.mixin;

import net.minecraft.nbt.CompoundTag;
import org.cha0scollective.wwpg.experimental.WireTreeBatch;
import org.patryk3211.powergrid.electricity.wire.BaseWireEntity;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

@Mixin(value = BaseWireEntity.class, remap = false)
public abstract class PgWireRestoreScopeMixin {
    @WrapMethod(method = "readAdditionalSaveData")
    private void wwpg$readingSavedWire(CompoundTag tag, Operation<Void> original) {
        WireTreeBatch.reading((BaseWireEntity) (Object) this, () -> original.call(tag));
    }
}
