package org.cha0scollective.wwpg.acceptance;

import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import net.neoforged.fml.ModList;
import org.cha0scollective.wwpg.WorldWidePowerGrid;

/** Record the actual mod file selected by NeoForge, rather than a requested path. */
public final class RuntimeIdentity {
    public static void verifyWorkshopSpawn(net.minecraft.server.level.ServerPlayer player) {
        var spawn=org.cha0scollective.wwpg.gametest.ExtensionApiWorldGameTests.SPAWN;
        if(Math.abs(player.getX()-spawn.getX()-.5)>.51 || Math.abs(player.getZ()-spawn.getZ()-.5)>.51
                || player.getY()<spawn.getY()-.1 || player.getY()>spawn.getY()+1
                || player.serverLevel().getBlockState(spawn.below()).isAir())
            throw new IllegalStateException("Fresh Workshop player did not start on its documented platform: "+player.position());
        WorldWidePowerGrid.LOGGER.info("CI_WORKSHOP_INITIAL_SPAWN_PASSED: actual={}; platform={}; before helper teleport",player.position(),spawn);
    }
    public static void record() {
        try {
            for(var entry:java.util.Map.of("wwpg","runtime","wwpg_tests","gametest","wwpg_api_example","example","wwpg_api_upstream","upstream_example").entrySet()) {
                var file=ModList.get().getModFileById(entry.getKey());if(file==null)continue;
                var hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file.getFile().getFilePath())));
                WorldWidePowerGrid.LOGGER.info("CI_LOADED_ARTIFACT: {} {}",entry.getValue(),hash);
            }
        }catch(Exception error){throw new IllegalStateException("Cannot identify actual tested mod file",error);}
    }
}
