package org.cha0scollective.wwpg.gametest;

import com.electronwill.nightconfig.core.file.FileWatcher;
import java.nio.file.*;
import java.util.concurrent.*;

/** Freeze experiment inputs, draining queued reloads before any timing window. */
final class ReferenceConfiguration {
    static void freeze(net.minecraft.server.level.ServerLevel level) {
        try {
            var watcher=FileWatcher.defaultInstance();
            var config=net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get();
            int count=0;
            for(var directory:new Path[]{config,level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("serverconfig")}) {
                if(!Files.isDirectory(directory))continue;
                try(var paths=Files.list(directory)) {
                    for(var path:paths.filter(p->p.getFileName().toString().endsWith(".toml")).toList()) {
                        watcher.removeWatchFuture(path).get(5,TimeUnit.SECONDS);count++;
                    }
                }
            }
            // Removal stops new callbacks but existing debounced callbacks can
            // still be queued. A later sentinel on the same filesystem runs on
            // that watcher's single callback executor, after those callbacks.
            // A fresh directory avoids reusing a just-cancelled config watch key.
            var barrierDirectory=Files.createTempDirectory(config,"wwpg-reference-barrier-");
            var sentinel=Files.createFile(barrierDirectory.resolve("drain.tmp"));
            var drained=new CountDownLatch(1);
            try {
                watcher.addWatchFuture(sentinel,drained::countDown).get(5,TimeUnit.SECONDS);
                Files.writeString(sentinel,"drain reference configuration callbacks");
                if(!drained.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Reference configuration reload barrier timed out");
            } finally {
                watcher.removeWatchFuture(sentinel).get(5,TimeUnit.SECONDS);
                Files.delete(sentinel);
                Files.delete(barrierDirectory);
            }
            org.cha0scollective.wwpg.WorldWidePowerGrid.LOGGER.info("WWPG_REFERENCE_CONFIG_FROZEN: files={}; pendingReloadsDrained=true",count);
        } catch(InterruptedException error) {
            Thread.currentThread().interrupt();throw new IllegalStateException("Interrupted reference configuration barrier",error);
        } catch(Exception error) {throw new IllegalStateException("Could not freeze reference configuration",error);}
    }
}
