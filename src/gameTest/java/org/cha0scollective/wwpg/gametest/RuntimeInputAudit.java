package org.cha0scollective.wwpg.gametest;

import java.nio.file.*;
import java.util.*;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.cha0scollective.wwpg.WorldWidePowerGrid;

/** Observe and verify actual loaded files, rather than the dependency resolver's choices. */
final class RuntimeInputAudit {
    static void started(ServerStartedEvent event) {
        if(!Boolean.getBoolean("wwpg.test.packaged")) return;
        try(var input=RuntimeInputAudit.class.getResourceAsStream("/data/wwpg_tests/runtime-inputs.json")) {
            if(input==null) throw new IllegalStateException("Missing frozen runtime input lock");
            var lock=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            var loaded=new LinkedHashMap<String,Map<String,Object>>();
            for(var mod:net.neoforged.fml.ModList.get().getMods()) {
                var path=mod.getOwningFile().getFile().getFilePath();
                var item=new LinkedHashMap<String,Object>();item.put("version",mod.getVersion().toString());item.put("path",path.toString());
                if(Files.isRegularFile(path)) item.put("sha256",HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))));
                loaded.put(mod.getModId(),item);
            }
            Files.writeString(Path.of("loaded-runtime-inputs.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(loaded));
            for(var entry:lock.entrySet()) {
                var expected=entry.getValue().getAsJsonObject();String id=entry.getKey();
                boolean required=!expected.get("optional").getAsBoolean();
                if(id.equals("computercraft")&&Boolean.getBoolean("wwpg.test.legacyComputerRuntime")) expected=expected.getAsJsonObject("legacy");
                if(id.equals("pinout")||id.equals("computercraft")) required="PINOUT".equals(System.getProperty("wwpg.test.profile","BASE"));
                if(id.equals("moregrid")) required=Set.of("fused-latch","relay-heater","quiet-heater").contains(System.getProperty("wwpg.test.circuit",""));
                var actual=loaded.get(id);
                if(actual==null) {if(required)throw new IllegalStateException("Required runtime input absent: "+id);continue;}
                if(!expected.get("sha256").getAsString().equals(actual.get("sha256"))) throw new IllegalStateException("Loaded runtime jar differs from lock: "+id+" "+actual);
            }
            String neo=System.getProperty("wwpg.test.expectedNeoForge","");
            if(!neo.isEmpty()&&!neo.equals(loaded.get("neoforge").get("version"))) throw new IllegalStateException("Loaded NeoForge differs: "+loaded.get("neoforge"));
            WorldWidePowerGrid.LOGGER.info("WWPG_RUNTIME_INPUTS_VERIFIED: {}",new com.google.gson.Gson().toJson(loaded));
        } catch(Exception error) {throw new IllegalStateException("Runtime input audit failed",error);}
    }
}
