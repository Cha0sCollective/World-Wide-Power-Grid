package org.cha0scollective.wwpg.mixin;

import net.minecraft.server.level.ServerLevel;
import org.cha0scollective.wwpg.wiring.RemovedWires;
import org.patryk3211.powergrid.electricity.wire.WireEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = WireEntity.class, remap = false)
public abstract class PgWireRemovalMixin {
    @Inject(method = "makeWire", at = @At("HEAD"), cancellable = true)
    private void wwpg$discardDeletedWire(CallbackInfo ci) {
        var wire = (WireEntity) (Object) this;
        if (wire.level() instanceof ServerLevel level) {
            // Minecraft deserializes saved entities on its IO worker. Native PG
            // calls makeWire there from setEndpoint; retain its exact endpoints
            // and use its first server tick to bind them, before reading SavedData
            // or changing the shared electrical graph.
            if (!level.getServer().isSameThread()) {
                wire.redeferEndpoints();
                ci.cancel();
                return;
            }
            if (RemovedWires.get(level).contains(wire.getUUID())) {
                wire.kill(); ci.cancel();return;
            }
            // EntityStorage.flush can drain deserialization on the server
            // thread. Read-only geometry can exist before that inbox is ready;
            // native wire binding must still wait for either API owner.
            if(wire.getEndpoint1() instanceof org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint first
                    &&org.cha0scollective.wwpg.extension.ApiTerminals.blockedWireEndpoint(level,first.getPos())
                    ||wire.getEndpoint2() instanceof org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint second
                    &&org.cha0scollective.wwpg.extension.ApiTerminals.blockedWireEndpoint(level,second.getPos())) {
                wire.redeferEndpoints();ci.cancel();
            }
        }
    }

}
