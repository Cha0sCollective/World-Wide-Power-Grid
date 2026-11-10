package org.cha0scollective.wwpg.circuits;

import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import org.patryk3211.powergrid.circuits.circuitboard.CircuitBoardBlockEntity;
import org.patryk3211.powergrid.circuits.components.*;
import org.patryk3211.powergrid.circuits.schematic.*;
import org.patryk3211.powergrid.collections.*;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;


/** Opt-in, one circuit per process, preserving raw traces in the working directory. */

public final class FunctionalBaselines {
    static CircuitSchematic load(GameTestHelper h,String file) {
        try(var stream=CircuitInputs.open(file)) {
            var nbt=NbtIo.readCompressed(stream,NbtAccounter.unlimitedHeap());
            var s=CircuitSchematic.fromNbt(h.getLevel().registryAccess(),nbt);
            h.assertTrue(s.components().size()==nbt.getList("Components",10).size(),"Importer dropped components: "+file);
            return s;
        } catch(java.io.IOException e){throw new RuntimeException(e);}
    }
    static class Rig {
        final GameTestHelper h; final BlockPos board; final CircuitBoardBlockEntity be; final BlockPos origin;
        final Map<String,Integer> ports=new LinkedHashMap<>(); final Map<String,BlockPos> sources=new LinkedHashMap<>();
        final List<String> trace=new ArrayList<>(); int devices=0; String ground;
        Rig(GameTestHelper h,String file,String ground){this(h,FunctionalBaselines.load(h,file),ground);}
        Rig(GameTestHelper h,CircuitSchematic s,String ground) {this(h,s,ground,BlockPos.ZERO);}
        Rig(GameTestHelper h,CircuitSchematic s,String ground,BlockPos origin) {
            this.h=h;this.ground=ground;this.origin=origin;board=h.absolutePos(origin.offset(3,2,3));
            place(new BlockPos(3,2,3),ModdedBlocks.CIRCUIT_BOARD.get());be=(CircuitBoardBlockEntity)h.getLevel().getBlockEntity(board);be.setSchematic(s);
            int port=0;for(var c:s.components())if(c.component==Components.CONNECTOR.get()) {
                String label=c.serializeNbt(h.getLevel().registryAccess()).getCompound("Properties").getString("powergrid:label");
                if(ports.put(label,port++)!=null)throw new IllegalStateException("Duplicate pin label: "+label);
            }
            h.assertTrue(be.terminalCount()==port,"Unmapped external terminals");
            for(var bundle:s.findNodeBundles())System.out.println("COMMUNITY_NET "+bundle.stream().map(n->n.placed().x+":"+n.placed().y+"/"+n.pad()).toList());
            for(var c:s.components())if(c.component!=Components.VIA.get())System.out.println("COMMUNITY_PADS "+c.x+":"+c.y+" "+c.footprint().getPads());
        }
        BlockPos place(BlockPos relative,net.minecraft.world.level.block.Block block){relative=relative.offset(origin);var absolute=h.absolutePos(relative);h.getLevel().setChunkForced(absolute.getX()>>4,absolute.getZ()>>4,true);h.setBlock(relative.below(),Blocks.STONE);h.setBlock(relative,block);return absolute;}
        BlockPos device(net.minecraft.world.level.block.Block block){int n=devices++;return place(new BlockPos(1+n%7,2,6+n/7*3),block);}
        void wire(BlockPos a,int ai,BlockPos b,int bi){NativeCircuitWiring.connect(h,a,ai,b,bi,true);}
        void source(String pin,double value) {
            var p=device(ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());sources.put(pin,p);
            ((CreativeSourceBlockEntity)h.getLevel().getBlockEntity(p)).setValue((float)value);
            wire(p,0,board,ports.get(pin));wire(p,1,board,ports.get(ground));
        }
        void volts(String pin,double value){((CreativeSourceBlockEntity)h.getLevel().getBlockEntity(sources.get(pin))).setValue((float)value);}
        void load(String pin,double resistance){load(pin,ground,resistance);}
        void load(String pin,String returnPin,double resistance){var p=device(ModdedBlocks.CREATIVE_RESISTOR.get());((ResistorBlockEntity)h.getLevel().getBlockEntity(p)).setValue(resistance);wire(board,ports.get(pin),p,0);wire(p,1,board,ports.get(returnPin));}
        double v(String pin){return node(pin)-node(ground);}
        double node(String pin){return new BlockWireEndpoint(board,ports.get(pin)).getNode(h.getLevel()).getVoltage();}
        void sample(String event,String...pins){h.assertTrue(be.getComponentsStream().noneMatch(c->c.destroyed),"Destroyed component at "+event);String row=event;for(String pin:pins){double v=v(pin);h.assertTrue(Double.isFinite(v),"Nonfinite "+pin);row+=","+pin+"="+v;}trace.add(row);System.out.println("COMMUNITY_TRACE "+row);}
        void save(String name){try{Files.write(Path.of(name+"-trace.csv"),trace);}catch(Exception e){throw new RuntimeException(e);}}
        void check(String pin,boolean expected){double v=v(pin);h.assertTrue(Double.isFinite(v)&&(expected?v>10:v<1),pin+" expected="+expected+" actual="+v);}
    }
    static void shift(GameTestHelper h){
        var r=new Rig(h,"shift-register/ShiftReg3Bit.nbt","GND");
        h.runAtTickTime(5,()->{r.source("VCC",15);r.source("EN",0);r.source("DIN",0);for(String p:List.of("D0","D1","D2"))r.load(p,1000);});
        boolean[] bits={false,false,false,true,false,true,true,false,false,false};
        for(int i=0;i<bits.length;i++){final int step=i,start=30+i*12;
            h.runAtTickTime(start,()->r.volts("DIN",bits[step]?15:0));
            h.runAtTickTime(start+4,()->r.volts("EN",15));
            h.runAtTickTime(start+5,()->r.volts("EN",0));
            h.runAtTickTime(start+10,()->{
                r.sample("shift-"+step,"D0","D1","D2");r.save("shift");
                for(int j=0;j<3;j++)r.check("D"+j,step>=j&&bits[step-j]);
                if(step==bits.length-1)h.succeed();
            });
        }
    }
    static void adder3(GameTestHelper h){
        var r=new Rig(h,"adders/fa3_ordered.nbt","GND");
        h.runAtTickTime(5,()->{r.source("VCC",15);for(String p:List.of("A1","A2","A3","B1","B2","B3","CIN1"))r.source(p,0);for(String p:List.of("S1","S2","S3","COUT3"))r.load(p,1000);});
        for(int vector=0;vector<128;vector++){final int v=vector,start=20+24*vector;
            h.runAtTickTime(start,()->{for(int i=0;i<3;i++){r.volts("A"+(i+1),(v&(1<<i))!=0?15:0);r.volts("B"+(i+1),(v&(8<<i))!=0?15:0);}r.volts("CIN1",(v&64)!=0?15:0);});
            h.runAtTickTime(start+20,()->{r.sample("vector-"+v,"S1","S2","S3","COUT3");r.save("adder3");int expected=(v&7)+(v>>3&7)+(v>>6);for(int i=0;i<3;i++)r.check("S"+(i+1),(expected&(1<<i))!=0);r.check("COUT3",expected>=8);if(v==127)h.succeed();});
        }
    }
    static void oscillator(GameTestHelper h){
        var r=new Rig(h,"oscillator/LC_Oscillator.nbt","GND");
        h.runAtTickTime(5,()->{r.source("Positive",12);r.source("Negative",-12);r.load("Output",1000000);});
        for(int i=20;i<420;i++){final int tick=i;h.runAtTickTime(i,()->{r.sample("tick-"+tick,"Output");r.save("oscillator");if(tick==419){double min=Double.POSITIVE_INFINITY,max=-min,previous=0;var crossings=new ArrayList<Integer>();for(int j=200;j<r.trace.size();j++){String row=r.trace.get(j);double v=Double.parseDouble(row.substring(row.lastIndexOf('=')+1));min=Math.min(min,v);max=Math.max(max,v);if(previous<0&&v>=0)crossings.add(j);previous=v;}h.assertTrue(min<-.15&&max>.15&&max<.6&&min>-.6,"Sustained waveform amplitude outside expected window");h.assertTrue(crossings.size()>=3,"Too few sustained cycles");for(int j=1;j<crossings.size();j++)h.assertTrue(Math.abs(crossings.get(j)-crossings.get(j-1)-63)<=3,"LC period outside 60–66 ticks");h.succeed();}});}
    }
    static void sourceSwitch(GameTestHelper h){
        var r=new Rig(h,"source-switch/Gen_Battery_autoSwitchV3.nbt","gen -");
        // The three pairs of returns are electrically separate in this board.
        h.runAtTickTime(5,()->{r.source("gen +",48);r.wire(r.board,r.ports.get("check -"),r.board,r.ports.get("gen -"));r.wire(r.board,r.ports.get("bat -"),r.board,r.ports.get("gen -"));r.source("check +",0);r.source("bat +",24);r.load("genload +","genload -",1000);r.load("batload +","batload -",1000);r.load("charge +","charge -",1000);});
        h.runAtTickTime(30,()->{r.sample("checker-off","genload +","genload -","batload +","batload -","charge +","charge -");near(h,r.node("batload +")-r.node("batload -"),24,.1,"Battery feeds its load");r.volts("check +",24);});
        h.runAtTickTime(60,()->{r.sample("checker-on","genload +","genload -","batload +","batload -","charge +","charge -");near(h,r.node("genload +")-r.node("genload -"),48,.1,"Generator feeds load");near(h,r.node("charge +")-r.node("charge -"),48,.1,"Charging feed enabled");near(h,r.node("batload +")-r.node("batload -"),0,.01,"Battery load isolated");r.volts("check +",0);r.volts("gen +",0);});
        h.runAtTickTime(90,()->{r.sample("mains-outage","genload +","genload -","batload +","batload -","charge +","charge -");near(h,r.node("batload +")-r.node("batload -"),24,.1,"Battery takeover");near(h,r.node("genload +")-r.node("genload -"),0,.01,"Generator load lost power");near(h,r.node("charge +")-r.node("charge -"),0,.01,"Charging feed off");r.volts("gen +",48);r.volts("check +",24);});
        h.runAtTickTime(120,()->{r.sample("mains-restored","genload +","genload -","batload +","batload -","charge +","charge -");r.save("source-switch");near(h,r.node("genload +")-r.node("genload -"),48,.1,"Generator recovery");near(h,r.node("charge +")-r.node("charge -"),48,.1,"Charging feed recovery");near(h,r.node("batload +")-r.node("batload -"),0,.01,"Battery load isolated on recovery");h.succeed();});
    }
    static void near(GameTestHelper h,double actual,double expected,double tolerance,String message){h.assertTrue(Double.isFinite(actual)&&Math.abs(actual-expected)<tolerance,message+": "+actual);}
    static void bjt(GameTestHelper h){
        Rig[] cell={null};
        h.runAtTickTime(5,()->{var r=new Rig(h,"adders/fa4t_14V.nbt","GND");cell[0]=r;r.source("VCC",14);for(String pin:List.of("A","B","Cin"))r.source(pin,0);r.load("SUM",1000);r.load("Carry",1000);});
        for(int i=0;i<8;i++){int vector=i,start=20+24*i;h.runAtTickTime(start,()->{var r=cell[0];r.volts("A",(vector&1)!=0?14:0);r.volts("B",(vector&2)!=0?14:0);r.volts("Cin",(vector&4)!=0?14:0);});h.runAtTickTime(start+20,()->{var r=cell[0];r.sample("vector-"+vector,"SUM","Carry");r.save("bjt");int expected=Integer.bitCount(vector);r.check("SUM",(expected&1)!=0);r.check("Carry",expected>=2);if(vector==7)h.succeed();});}
    }
    static void regulator(GameTestHelper h){
        Rig[] cell={null};double[] first={0};
        h.runAtTickTime(5,()->{
            var r=new Rig(h,load(h,"rebuilt/native-linear-regulator.nbt"),"GND");cell[0]=r;r.source("VIN",24);r.source("VREF",5);r.load("VREF","ADJUST",1000);r.load("ADJUST",1000);r.load("OUT",1000);
        });
        h.runAtTickTime(60,()->{var r=cell[0];r.sample("vin24-vref5-1k","OUT","ADJUST","VIN","VREF");r.save("regulator");near(h,r.v("OUT"),1.5,.5,"Two-junction pass output at divided reference");first[0]=r.v("OUT");r.load("OUT",100);});
        h.runAtTickTime(100,()->{var r=cell[0];r.sample("load-step-100ohm","OUT");r.save("regulator");near(h,r.v("OUT"),first[0],.5,"Load regulation");r.volts("VIN",18);});
        h.runAtTickTime(140,()->{var r=cell[0];r.sample("vin18","OUT");r.save("regulator");near(h,r.v("OUT"),first[0],.5,"Line regulation");r.volts("VREF",3);});
        h.runAtTickTime(180,()->{var r=cell[0];r.sample("vref3","OUT","ADJUST");r.save("regulator");near(h,r.v("OUT"),.5,.5,"Reduced divided reference");h.assertTrue(r.v("OUT")<first[0]-.5,"Output did not track lower control voltage");h.succeed();});
    }
    static void memoryCell(GameTestHelper h){
        var r=new Rig(h,NativeMemory.cell(h),"GND");
        h.runAtTickTime(5,()->{r.source("VCC",15);r.source("WRITE",0);r.source("DATA",-15);r.load("Q",1000);});
        h.runAtTickTime(20,()->r.volts("WRITE",15));
        h.runAtTickTime(30,()->{r.volts("WRITE",0);r.sample("write-zero","Q");r.check("Q",false);});
        h.runAtTickTime(40,()->r.volts("DATA",15));
        h.runAtTickTime(45,()->{r.sample("hold-zero","Q");r.check("Q",false);r.volts("WRITE",15);});
        h.runAtTickTime(60,()->{r.sample("write-one","Q");r.check("Q",true);r.volts("WRITE",0);});
        h.runAtTickTime(75,()->{r.volts("DATA",-15);r.volts("VCC",0);});
        h.runAtTickTime(90,()->{r.volts("VCC",15);});
        h.runAtTickTime(105,()->{r.sample("retained-one","Q");r.check("Q",true);r.volts("WRITE",15);});
        h.runAtTickTime(120,()->{r.sample("overwrite-zero","Q");r.check("Q",false);r.save("memory-cell");h.succeed();});
    }
    static void memoryBank(GameTestHelper h,int rows,int columns){
        var cells=new Rig[rows][columns];var memory=new int[rows];
        for(int row=0;row<rows;row++)for(int col=0;col<columns;col++)cells[row][col]=new Rig(h,load(h,"rebuilt/native-memory-cell.nbt"),"GND",new BlockPos(col*10,0,row*12));
        Rig anchor=cells[0][0];
        h.runAtTickTime(5,()->{
            for(int row=0;row<rows;row++)for(int col=0;col<columns;col++){
                Rig r=cells[row][col];r.load("Q",1000);
                if(r!=anchor){Rig neighbor=col>0?cells[row][col-1]:cells[row-1][col];r.wire(r.board,r.ports.get("GND"),neighbor.board,neighbor.ports.get("GND"));r.wire(r.board,r.ports.get("VCC"),neighbor.board,neighbor.ports.get("VCC"));}
                if(col>0)r.wire(r.board,r.ports.get("WRITE"),cells[row][col-1].board,cells[row][col-1].ports.get("WRITE"));
                if(row>0)r.wire(r.board,r.ports.get("DATA"),cells[row-1][col].board,cells[row-1][col].ports.get("DATA"));
            }
            anchor.source("VCC",15);
            for(int row=0;row<rows;row++)cells[row][0].source("WRITE",0);
            for(int col=0;col<columns;col++)cells[0][col].source("DATA",-15);
        });
        var writes=new ArrayList<int[]>();
        if(rows==1){for(int p:new int[]{0xA5,0x5A,0,255,1,2,4,8,16,32,64,128,254,253,251,247,239,223,191,127})writes.add(new int[]{0,p});}
        else{for(int row=0;row<rows;row++)writes.add(new int[]{row,new int[]{10,5,15,0}[row]});for(int row=0;row<rows;row++){writes.add(new int[]{row,1<<row});writes.add(new int[]{row,15^(1<<row)});}}
        Consumer<String> verify=event->{int errors=0;for(int row=0;row<rows;row++)for(int col=0;col<columns;col++){var r=cells[row][col];r.sample(event+"-row"+row+"-bit"+col,"Q","DATA","WRITE","VCC");r.save("bank-"+row+"-"+col);boolean expected=(memory[row]&(1<<col))!=0;double v=r.v("Q");if(!(expected?v>10:v<1))errors++;}h.assertTrue(errors==0,"Memory mismatches="+errors+" at "+event);};
        for(int i=0;i<writes.size();i++){final int[] write=writes.get(i);int start=30+32*i;
            h.runAtTickTime(start,()->{for(int col=0;col<columns;col++)cells[0][col].volts("DATA",(write[1]&(1<<col))!=0?15:-15);});
            h.runAtTickTime(start+5,()->cells[write[0]][0].volts("WRITE",15));
            h.runAtTickTime(start+15,()->cells[write[0]][0].volts("WRITE",0));
            h.runAtTickTime(start+25,()->{memory[write[0]]=write[1];verify.accept("write-"+write[0]+"-"+write[1]);});
        }
        int end=30+32*writes.size();
        h.runAtTickTime(end,()->{for(int col=0;col<columns;col++)cells[0][col].volts("DATA",15);});
        h.runAtTickTime(end+15,()->{verify.accept("shared-data-hold");anchor.volts("VCC",0);});
        h.runAtTickTime(end+30,()->anchor.volts("VCC",15));
        h.runAtTickTime(end+45,()->{verify.accept("power-restored");for(var row:cells)for(var r:row){var saved=r.be.getSchematic().serializeNbt(h.getLevel().registryAccess());r.be.setSchematic(CircuitSchematic.fromNbt(h.getLevel().registryAccess(),saved));}});
        h.runAtTickTime(end+65,()->{verify.accept("schematic-roundtrip");System.out.println("COMMUNITY_MEMORY_PASS rows="+rows+" columns="+columns+" writes="+writes.size());h.succeed();});
    }
}
