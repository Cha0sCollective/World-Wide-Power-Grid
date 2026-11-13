package org.cha0scollective.wwpg.mixin;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.*;
import net.neoforged.neoforge.common.CommonHooks;
import org.cha0scollective.wwpg.compat.cee.CeeSaveConventions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=CommonHooks.class,remap=false)
public abstract class CeeWorldHistoryMixin {
    @Inject(method="readAdditionalLevelSaveData",at=@At("HEAD"))
    private static void wwpg$origin(CompoundTag root,LevelStorageSource.LevelDirectory directory,CallbackInfo ci){CeeSaveConventions.loaded(root,directory.path());}
    @Inject(method="writeAdditionalLevelSaveData",at=@At("TAIL"))
    private static void wwpg$preserveOrigin(WorldData data,CompoundTag root,CallbackInfo ci){CeeSaveConventions.saving(root);}
}
