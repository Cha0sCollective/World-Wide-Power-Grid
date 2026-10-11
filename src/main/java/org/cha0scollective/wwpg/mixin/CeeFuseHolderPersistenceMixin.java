package org.cha0scollective.wwpg.mixin;

import com.george_vi.electroenergetics.content.fuse.FuseHolderBlockEntity;
import com.george_vi.electroenergetics.content.fuse.fuse_held.FuseHoldable;
import net.createmod.catnip.data.Pair;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

/** Native sendData synchronizes clients but does not schedule a chunk save. */
@Mixin(value=FuseHolderBlockEntity.class,remap=false)
public abstract class CeeFuseHolderPersistenceMixin {
    @Shadow Pair<FuseHoldable,CompoundTag> firstFuse;
    @Shadow Pair<FuseHoldable,CompoundTag> secondFuse;
    @Unique private CompoundTag wwpg$lastFirst,wwpg$lastSecond;

    @Inject(method="read",at=@At("TAIL"))
    private void wwpg$rememberLoadedState(CompoundTag tag,net.minecraft.core.HolderLookup.Provider registries,
                                        boolean clientPacket,CallbackInfo ci) {
        wwpg$remember();
    }

    @Inject(method="updateFuses",at=@At("TAIL"))
    private void wwpg$savePlayerChanges(CallbackInfo ci) {
        ((FuseHolderBlockEntity)(Object)this).setChanged();
        wwpg$remember();
    }

    @Inject(method="updateFusesFromDevice",at=@At("TAIL"))
    private void wwpg$saveCommittedChanges(CompoundTag firstData,CompoundTag secondData,CallbackInfo ci) {
        if (!Objects.equals(wwpg$lastFirst,wwpg$data(firstFuse))
                || !Objects.equals(wwpg$lastSecond,wwpg$data(secondFuse))) {
            ((FuseHolderBlockEntity)(Object)this).setChanged();
            wwpg$remember();
        }
    }

    @Unique private void wwpg$remember() {
        var first=wwpg$data(firstFuse);var second=wwpg$data(secondFuse);
        wwpg$lastFirst=first==null?null:first.copy();
        wwpg$lastSecond=second==null?null:second.copy();
    }
    @Unique private static CompoundTag wwpg$data(Pair<FuseHoldable,CompoundTag> insert) {
        return insert==null?null:insert.getSecond();
    }
}
