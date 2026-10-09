package org.cha0scollective.wwpg.mixin;

import com.google.common.collect.Multimap;
import com.tterrag.registrate.AbstractRegistrate;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.cha0scollective.wwpg.WorldWidePowerGrid;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Temporary diagnostic: only runs after Registrate detects unused callbacks. */
@Mixin(value = AbstractRegistrate.class, remap = false)
public abstract class RegistrateStartupDiagnosticMixin {
    @Shadow @Final private Multimap<?, ?> registerCallbacks;
    @Shadow public abstract String getModid();

    @Inject(method = "onRegister", at = @At(value = "INVOKE", target = "Lcom/google/common/collect/Multimap;asMap()Ljava/util/Map;"))
    private void wwpg$unusedCallbacks(RegisterEvent event, CallbackInfo ci) {
        WorldWidePowerGrid.LOGGER.error("WWPG registration diagnostic: mod={}, registry={}, callback size={}, keys={}, entries={}",
                getModid(), event.getRegistryKey(), registerCallbacks.size(), registerCallbacks.keySet(), registerCallbacks.asMap());
    }
}
