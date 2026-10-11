package org.cha0scollective.wwpg.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.Level;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.patryk3211.powergrid.circuits.editor.CircuitDesignTableBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Minecraft supplies the correct lookup even before the entity has a world. */
@Mixin(value = CircuitDesignTableBlockEntity.class, remap = false)
public abstract class DesignTableRegistriesMixin {
    @Shadow @Final private Container inventory;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void wwpg$inventorySchedulesSave(CallbackInfo ci) {
        ((SimpleContainer) inventory).addListener(ignored -> ((BlockEntity) (Object) this).setChanged());
    }


    @Redirect(method = {"read", "write"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;registryAccess()Lnet/minecraft/core/RegistryAccess;"))
    private RegistryAccess wwpg$avoidUnassignedWorld(Level unused) {
        // The argument is immediately replaced below with the supplied lookup.
        // Redirecting this call also avoids invoking it on a null Level.
        return RegistryAccess.EMPTY;
    }

    @ModifyArg(method = "read", at = @At(value = "INVOKE",
            target = "Lorg/patryk3211/powergrid/circuits/schematic/CircuitSchematic;deserializeNbt(Lnet/minecraft/core/HolderLookup$Provider;Lnet/minecraft/nbt/CompoundTag;)V"), index = 0)
    private HolderLookup.Provider wwpg$readLookup(HolderLookup.Provider unused,
                                                 @Local(argsOnly = true) HolderLookup.Provider registries) {
        return registries;
    }

    @ModifyArg(method = "write", at = @At(value = "INVOKE",
            target = "Lorg/patryk3211/powergrid/circuits/schematic/CircuitSchematic;serializeNbt(Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/nbt/CompoundTag;"), index = 0)
    private HolderLookup.Provider wwpg$writeLookup(HolderLookup.Provider unused,
                                                  @Local(argsOnly = true) HolderLookup.Provider registries) {
        return registries;
    }

    @Inject(method = "write", at = @At("TAIL"))
    private void wwpg$saveInventory(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket,
                                    CallbackInfo ci) {
        var items = NonNullList.withSize(inventory.getContainerSize(), ItemStack.EMPTY);
        for (int slot = 0; slot < items.size(); ++slot) items.set(slot, inventory.getItem(slot));
        var saved = new CompoundTag();
        saved.putInt("Version", 1);
        ContainerHelper.saveAllItems(saved, items, registries);
        tag.put("WWPGInventory", saved);
    }

    @Inject(method = "read", at = @At("TAIL"))
    private void wwpg$loadInventory(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket,
                                    CallbackInfo ci) {
        if (!tag.contains("WWPGInventory")) return; // Legacy tables have no saved inventory.
        var saved = tag.getCompound("WWPGInventory");
        if (saved.getInt("Version") != 1) return;
        var items = NonNullList.withSize(inventory.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(saved, items, registries);
        for (int slot = 0; slot < items.size(); ++slot) inventory.setItem(slot, items.get(slot));
    }
}
