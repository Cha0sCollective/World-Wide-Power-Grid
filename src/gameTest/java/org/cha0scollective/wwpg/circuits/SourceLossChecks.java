package org.cha0scollective.wwpg.circuits;

import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.*;
import net.neoforged.neoforge.gametest.GameTestHolder;
import org.patryk3211.powergrid.collections.*;
import org.patryk3211.powergrid.circuits.circuitboard.*;
import org.patryk3211.powergrid.circuits.components.*;
import org.patryk3211.powergrid.circuits.schematic.*;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

/** Real sources and native wiring; assertions observe actual dust and lamp states. */

public final class SourceLossChecks {
    static final BlockPos BOARD=new BlockPos(3,2,3),A=new BlockPos(3,2,7),B=new BlockPos(6,2,7),AUX=new BlockPos(9,2,7);
    static final BlockPos DUST=BOARD.east(),DUST2=BOARD.east(2),LAMP=BOARD.east(3);
    static final com.google.gson.Gson JSON=new com.google.gson.GsonBuilder().setPrettyPrinting().create();
    static void save(String name,Object object){try{Files.writeString(Path.of(name),JSON.toJson(object));}catch(Exception e){throw new RuntimeException(e);}}
    record Phase(String name,float a,float b,float aux,Integer expected){}
    static void operate(GameTestHelper h){
        CircuitSchematic schematic;try{var tag=CircuitInputs.read("source-loss-alarm.nbt");schematic=CircuitSchematic.fromNbt(h.getLevel().registryAccess(),tag);h.assertTrue(schematic.components().size()==tag.getList("Components",10).size(),"Import dropped parts");}catch(Exception e){throw new RuntimeException(e);}
        var inventory=schematic.components().stream().map(p->ComponentRegistry.getId(p.component).toString()).toList();
        h.assertTrue(inventory.stream().allMatch(id->id.startsWith("powergrid:")),"Board requires addon parts");h.assertTrue(inventory.stream().noneMatch(id->id.contains("relay")),"Board contains relay");save("component-inventory.json",inventory);
        int expectedParts=schematic.components().size();
        for(var p:List.of(BOARD,A,B,AUX,DUST,DUST2,LAMP)){h.setBlock(p.below(),Blocks.STONE);var abs=h.absolutePos(p);h.getLevel().setChunkForced(abs.getX()>>4,abs.getZ()>>4,true);}
        h.setBlock(BOARD,ModdedBlocks.CIRCUIT_BOARD.getDefaultState().setValue(CircuitBoardBlock.HORIZONTAL_FACING,Direction.NORTH).setValue(CircuitBoardBlock.ROTATION,0));
        var board=(CircuitBoardBlockEntity)h.getBlockEntity(BOARD);board.setSchematic(schematic);
        h.setBlock(DUST,Blocks.REDSTONE_WIRE);h.setBlock(DUST2,Blocks.REDSTONE_WIRE);h.setBlock(LAMP,Blocks.REDSTONE_LAMP);
        for(var p:List.of(A,B,AUX)){h.setBlock(p,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());source(h,p,0);}
        var ports=new LinkedHashMap<String,Integer>();int index=0;for(var p:schematic.components())if(p.component==Components.CONNECTOR.get())ports.put(p.get(Component.LABEL),index++);
        h.assertTrue(ports.keySet().equals(new LinkedHashSet<>(List.of("A","B","AUX","GND"))),"Unexpected ports");
        h.runAtTickTime(5,()->{connect(h,A,0,BOARD,ports.get("A"));connect(h,A,1,BOARD,ports.get("GND"));connect(h,B,0,BOARD,ports.get("B"));connect(h,B,1,BOARD,ports.get("GND"));connect(h,AUX,0,BOARD,ports.get("AUX"));connect(h,AUX,1,BOARD,ports.get("GND"));});
        var phases=new ArrayList<Phase>();for(float aux:new float[]{12,18,24})for(float a:new float[]{0,12,18,24})for(float b:new float[]{0,12,18,24})phases.add(new Phase("matrix",a,b,aux,a==0||b==0?15:0));
        for(float supply:new float[]{12,24})for(float sense:new float[]{0,.25f,.5f,.75f,1,2}){
            Integer expected=sense<=.25f?Integer.valueOf(15):sense>=1?Integer.valueOf(0):null;
            phases.add(new Phase("threshold-a",sense,supply,supply,expected));phases.add(new Phase("threshold-b",supply,sense,supply,expected));
        }
        var results=new ArrayList<Map<String,Object>>();var observations=new ArrayList<Map<String,Object>>();
        for(int i=0;i<phases.size();i++){
            var phase=phases.get(i);int begin=30+i*24;var samples=new ArrayList<Map<String,Object>>();
            h.runAtTickTime(begin,()->{source(h,A,phase.a());source(h,B,phase.b());source(h,AUX,phase.aux());});
            for(int k=12;k<22;k++){final int tick=begin+k;h.runAtTickTime(tick,()->{
                var sample=observe(h,board,expectedParts);samples.add(sample);if(phase.expected()!=null)assertOutput(h,sample,phase.expected(),phase.toString());
            });}
            h.runAtTickTime(begin+23,()->{var item=new LinkedHashMap<String,Object>();item.put("name",phase.name());item.put("a",phase.a());item.put("b",phase.b());item.put("aux",phase.aux());item.put("expected",phase.expected());item.put("samples",samples);results.add(item);save("phase-results.json",results);observations.add(backend(h));save("backend-observations.json",observations);});
        }
        int end=30+phases.size()*24;
        h.runAtTickTime(end,()->{source(h,A,0);source(h,B,0);source(h,AUX,12);});
        h.runAtTickTime(end+24,()->{checkpoint(h,board,expectedParts,"both-missing",15);board.setSchematic(CircuitSchematic.fromNbt(h.getLevel().registryAccess(),board.getSchematic().serializeNbt(h.getLevel().registryAccess())));});
        h.runAtTickTime(end+48,()->{checkpoint(h,board,expectedParts,"reconstructed-alarm",15);source(h,A,12);source(h,B,24);});
        h.runAtTickTime(end+72,()->{checkpoint(h,board,expectedParts,"restored-healthy",0);h.setBlock(A,Blocks.AIR);});
        h.runAtTickTime(end+112,()->{checkpoint(h,board,expectedParts,"source-a-removed",15);h.setBlock(B,Blocks.AIR);});
        h.runAtTickTime(end+152,()->{checkpoint(h,board,expectedParts,"both-sources-removed",15);for(var p:List.of(A,B)){h.setBlock(p,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());source(h,p,12);}connect(h,A,0,BOARD,ports.get("A"));connect(h,A,1,BOARD,ports.get("GND"));connect(h,B,0,BOARD,ports.get("B"));connect(h,B,1,BOARD,ports.get("GND"));});
        h.runAtTickTime(end+192,()->{checkpoint(h,board,expectedParts,"sources-reconnected",0);source(h,A,0);source(h,B,0);});
        h.runAtTickTime(end+216,()->{checkpoint(h,board,expectedParts,"alarm-before-aux-loss",15);source(h,AUX,0);});
        h.runAtTickTime(end+240,()->{save("aux-off-observation.json",observe(h,board,expectedParts));source(h,AUX,24);});
        h.runAtTickTime(end+264,()->{checkpoint(h,board,expectedParts,"aux-restored",15);source(h,A,24);source(h,B,24);});
        h.runAtTickTime(end+288,()->{checkpoint(h,board,expectedParts,"final-healthy",0);save("actual-backend.json",backend(h));h.assertTrue(ModdedConfigs.server().electricity.overheating.get(),"Overheating disabled");save("complete.json",Map.of("passed",true,"substeps",16,"matrix_cases",48,"threshold_cases",24,"samples_per_case",10,"components",expectedParts,"direct_redstone",true,"overheating",true));h.succeed();});
    }
    static Map<String,Object> observe(GameTestHelper h,CircuitBoardBlockEntity board,int expectedParts){
        h.assertTrue(board.getComponentsStream().count()==expectedParts,"Lost board part");h.assertTrue(board.getComponentsStream().noneMatch(p->p.destroyed),"Destroyed board part");
        var gauge=board.getComponentsStream().filter(p->p.component instanceof VoltageGaugeComponent).findFirst().orElseThrow();
        return Map.of("level",h.getLevel().getSignal(h.absolutePos(BOARD),Direction.WEST),"dust",h.getBlockState(DUST).getValue(RedStoneWireBlock.POWER),"dust2",h.getBlockState(DUST2).getValue(RedStoneWireBlock.POWER),"lamp",h.getBlockState(LAMP).getValue(RedstoneLampBlock.LIT),"gauge_level",gauge.get(GaugeComponent.LEVEL),"alarm_voltage",((VoltageGaugeComponent)gauge.component).getValue(gauge));
    }
    static void assertOutput(GameTestHelper h,Map<String,Object> value,int expected,String phase){h.assertTrue(value.get("level").equals(expected)&&value.get("dust").equals(expected)&&value.get("dust2").equals(Math.max(0,expected-1))&&value.get("lamp").equals(expected>0),"Bad output "+phase+": "+value);}
    static void checkpoint(GameTestHelper h,CircuitBoardBlockEntity board,int count,String name,int expected){var value=observe(h,board,count);save(name+".json",value);assertOutput(h,value,expected,name);}
    static Map<String,Object> backend(GameTestHelper h){
        try{var expected=org.patryk3211.powergrid.config.CSolver.SolverBackend.valueOf(System.getProperty("wwpg.test.backend"));var field=org.patryk3211.powergrid.electricity.sim.ElectricalNetwork.class.getDeclaredField("mna");field.setAccessible(true);var classes=new ArrayList<String>();
            for(var network:org.patryk3211.powergrid.electricity.GlobalElectricNetworks.getWorldNetworks(h.getLevel()).subnetworks){if(network.isEmpty())continue;var solver=(org.patryk3211.powergrid.electricity.sim.solver.IMNA)field.get(network);h.assertTrue(solver.type()==expected,"Unexpected backend");classes.add(solver.getClass().getName());}
            h.assertTrue(!classes.isEmpty(),"No real solver");h.assertTrue(ModdedConfigs.server().electricity.solver.multiTicks.get()==16,"Substeps changed");return Map.of("backend",expected.name(),"substeps",16,"classes",classes);
        }catch(Exception e){throw new RuntimeException(e);}
    }
    static void source(GameTestHelper h,BlockPos pos,float volts){((CreativeSourceBlockEntity)h.getBlockEntity(pos)).setValue(volts);}
    static void connect(GameTestHelper h,BlockPos a,int ai,BlockPos b,int bi){NativeCircuitWiring.connect(h,h.absolutePos(a),ai,h.absolutePos(b),bi,true);}
}
