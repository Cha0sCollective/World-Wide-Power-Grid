package org.cha0scollective.wwpg.circuits;

import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import org.patryk3211.powergrid.collections.*;
import org.patryk3211.powergrid.circuits.components.*;
import org.patryk3211.powergrid.circuits.schematic.*;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;



/** Drives only real native input devices; all storage, selection and protection is circuitry. */

public final class AdvancedLibrary {
    static final class Rack {
        final GameTestHelper h;int devices=0;final Map<String,Rig> boards=new LinkedHashMap<>();
        final List<Map<String,Object>> wiring=new ArrayList<>(),equipment=new ArrayList<>();
        Rack(GameTestHelper h){this.h=h;}
        Rig board(String id,String schematic,int col,int row){var r=new Rig(this,schematic,col,row);boards.put(id,r);return r;}
        void join(Rig a,String ap,Rig b,String bp){a.wire(a.board,a.ports.get(ap),b.board,b.ports.get(bp));}
        void common(String pin){Rig first=null;for(var r:boards.values())if(r.ports.containsKey(pin)){if(first!=null)join(first,pin,r,pin);else first=r;}}
        void rebuild(){for(var r:boards.values()){var tag=r.be.getSchematic().serializeNbt(h.getLevel().registryAccess());r.be.setSchematic(CircuitSchematic.fromNbt(h.getLevel().registryAccess(),tag));}}
        void diagnose(){
            var dump=new LinkedHashMap<String,Object>();
            for(var entry:boards.entrySet()){
                var r=entry.getValue();var ports=new LinkedHashMap<String,Double>();for(var name:r.ports.keySet())ports.put(name,r.v(name));
                var relays=new ArrayList<Map<String,Object>>();
                for(var part:r.be.getComponentsStream().toList())if(part.component instanceof RelayComponent){
                    var item=new LinkedHashMap<String,Object>();item.put("x",part.x);item.put("y",part.y);item.put("state",part.get(RelayComponent.STATE));item.put("polarized",part.get(RelayComponent.POLARIZED));
                    try{var field=org.patryk3211.powergrid.electricity.sim.special.RelaySwitchWire.class.getDeclaredField("coilWire");field.setAccessible(true);var coil=(org.patryk3211.powergrid.electricity.sim.ElectricWire)field.get(part.wires.getFirst());item.put("coil_current",coil.current());item.put("coil_converged",coil.isConverged());item.put("coil_network",coil.getNetwork()!=null);item.put("stock_pg",true);if(coil.getNetwork()!=null)item.put("network_converged",coil.getNetwork().isConverged());item.put("nc",((org.patryk3211.powergrid.electricity.sim.special.RelaySwitchWire)part.wires.get(0)).getState());item.put("no",((org.patryk3211.powergrid.electricity.sim.special.RelaySwitchWire)part.wires.get(1)).getState());}catch(Exception e){item.put("error",e.toString());}relays.add(item);
                }
                dump.put(entry.getKey(),Map.of("ports",ports,"relays",relays));
            }saveJson("fault-rack.json",dump);
        }
        void finish(){
            h.assertTrue(ModdedConfigs.server().electricity.solver.multiTicks.get()==16,"Substep count changed during test");
            h.assertTrue(ModdedConfigs.server().electricity.solver.solverBackend.get().name().equals(System.getProperty("wwpg.test.backend","NATIVE")),"Backend changed during test");
            for(var r:boards.values())h.assertTrue(r.be.getComponentsStream().noneMatch(p->p.destroyed),"Assembly contains a destroyed component");
            for(var r:boards.values())r.save("module-"+boards.entrySet().stream().filter(e->e.getValue()==r).findFirst().orElseThrow().getKey());
            saveManifest();System.out.println("ADVANCED_LIBRARY_PASS "+System.getProperty("wwpg.test.circuit"));h.succeed();
        }
        void saveManifest(){
            var modules=new ArrayList<Map<String,Object>>();for(var e:boards.entrySet()){
                var r=e.getValue();modules.add(Map.of("id",e.getKey(),"schematic",r.design,"position",List.of(r.board.getX(),r.board.getY(),r.board.getZ()),"ports",r.ports));
            }
            saveJson("assembly.json",Map.of("modules",modules,"wires",wiring,"equipment",equipment,"substeps",16));
        }
    }
    static final class Rig extends FunctionalBaselines.Rig {
        final Rack rack;final String design;
        Rig(Rack rack,String design,int col,int row){super(rack.h,LibraryBoards.load(rack.h,design),"GND",new BlockPos(col*3,4,row*3));this.rack=rack;this.design=design;}
        @Override BlockPos device(Block block){
            int n=rack.devices++;var relative=new BlockPos(1+n%8*2,2,1+n/8*2);var absolute=h.absolutePos(relative);
            h.getLevel().setChunkForced(absolute.getX()>>4,absolute.getZ()>>4,true);h.setBlock(relative.below(),Blocks.STONE);h.setBlock(relative,block);
            rack.equipment.add(new LinkedHashMap<>(Map.of("id","device"+n,"position",List.of(absolute.getX(),absolute.getY(),absolute.getZ()),"block",BuiltInRegistries.BLOCK.getKey(block).toString())));return absolute;
        }
        @Override void wire(BlockPos a,int ai,BlockPos b,int bi){
            NativeCircuitWiring.connect(h,a,ai,b,bi,true);
            rack.wiring.add(Map.of("a",List.of(a.getX(),a.getY(),a.getZ(),ai),"b",List.of(b.getX(),b.getY(),b.getZ(),bi)));
        }
        void checkWord(int expected,String...pins){sample("check-"+trace.size(),pins);save("trace-"+design+"-"+board.getX()+"-"+board.getZ());for(int i=0;i<pins.length;i++)check(pins[i],(expected&(1<<i))!=0);}
        @Override void check(String pin,boolean expected){double value=v(pin);if(!(Double.isFinite(value)&&(expected?value>10:value<1)))rack.diagnose();super.check(pin,expected);}
        String moduleId(){return rack.boards.entrySet().stream().filter(e->e.getValue()==this).findFirst().orElseThrow().getKey();}
        @Override void source(String pin,double value){super.source(pin,value);rack.equipment.getLast().put("name",moduleId()+"-"+pin+"-supply");rack.equipment.getLast().put("initial_voltage",value);}
        @Override void load(String pin,String returnPin,double resistance){super.load(pin,returnPin,resistance);rack.equipment.getLast().put("name",moduleId()+"-"+pin+"-load");rack.equipment.getLast().put("resistance",resistance);}
    }
    static void shift(GameTestHelper h){
        var rack=new Rack(h);var bits=new Rig[4];for(int i=0;i<4;i++)bits[i]=rack.board("bit"+i,"clocked-bit",i,0);
        var root=bits[0];int[] state={0};
        h.runAtTickTime(5,()->{
            for(String pin:List.of("GND","VCC","VEE","CLK"))rack.common(pin);
            root.source("VCC",15);root.source("VEE",-15);root.source("CLK",0);root.source("DIN",0);
            for(int i=0;i<4;i++){bits[i].load("Q",1000);if(i>0)rack.join(bits[i-1],"Q",bits[i],"DIN");}
            rack.saveManifest();
        });
        // Long high holds expose transparent-latch propagation; data also changes while clock is high.
        boolean[] input={false,true,false,true,true,false,false,true,true,true,false,false,false,false,true,false,true,true};
        for(int i=0;i<input.length;i++){
            final boolean value=input[i];int start=30+i*32;
            h.runAtTickTime(start,()->{root.volts("CLK",0);root.volts("DIN",value?15:0);});
            h.runAtTickTime(start+8,()->root.volts("CLK",15));
            h.runAtTickTime(start+17,()->{state[0]=((state[0]<<1)|(value?1:0))&15;verifyShift(bits,state[0],"rising");root.volts("DIN",value?0:15);});
            h.runAtTickTime(start+25,()->verifyShift(bits,state[0],"high-hold"));
        }
        int end=30+input.length*32;
        h.runAtTickTime(end,()->{root.volts("CLK",0);verifyShift(bits,state[0],"finished");root.volts("VCC",0);root.volts("VEE",0);});
        h.runAtTickTime(end+15,()->{root.volts("VCC",15);root.volts("VEE",-15);});
        h.runAtTickTime(end+30,()->{verifyShift(bits,state[0],"power-restored");rack.rebuild();});
        h.runAtTickTime(end+50,()->verifyShift(bits,state[0],"rebuilt"));
        for(int i=0;i<10;i++){final int n=i;h.runAtTickTime(end+100+i*100,()->root.volts("DIN",(n&1)==0?15:0));h.runAtTickTime(end+150+i*100,()->verifyShift(bits,state[0],"steady-low-clock"));}
        h.runAtTickTime(end+1150,rack::finish);
    }
    static void verifyShift(Rig[] bits,int expected,String event){for(int i=0;i<4;i++){var r=bits[i];r.sample(event+"-"+r.trace.size(),"Q");r.save("shift-bit"+i);r.check("Q",(expected&(1<<i))!=0);}}
    static void ram(GameTestHelper h){
        var rack=new Rack(h);var words=new Rig[4][2];
        for(int row=0;row<4;row++)for(int half=0;half<2;half++)words[row][half]=rack.board("word"+row+"-half"+half,"retained-word2",half,row);
        var decode=rack.board("write-address","write-decoder",2,1);var read=new Rig[4];for(int bit=0;bit<4;bit++)read[bit]=rack.board("read-bit"+bit,"read-selector",3,bit);
        int[] memory={0,0,0,0};int[] address={0};
        h.runAtTickTime(5,()->{
            rack.common("GND");rack.common("VCC");rack.common("VEE");rack.common("A0");rack.common("A1");
            var root=words[0][0];root.source("VCC",15);root.source("VEE",-15);
            decode.source("A0",0);decode.source("A1",0);decode.source("WRITE",0);
            for(int bit=0;bit<4;bit++){
                words[0][bit/2].source("D"+(bit%2),0);read[bit].load("Q",1000);
                for(int row=0;row<4;row++){
                    rack.join(words[row][bit/2],"Q"+(bit%2),read[bit],"D"+row);
                    if(row>0)rack.join(words[0][bit/2],"D"+(bit%2),words[row][bit/2],"D"+(bit%2));
                }
            }
            for(int row=0;row<4;row++)for(int half=0;half<2;half++)rack.join(decode,"W"+row,words[row][half],"WRITE");
            rack.saveManifest();
        });
        var writes=new ArrayList<int[]>();
        for(int row=0;row<4;row++)writes.add(new int[]{row,new int[]{10,5,15,0}[row]});
        for(int row=0;row<4;row++){writes.add(new int[]{row,1<<row});writes.add(new int[]{row,15^(1<<row)});}
        for(int i=0;i<writes.size();i++){
            var w=writes.get(i);int start=30+i*100;
            h.runAtTickTime(start,()->{decode.volts("WRITE",0);setAddress(decode,w[0]);address[0]=w[0];setData(words,w[1]);});
            h.runAtTickTime(start+8,()->decode.volts("WRITE",15));
            h.runAtTickTime(start+20,()->{decode.volts("WRITE",0);memory[w[0]]=w[1];});
            h.runAtTickTime(start+30,()->{verifyRam(read,memory[address[0]],"written");setData(words,15^w[1]);});
            h.runAtTickTime(start+40,()->verifyRam(read,memory[address[0]],"write-disabled"));
            for(int row=0;row<4;row++){final int r=row;h.runAtTickTime(start+45+row*12,()->{setAddress(decode,r);address[0]=r;});h.runAtTickTime(start+54+row*12,()->verifyRam(read,memory[r],"row"+r));}
        }
        int end=30+writes.size()*100;var root=words[0][0];
        h.runAtTickTime(end,()->{decode.volts("WRITE",0);root.volts("VCC",0);root.volts("VEE",0);});
        h.runAtTickTime(end+15,()->{root.volts("VCC",15);root.volts("VEE",-15);});
        for(int row=0;row<4;row++){final int r=row;h.runAtTickTime(end+30+row*12,()->setAddress(decode,r));h.runAtTickTime(end+40+row*12,()->verifyRam(read,memory[r],"retained"+r));}
        h.runAtTickTime(end+85,rack::rebuild);
        for(int row=0;row<4;row++){final int r=row;h.runAtTickTime(end+100+row*12,()->setAddress(decode,r));h.runAtTickTime(end+110+row*12,()->verifyRam(read,memory[r],"rebuilt"+r));}
        for(int i=0;i<8;i++){final int row=i%4;h.runAtTickTime(end+200+i*80,()->{setAddress(decode,row);setData(words,15^memory[row]);});h.runAtTickTime(end+250+i*80,()->verifyRam(read,memory[row],"steady-hold"+row));}
        h.runAtTickTime(end+900,rack::finish);
    }
    static void setAddress(Rig r,int address){r.volts("A0",(address&1)!=0?15:0);r.volts("A1",(address&2)!=0?15:0);}
    static void setData(Rig[][] words,int data){for(int bit=0;bit<4;bit++)words[0][bit/2].volts("D"+(bit%2),(data&(1<<bit))!=0?15:0);}
    static void verifyRam(Rig[] outputs,int expected,String event){for(int i=0;i<4;i++){var r=outputs[i];r.sample(event+"-"+r.trace.size(),"Q");r.save("ram-read-bit"+i);r.check("Q",(expected&(1<<i))!=0);}}
    static void identity(){
        var mods=new ArrayList<Map<String,Object>>();for(var m:net.neoforged.fml.ModList.get().getMods()){
            var item=new LinkedHashMap<String,Object>();item.put("id",m.getModId());item.put("version",m.getVersion().toString());var path=m.getOwningFile().getFile().getFilePath();
            try{if(Files.isRegularFile(path))item.put("sha256",HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))));}catch(Exception e){throw new RuntimeException(e);}mods.add(item);
        }saveJson("runtime-identity.json",mods);
    }
    static void saveJson(String path,Object value){try{Files.writeString(Path.of(path),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(value));}catch(Exception e){throw new RuntimeException(e);}}
}
