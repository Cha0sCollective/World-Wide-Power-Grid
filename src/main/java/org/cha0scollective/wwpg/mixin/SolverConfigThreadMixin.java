package org.cha0scollective.wwpg.mixin;

import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicReference;

/** PG's file watcher must not replace a live solver from another thread. */
@Mixin(value=GlobalElectricNetworks.class,remap=false)
abstract class SolverConfigThreadMixin {
    @Unique private static final AtomicReference<net.minecraft.server.MinecraftServer> wwpg$reloadQueued=new AtomicReference<>();
    @Inject(method="configsReloaded",at=@At("HEAD"),cancellable=true)
    private static void wwpg$reloadOnServer(CallbackInfo ci){
        var server=ServerLifecycleHooks.getCurrentServer();
        if(server==null||server.isSameThread())return;
        ci.cancel();
        if(server.isStopped())return;
        // execute() runs inline when the server stops scheduling tasks. tell()
        // always queues, avoiding recursion on the file watcher during shutdown.
        if(wwpg$reloadQueued.getAndSet(server)!=server)server.tell(new net.minecraft.server.TickTask(server.getTickCount(),()->{
            wwpg$reloadQueued.compareAndSet(server,null);
            if(!server.isStopped()&&ServerLifecycleHooks.getCurrentServer()==server)GlobalElectricNetworks.configsReloaded();
        }));
    }
}
