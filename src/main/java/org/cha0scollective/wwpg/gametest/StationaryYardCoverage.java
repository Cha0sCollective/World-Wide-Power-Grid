package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEERegistries;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlockEntity;
import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import org.patryk3211.powergrid.circuits.circuitboard.CircuitBoardBlockEntity;
import org.patryk3211.powergrid.circuits.components.ComponentRegistry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Current yard evidence; stored parts are never counted as working exhibits. */
final class StationaryYardCoverage {
    record Visit(String code,String name,BlockPos origin,List<String> instructions) {}
    static void write(GameTestHelper h,List<Visit> legacy,StationaryYardStations additional) {
        var visits=new ArrayList<>(legacy);
        visits.add(new Visit("B26","Routing board: via, label and pins",new BlockPos(80,64,140),
                List.of("10 V / 10 mA across front and back traces","Use the meters on the two external pins","Restore the supply to 10 V")));
        visits.add(new Visit("F01","Panel and board factory",new BlockPos(5,64,4),
                List.of("Factory enable changes the RUN/OFF lamps after a short capacitor delay","Heater has a separate 600 V feed; pump moves water between tanks","Restore Factory enable and refill the input tank")));
        for(var station:additional.stations)visits.add(new Visit(station.code(),station.name(),station.origin(),station.instructions()));
        var rows=new JsonArray();
        for(var visit:visits) {
            var row=new JsonObject();row.addProperty("code",visit.code());row.addProperty("name",visit.name());
            row.addProperty("dimension","minecraft:overworld");row.addProperty("x",visit.origin().getX());row.addProperty("y",visit.origin().getY());row.addProperty("z",visit.origin().getZ());
            row.add("instructions",new Gson().toJsonTree(visit.instructions()));row.addProperty("checked",true);
            var observed=new TreeSet<String>();
            int width=visit.code().startsWith("S")?25:visit.code().equals("F01")?18:15;
            int depth=visit.code().startsWith("S")?17:visit.code().equals("F01")?30:13;
            for(int x=0;x<width;x++)for(int z=0;z<depth;z++)for(int y=-2;y<=6;y++) {
                var pos=visit.origin().offset(x,y,z);
                var chunk=h.getLevel().getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
                if(chunk==null)continue;
                String id=BuiltInRegistries.BLOCK.getKey(chunk.getBlockState(pos).getBlock()).toString();
                if(id.startsWith("powergrid:")||id.startsWith("electroenergetics:")||id.startsWith("pinout:"))observed.add("block|"+id);
                var entity=chunk.getBlockEntity(pos,net.minecraft.world.level.chunk.LevelChunk.EntityCreationType.CHECK);
                if(entity instanceof CircuitBoardBlockEntity board)board.getComponentsStream().forEach(component->observed.add("board_component|"+ComponentRegistry.getId(component.component)));
                if(entity instanceof ElectricalPanelBlockEntity panel)for(var attachment:panel.getAttachments())if(attachment!=null)observed.add("panel_attachment|"+CEERegistries.PANEL_ATTACHMENT_TYPE.getKey(attachment.type));
            }
            var content=new JsonArray();
            for(var entry:observed) {var split=entry.split("\\|",2);var item=new JsonObject();item.addProperty("registry",split[0]);item.addProperty("id",split[1]);content.add(item);}
            row.add("observed_content",content);
            var declared=additional.stations.stream().filter(s->s.code().equals(visit.code())).findFirst();
            row.add("checked_workflow_content",new Gson().toJsonTree(declared.map(StationaryYardStations.Station::content).orElse(List.of())));
            rows.add(row);
        }
        var report=new JsonObject();report.addProperty("schema",1);report.addProperty("target","0.1.0-beta.5");
        report.addProperty("profile",System.getProperty("wwpg.test.profile","BASE"));report.addProperty("backend",System.getProperty("wwpg.test.backend","NATIVE"));
        report.addProperty("stationary_completion",false);report.add("stations",rows);
        report.addProperty("note","Records follow passing station checks. Observed blocks are distinguished from checked assembly and repair workflows; cabinet items are excluded. Release acceptance is recorded separately.");
        try {Files.writeString(Path.of("stationary-yard-coverage.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");}
        catch(java.io.IOException error){throw new IllegalStateException(error);}
    }
}
