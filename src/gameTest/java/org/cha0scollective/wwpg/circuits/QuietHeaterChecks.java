package org.cha0scollective.wwpg.circuits;

import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.gametest.GameTestHolder;
import com.simibubi.create.AllBlocks;
import com.feb.moregrid.component.rotary.RotarySwitchComponent;
import org.patryk3211.powergrid.collections.*;
import org.patryk3211.powergrid.circuits.circuitboard.*;
import org.patryk3211.powergrid.circuits.components.*;
import org.patryk3211.powergrid.circuits.schematic.*;
import org.patryk3211.powergrid.electricity.base.*;
import org.patryk3211.powergrid.electricity.basinheater.*;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.deviceconnector.DeviceConnectorBlock;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

/** Drives only the actual on-board selector; the real PG heater supplies the result. */

public final class QuietHeaterChecks {
    static int expectedComponents;
    static final BlockPos BOARD=new BlockPos(3,2,3),SOURCE=new BlockPos(3,2,6),HEATER=new BlockPos(6,2,3),CONNECTOR=HEATER.east();
    static final com.google.gson.Gson JSON=new com.google.gson.GsonBuilder().setPrettyPrinting().create();
    static void save(String file,Object data){try{Files.writeString(Path.of(file),JSON.toJson(data));}catch(Exception e){throw new RuntimeException(e);}}
    static void operate(GameTestHelper h,String scenario){
        CircuitSchematic schematic;
        try(var input=CircuitInputs.open("basin-heater-quiet-240v.nbt")){
            var tag=NbtIo.readCompressed(input,NbtAccounter.unlimitedHeap());schematic=CircuitSchematic.fromNbt(h.getLevel().registryAccess(),tag);
            h.assertTrue(schematic.components().size()==tag.getList("Components",10).size(),"Dropped schematic parts");
        }catch(Exception e){throw new RuntimeException(e);}
        h.assertTrue(schematic.components().stream().noneMatch(p->p.component instanceof RelayComponent || p.component instanceof RedstoneRelayComponent),"Quiet board contains relay");
        expectedComponents=schematic.components().size();
        save("quiet-components.json",schematic.components().stream().map(p->ComponentRegistry.getId(p.component).toString()).toList());
        for(var p:List.of(BOARD,SOURCE,HEATER,CONNECTOR)){h.setBlock(p.below(),Blocks.STONE);var a=h.absolutePos(p);h.getLevel().setChunkForced(a.getX()>>4,a.getZ()>>4,true);}
        h.setBlock(BOARD,ModdedBlocks.CIRCUIT_BOARD.get());h.setBlock(SOURCE,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
        h.setBlock(HEATER,ModdedBlocks.BASIN_HEATER.get());h.setBlock(HEATER.above(),AllBlocks.BASIN.get());
        h.setBlock(CONNECTOR,ModdedBlocks.DEVICE_CONNECTOR.getDefaultState().setValue(DeviceConnectorBlock.FACING,Direction.WEST));
        var be=(CircuitBoardBlockEntity)h.getBlockEntity(BOARD);be.setSchematic(schematic);
        var source=(CreativeSourceBlockEntity)h.getBlockEntity(SOURCE);source.setValue(0);
        var ports=new HashMap<String,Integer>();int index=0;
        for(var p:schematic.components())if(p.component==Components.CONNECTOR.get())ports.put(p.get(Component.LABEL),index++);
        var knob=be.getComponentsStream().filter(p->p.component instanceof RotarySwitchComponent).findFirst().orElseThrow();
        var samples=new ArrayList<Map<String,Object>>();var stats=new ArrayList<Map<String,Object>>();
        h.runAtTickTime(5,()->{
            connect(h,SOURCE,0,BOARD,ports.get("VIN"));connect(h,SOURCE,1,BOARD,ports.get("GND"));
            connect(h,BOARD,ports.get("OUT"),CONNECTOR,0);connect(h,CONNECTOR,1,BOARD,ports.get("GND"));source.setValue(240);
            var heater=(BasinHeaterBlockEntity)h.getBlockEntity(HEATER);
            save("heater-settings.json",Map.of("voltage",240,"resistance",heater.resistance(),"kindled_power",BasinHeaterBlockEntity.power(),"holding_percent",ModdedConfigs.server().electricity.holdingCurrentPercent.get(),"substeps",16,"ambient_temperature",ThermalBehaviour.getAmbientTemperature(h.getLevel(),h.absolutePos(HEATER)),"overheating_enabled",ModdedConfigs.server().electricity.overheating.get()));
        });
        h.runAtTickTime(12,()->{
            try{
                var expected=org.patryk3211.powergrid.config.CSolver.SolverBackend.valueOf(System.getProperty("wwpg.test.backend"));
                var field=org.patryk3211.powergrid.electricity.sim.ElectricalNetwork.class.getDeclaredField("mna");field.setAccessible(true);
                var actual=new ArrayList<String>();
                for(var network:org.patryk3211.powergrid.electricity.GlobalElectricNetworks.getWorldNetworks(h.getLevel()).subnetworks){
                    if(network.isEmpty())continue;
                    var solver=(org.patryk3211.powergrid.electricity.sim.solver.IMNA)field.get(network);h.assertTrue(solver.type()==expected,"Unexpected solver implementation");actual.add(solver.getClass().getName());
                }
                h.assertTrue(!actual.isEmpty(),"No real solver observed");save("actual-backend.json",Map.of("backend",expected.name(),"substeps",ModdedConfigs.server().electricity.solver.multiTicks.get(),"classes",actual));
            }catch(ReflectiveOperationException e){throw new RuntimeException(e);}
        });
        int[] positions=scenario.equals("probe")?new int[]{0,1,2,3}:new int[]{0,1,2,3,1,2,0};String[] names={"off-start","low","heated","superheated","low-after-high","heated-again","off-finish"};
        int duration=scenario.equals("probe")?180:300;
        for(int phase=0;phase<positions.length;phase++){
            final int stage=phase;int begin=30+phase*duration;
            h.runAtTickTime(begin,()->{knob.set(RotarySwitchComponent.POSITION,positions[stage]);knob.component.stateUpdated(knob);System.out.println("HEATER_SELECT "+names[stage]);});
            for(int n=0;n<duration-10;n++){
                final int tick=begin+n;
                h.runAtTickTime(tick,()->{
                    checkComponents(h,be);
                    h.assertTrue(h.getBlockState(HEATER).is(ModdedBlocks.BASIN_HEATER.get()),"Heater destroyed");
                    float t=((BasinHeaterBlockEntity)h.getBlockEntity(HEATER)).getBehaviour(ThermalBehaviour.TYPE).getTemperature();
                    double out=new BlockWireEndpoint(h.absolutePos(BOARD),ports.get("OUT")).getNode(h.getLevel()).getVoltage()-new BlockWireEndpoint(h.absolutePos(BOARD),ports.get("GND")).getNode(h.getLevel()).getVoltage();
                    var state=h.getBlockState(HEATER).getValue(BasinHeaterBlock.HEAT_LEVEL);
                    var sample=new LinkedHashMap<String,Object>();sample.put("tick",tick);sample.put("stage",names[stage]);sample.put("temp",t);sample.put("heat",state.name());sample.put("out",out);
                    if(scenario.equals("probe")){
                        var bjt=new ArrayList<Map<String,Object>>();be.getComponentsStream().filter(p->p.component instanceof BJTComponent).forEach(p->{
                            var wire=(org.patryk3211.powergrid.electricity.sim.special.BJTWire)p.wires.getFirst();
                            bjt.add(Map.of("x",p.x,"y",p.y,"volts",wire.coupledNodes().stream().map(node->node.getVoltage()).toList()));
                        });sample.put("bjt",bjt);
                    }
                    samples.add(sample);
                });
            }
            h.runAtTickTime(begin+duration-5,()->{
                var tail=samples.stream().filter(p->p.get("stage").equals(names[stage])&&(int)p.get("tick")>=begin+duration-110).toList();
                var heat=new TreeMap<String,Integer>();double lo=Double.POSITIVE_INFINITY,hi=-lo,sum=0;int on=0;
                for(var p:tail){double t=((Number)p.get("temp")).doubleValue();lo=Math.min(lo,t);hi=Math.max(hi,t);sum+=t;heat.merge((String)p.get("heat"),1,Integer::sum);if(((Number)p.get("out")).doubleValue()>200)on++;}
                var result=Map.<String,Object>of("stage",names[stage],"position",positions[stage],"min_temp",lo,"max_temp",hi,"average_temp",sum/tail.size(),"heat_states",heat,"sampled_on_fraction",on/(double)tail.size());
                stats.add(result);save("phase-results.json",stats);save("heater-trace.json",samples);System.out.println("HEATER_RESULT "+JSON.toJson(result));
            });
        }
        int end=30+positions.length*duration;
        h.runAtTickTime(end,()->{
            save("phase-results.json",stats);save("heater-trace.json",samples);
            if(scenario.equals("verify"))for(var p:stats){int pos=((Number)p.get("position")).intValue();String expected=new String[]{"NONE","FADING","KINDLED","SEETHING"}[pos];var states=(Map<?,?>)p.get("heat_states");h.assertTrue(states.size()==1&&states.containsKey(expected),"Heat preset missed "+expected+": "+p);}
            if(!scenario.equals("verify")){System.out.println("HEATER_COMPLETE "+scenario);h.succeed();return;}
            be.setSchematic(CircuitSchematic.fromNbt(h.getLevel().registryAccess(),be.getSchematic().serializeNbt(h.getLevel().registryAccess())));
            h.assertTrue(be.terminalCount()==3,"Reconstruction changed external terminals");
        });
        if(scenario.equals("verify")){
            h.runAtTickTime(end+5,()->select(be,3));
            h.runAtTickTime(end+145,()->checkState(h,be,"SEETHING","reconstructed-high"));
            h.runAtTickTime(end+150,()->source.setValue(0));
            h.runAtTickTime(end+260,()->checkState(h,be,"NONE","supply-lost"));
            h.runAtTickTime(end+265,()->source.setValue(240));
            h.runAtTickTime(end+420,()->checkState(h,be,"SEETHING","supply-restored-high"));
            h.runAtTickTime(end+425,()->select(be,0));
            h.runAtTickTime(end+560,()->{
                checkState(h,be,"NONE","final-off");
            });
            h.runAtTickTime(end+565,()->select(be,1));
            h.runAtTickTime(end+760,()->{
                checkState(h,be,"FADING","low-before-reconstruction");
                be.setSchematic(CircuitSchematic.fromNbt(h.getLevel().registryAccess(),be.getSchematic().serializeNbt(h.getLevel().registryAccess())));
            });
            h.runAtTickTime(end+960,()->checkState(h,be,"FADING","reconstructed-low"));
            h.runAtTickTime(end+965,()->select(be,2));
            h.runAtTickTime(end+1160,()->{checkState(h,be,"KINDLED","heated-before-power-loss");source.setValue(0);});
            h.runAtTickTime(end+1300,()->{checkState(h,be,"NONE","heated-supply-lost");source.setValue(240);});
            h.runAtTickTime(end+1500,()->checkState(h,be,"KINDLED","heated-supply-restored"));
            h.runAtTickTime(end+1505,()->select(be,0));
            h.runAtTickTime(end+1700,()->{
                checkState(h,be,"NONE","final-off-after-restarts");
                save("sound-events.json",QuietSoundObserver.sounds);
                h.assertTrue(QuietSoundObserver.sounds.entrySet().stream().noneMatch(e->e.getKey().contains("relay")&&e.getValue()>0),"Relay clicks emitted: "+QuietSoundObserver.sounds);
                h.assertTrue(ModdedConfigs.server().electricity.solver.multiTicks.get()==16,"Substeps changed");
                System.out.println("HEATER_COMPLETE "+scenario);h.succeed();
            });
        }
    }
    static void select(CircuitBoardBlockEntity be,int index){var knob=be.getComponentsStream().filter(p->p.component instanceof RotarySwitchComponent).findFirst().orElseThrow();knob.set(RotarySwitchComponent.POSITION,index);knob.component.stateUpdated(knob);}
    static void checkState(GameTestHelper h,CircuitBoardBlockEntity be,String expected,String label){
        checkComponents(h,be);
        h.assertTrue(h.getBlockState(HEATER).is(ModdedBlocks.BASIN_HEATER.get()),"Heater destroyed during "+label);
        var heat=h.getBlockState(HEATER).getValue(BasinHeaterBlock.HEAT_LEVEL);var temperature=((BasinHeaterBlockEntity)h.getBlockEntity(HEATER)).getBehaviour(ThermalBehaviour.TYPE).getTemperature();
        h.assertTrue(heat.name().equals(expected),"Unexpected "+heat+" during "+label+" at "+temperature);
        save(label+".json",Map.of("heat",heat.name(),"temperature",temperature));System.out.println("HEATER_CHECK "+label+" "+heat+" "+temperature);
    }
    static void checkComponents(GameTestHelper h,CircuitBoardBlockEntity be){
        h.assertTrue(be.getComponentsStream().count()==expectedComponents,"Board lost a component");
        h.assertTrue(be.getComponentsStream().noneMatch(p->p.destroyed),"Destroyed board component");
        h.assertTrue(be.getComponentsStream().filter(p->p.component instanceof com.feb.moregrid.component.FuseComponent).noneMatch(p->p.get(com.feb.moregrid.component.FuseComponent.BLOWN)),"Input fuse blew");
    }
    static void connect(GameTestHelper h,BlockPos a,int ai,BlockPos b,int bi){NativeCircuitWiring.connect(h,h.absolutePos(a),ai,h.absolutePos(b),bi,true);}
}
