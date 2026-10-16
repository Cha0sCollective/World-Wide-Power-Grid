package org.cha0scollective.wwpg.mixin;

import net.minecraft.server.level.ServerLevel;
import org.cha0scollective.wwpg.wiring.Terminals;
import org.patryk3211.powergrid.electricity.base.ElectricBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.AbstractElectricWire;
import org.patryk3211.powergrid.electricity.sim.node.INode;
import org.patryk3211.powergrid.electricity.sim.node.OwnedFloatingNode;
import java.util.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ElectricBehaviour.class, remap = false)
public abstract class PgBehaviourMixin {
    @Shadow private boolean paused;
    @Shadow @Final private List<INode> internalNodes;
    @Shadow @Final private List<OwnedFloatingNode> externalNodes;
    @Shadow @Final private List<AbstractElectricWire> internalWires;

    @WrapMethod(method = "pause")
    private void wwpg$rediscoverDetachedInternals(Operation<Void> original) {
        var behaviour = (ElectricBehaviour) (Object) this;
        if (paused || !behaviour.hasInternals() || !(behaviour.getWorld() instanceof ServerLevel level)) {
            original.call(); return;
        }
        // Unload retains external wire junctions, but removes the connections
        // inside the device. Capture owners before removal clears them.
        Set<ElectricalNetwork> owners = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var node : externalNodes) if (node.getNetwork() != null) owners.add(node.getNetwork());
        for (var node : internalNodes) if (node.getNetwork() != null) owners.add(node.getNetwork());
        for (var wire : internalWires) if (wire.getNetwork() != null) owners.add(wire.getNetwork());
        // Shutdown can retire all owners before the behaviours unload. Never
        // recreate SavedData/world networks merely to queue an obsolete owner.
        var world = owners.isEmpty() ? null : GlobalElectricNetworks.getWorldNetworks((net.minecraft.world.level.LevelAccessor) level);
        try { original.call(); }
        finally { if (world != null) owners.forEach(world::scheduleIslandDiscovery); }
    }
    @Inject(method = "remove", at = @At("HEAD"))
    private void wwpg$removeCeeWires(CallbackInfo ci) {
        var behaviour = (ElectricBehaviour) (Object) this;
        if (behaviour.getWorld() instanceof ServerLevel level)
            Terminals.removePgNodes(level, behaviour.getPos());
    }
    @Inject(method = "rebuildCircuit", at = @At("RETURN"))
    private void wwpg$refreshCeeTerminals(boolean external, CallbackInfo ci) {
        var behaviour = (ElectricBehaviour) (Object) this;
        if (external && behaviour.getWorld() instanceof ServerLevel level)
            Terminals.refreshPgNodes(level, behaviour.getPos());
    }
}
