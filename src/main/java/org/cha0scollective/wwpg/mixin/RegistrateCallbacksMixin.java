package org.cha0scollective.wwpg.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.tterrag.registrate.AbstractRegistrate;
import com.tterrag.registrate.builders.Builder;
import com.tterrag.registrate.util.entry.RegistryEntry;
import com.tterrag.registrate.util.nullness.NonNullConsumer;
import com.tterrag.registrate.util.nullness.NonNullFunction;
import com.tterrag.registrate.util.nullness.NonNullSupplier;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Mixin;

/** Keep pending callback updates and registration publication atomic per instance. */
@Mixin(value = AbstractRegistrate.class, remap = false)
public abstract class RegistrateCallbacksMixin {
    @WrapMethod(method = "addRegisterCallback(Ljava/lang/String;Lnet/minecraft/resources/ResourceKey;Lcom/tterrag/registrate/util/nullness/NonNullConsumer;)Lcom/tterrag/registrate/AbstractRegistrate;")
    private AbstractRegistrate<?> wwpg$addCallback(String name, ResourceKey<?> registry, NonNullConsumer<?> callback,
                                                   Operation<AbstractRegistrate<?>> original) {
        synchronized (this) { return original.call(name, registry, callback); }
    }

    @WrapMethod(method = "accept")
    private RegistryEntry<?, ?> wwpg$publishRegistration(String name, ResourceKey<?> registry, Builder<?, ?, ?, ?> builder,
                                                         NonNullSupplier<?> creator, NonNullFunction<?, ?> entryFactory,
                                                         Operation<RegistryEntry<?, ?>> original) {
        synchronized (this) { return original.call(name, registry, builder, creator, entryFactory); }
    }
}
