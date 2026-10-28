package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.CEEItems;
import com.george_vi.electroenergetics.content.railway_electrification.ElectricTrainData;
import com.george_vi.electroenergetics.content.railway_electrification.pantograph.PantographBlock;
import com.george_vi.electroenergetics.foundation.nodes.*;
import com.george_vi.electroenergetics.mixin_interfaces.*;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import com.george_vi.electroenergetics.simulation.infrastructure.*;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.Create;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.entity.*;
import com.simibubi.create.content.trains.graph.*;
import com.simibubi.create.content.trains.track.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.*;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.collections.ModdedItems;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.equipment.multimeter.MultimeterItem;
import java.util.*;

/** Actual carriage, catenary cuts, transformer, probe input and foreign result commits. */
@GameTestHolder("wwpg_trains")
@PrefixGameTestTemplate(false)
public final class TrainCircuitGameTests {
    private static Fixture active;
    private static boolean listening;
    @BeforeBatch(batch="wwpg_train_circuits") public static void configure(ServerLevel level) {
        DynamicGameTests.substeps(level);
        if (!listening) { NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, TrainCircuitGameTests::finished); listening=true; }
    }
    private static void finished(com.george_vi.electroenergetics.events.FinishElectricSimulationEvent event) {
        var f=active;if(f==null||f.train==null)return;
        if(event.level==f.h.getLevel()) {
            f.voltage=f.data().lastVoltage;f.current=f.data().displayCurrent;
            var p=f.data().pantographs.getFirst();
            if(p.node!=null&&f.firstContact==null)f.firstContact=p.node;
            if(p.onConnection!=null&&p.onConnection.wireData instanceof CatenaryConnectionData)f.spans.add(p.onConnection);
            long tick=f.h.getTick();
            if(tick>=55&&tick<150||tick>=198&&tick<=260) {
                if(!Double.isFinite(f.voltage)||f.voltage<1900)f.failure="Accepted power lost during continuous handover";
                if(p.node!=f.firstContact)f.failure="Handover replaced the native contact identity";
            }
        } else if(Double.compare(f.voltage,f.data().lastVoltage)!=0||Double.compare(f.current,f.data().displayCurrent)!=0)
            f.failure="Foreign dimension overwrote train readings";
    }
    @GameTest(template="empty",batch="wwpg_train_circuits",timeoutTicks=300)
    public static void transformerFedMovingTrainKeepsContactAndRecoversWithoutForeignCommits(GameTestHelper h) {
        var f=new Fixture(h);active=f;f.build();
        h.runAtTickTime(20,f::assemble);h.runAtTickTime(30,f::powerAndOverhead);h.runAtTickTime(35,f::neighboringContacts);
        h.onEachTick(()->{
            if(f.train==null)return;
            if(h.getTick()>=55){f.train.burnFuel();f.train.manualTick=true;f.train.targetSpeed=.1;f.train.approachTargetSpeed(1);}
            if(f.failure!=null){f.cleanup();h.fail(f.failure);}
            if(h.getTick()>=55&&h.getTick()<150||h.getTick()>=198&&h.getTick()<=260)f.meter(true);
        });
        h.runAtTickTime(90,f::foreignResults);
        h.runAtTickTime(150,()->{
            h.assertTrue(f.position().distanceTo(f.start)>3&&f.train.fuelTicks>1,"Train did not receive native powered traction");
            h.assertTrue(f.spans.size()>=2,"Native pantograph did not cross both overhead spans: "+f.spans.size()+" start="+f.start+" position="+f.position()+" join="+f.middle);
            f.supply().setValue(0);
        });
        h.runAtTickTime(185,()->{
            h.assertTrue(f.voltage<1&&f.current<.001&&f.train.fuelTicks==0,"Disconnected source left ghost traction");
            f.meter(false);
        });
        h.runAtTickTime(195,()->f.supply().setValue(1500));
        h.runAtTickTime(260,()->{
            h.assertTrue(f.failure==null,f.failure==null?"":f.failure);
            h.assertTrue(f.voltage>1900&&f.current>1&&f.train.fuelTicks>1,"Train failed to recover from unavailable initialization");
            h.assertTrue(f.data().accumulators==0&&f.data().accumulatorCharge==0&&!f.data().hasCreativeSource,"Onboard fallback contaminated test");
            f.foreignResults();DynamicGameTests.audit(h);f.cleanup();h.succeed();
        });
    }

    private static final class Fixture {
        final GameTestHelper h;
        final BlockPos source=new BlockPos(4,2,4),core=new BlockPos(10,2,5),rod=new BlockPos(7,2,4),bogey=new BlockPos(24,3,80);
        final Set<ConnectionEntry> spans=Collections.newSetFromMap(new IdentityHashMap<>());
        final List<WireSimulationState.WireCutHandle> neighbors=new ArrayList<>();
        final ItemStack stack=ModdedItems.MULTIMETER.asStack();
        final MultimeterItem probe=(MultimeterItem)stack.getItem();
        net.minecraft.world.entity.player.Player player;
        Train train;Carriage carriage;TrackGraph graph;Vec3 start;AttachedNode firstContact;
        BlockPos first,middle,last;double voltage,current;String failure;
        Fixture(GameTestHelper h){this.h=h;}
        void build(){
            h.setBlock(source.below(),Blocks.STONE);h.setBlock(source,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
            h.setBlock(rod.below(),Blocks.STONE);h.setBlock(rod,CEEBlocks.GROUND_ROD.get());
            var p=h.makeMockPlayer(GameType.CREATIVE);p.setYRot(-90);var item=CEEBlocks.TRANSFORMER_CORE.asStack();p.setItemInHand(InteractionHand.MAIN_HAND,item);
            h.setBlock(core.below(),Blocks.STONE);var support=h.absolutePos(core.below());
            h.assertTrue(item.getItem().useOn(new UseOnContext(p,InteractionHand.MAIN_HAND,new BlockHitResult(support.getCenter().add(0,.5,0),Direction.UP,support,false))).consumesAction(),"Native core placement failed");
            for(int x=8;x<=82;x++){h.setBlock(new BlockPos(x,1,80),Blocks.STONE);h.setBlock(new BlockPos(x,2,80),AllBlocks.TRACK.getDefaultState().setValue(TrackBlock.SHAPE,TrackShape.XO));}
            h.setBlock(bogey,AllBlocks.SMALL_BOGEY.getDefaultState().setValue(BlockStateProperties.HORIZONTAL_AXIS,Direction.Axis.Z));
            h.setBlock(bogey.above(),CEEBlocks.ELECTRIC_MOTORS[0].get());
            h.setBlock(bogey.above(2),CEEBlocks.PANTOGRAPH.getDefaultState().setValue(PantographBlock.FACING,Direction.WEST));
            h.setBlock(bogey.west().above(),AllBlocks.TRAIN_CONTROLS.getDefaultState().setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.WEST));
            var abs=h.absolutePos(bogey);h.getLevel().addFreshEntity(new SuperGlueEntity(h.getLevel(),new AABB(Vec3.atLowerCornerOf(abs.west()),Vec3.atLowerCornerOf(abs.above(3).east().south()))));
        }
        void assemble(){try{
            var rail=h.absolutePos(bogey.below());TrackPropagator.onRailAdded(h.getLevel(),rail,h.getLevel().getBlockState(rail));
            TrackNode first=null,last=null;double nearest=Double.POSITIVE_INFINITY,length=0;
            for(var g:Create.RAILWAYS.trackNetworks.values())for(var loc:g.getNodes()){
                if(!loc.dimension.equals(h.getLevel().dimension())||Math.abs(loc.getLocation().z-rail.getCenter().z)>1)continue;
                var a=g.locateNode(loc);
                for(var entry:g.getConnectionsFrom(a).entrySet()){
                    var b=entry.getKey();double len=entry.getValue().getLength();
                    double distance=Math.abs((a.getLocation().getLocation().x+b.getLocation().getLocation().x)/2-rail.getCenter().x);
                    if(b.getLocation().getLocation().x>a.getLocation().getLocation().x&&len>3&&distance<nearest){first=a;last=b;graph=g;length=len;nearest=distance;}
                }
            }
            h.assertTrue(first!=null,"Native track graph missing");var edge=graph.getConnectionsFrom(first).get(last);var type=(AbstractBogeyBlock<?>)AllBlocks.SMALL_BOGEY.get();double spacing=type.getWheelPointSpacing();
            carriage=new Carriage(new CarriageBogey(type,false,new CompoundTag(),new TravellingPoint(first,last,edge,length/2+spacing/2,false),new TravellingPoint(first,last,edge,length/2-spacing/2,false)),null,0);
            var contraption=new CarriageContraption(Direction.WEST);h.assertTrue(contraption.assemble(h.getLevel(),h.absolutePos(bogey)),"Native carriage assembly failed");
            h.assertTrue(((IPantographList)contraption).electroEnergetics$hasElectricMotor()&&((IPantographList)contraption).electroEnergetics$getPantographList().size()==1,"CEE did not capture its native motor/pantograph");
            contraption.removeBlocksFromWorld(h.getLevel(),BlockPos.ZERO);contraption.expandBoundsAroundAxis(Direction.Axis.Y);
            train=new Train(UUID.randomUUID(),UUID.randomUUID(),graph,new ArrayList<>(List.of(carriage)),new ArrayList<>(),false,0);carriage.setContraption(h.getLevel(),contraption);train.collectInitiallyOccupiedSignalBlocks();Create.RAILWAYS.addTrain(train);start=position();
        }catch(Exception e){cleanup();throw new RuntimeException(e);}}
        void powerAndOverhead(){
            var anchor=carriage.getDimensional(h.getLevel()).rotationAnchors.getFirst();var pantograph=data().pantographs.getFirst();
            int y=(int)Math.round(anchor.y+pantograph.rotatedPos.getY()+pantograph.type.reach/2+pantograph.type.topOffset)-h.absolutePos(BlockPos.ZERO).getY();
            int join=(int)Math.ceil(start.x-h.absolutePos(BlockPos.ZERO).getX())+3;
            first=new BlockPos(14,y,80);middle=new BlockPos(join,y,80);last=new BlockPos(62,y,80);
            for(var holder:List.of(first,middle,last)){
                var pole=holder.north(2).below(2);h.setBlock(pole.below(),Blocks.STONE);for(int i=0;i<3;i++)place(pole.above(i).below(),CEEBlocks.CONCRETE_POLE.asStack());place(holder.below(),CEEBlocks.CATENARY_HOLDER.asStack());
            }
            for(var ends:List.of(List.of(first,middle),List.of(middle,last))){var p=h.makeMockPlayer(GameType.SURVIVAL);var spool=CEEItems.COPPER_WIRE_SPOOL.asStack();p.setItemInHand(InteractionHand.MAIN_HAND,spool);for(var end:ends){var abs=h.absolutePos(end);h.assertTrue(spool.getItem().useOn(new UseOnContext(p,InteractionHand.MAIN_HAND,new BlockHitResult(new InWorldNode(0,abs).getPosition(h.getLevel()),Direction.UP,abs,false))).consumesAction(),"Native overhead spool failed");}}
            scroll(core,1);scroll(core.east(),2);wire(source,0,core,0);wire(source,1,core,1);wire(core.east(),0,first,0);wire(core.east(),1,rod,0);supply().setValue(1500);
            player=h.makeMockPlayer(GameType.CREATIVE);player.setPos(h.absolutePos(core.east()).getCenter());player.setItemInHand(InteractionHand.MAIN_HAND,stack);
            for(int id:new int[]{1,0}){var pos=h.absolutePos(core.east());var endpoint=new BlockWireEndpoint(pos,id);h.assertTrue(probe.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(endpoint.getExactPosition(h.getLevel()),Direction.UP,pos,false))).consumesAction(),"Native PG multimeter probe rejected transformer");}
        }
        void neighboringContacts(){
            var state=InfrastructureSavedData.load(h.getLevel()).wireSimulationState;
            for(var ends:List.of(List.of(first,middle),List.of(middle,last))){
                var connection=new InWorldNodeConnection(new InWorldNode(0,h.absolutePos(ends.get(0))),new InWorldNode(0,h.absolutePos(ends.get(1))));
                var handle=state.createHandle("WWPG neighboring-contact regression");neighbors.add(handle);state.createCut(handle,connection,.5f);
            }
        }
        void foreignResults(){
            var d=data();double v=d.lastVoltage,i=d.displayCurrent;int fuel=train.fuelTicks,sync=d.ticksSinceGaugeSync;d.accumulators=1;d.accumulatorCharge=.5;
            for(var dimension:List.of(Level.NETHER,Level.END)){
                var sd=InfrastructureSavedData.load(h.getLevel().getServer().getLevel(dimension));var indexes=new it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap<Node>();indexes.defaultReturnValue(-1);
                var builder=new com.george_vi.electroenergetics.simulation.CircuitBuilder(0,new ArrayList<>(),indexes);builder.addNode(d.trainNode);builder.addNode(d.groundNode);double[] samples=new double[builder.allNodes().size()*16];
                for(var node:builder.allNodes())if(node.node.equals(d.trainNode))Arrays.fill(samples,node.ordinal*16,node.ordinal*16+16,777);
                sd.catenaryModule.finishSimulation(new SimulationResults(samples,16,new it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap<>(),builder,sd));
                h.assertTrue(Double.compare(d.lastVoltage,v)==0&&Double.compare(d.displayCurrent,i)==0&&d.accumulatorCharge==.5&&train.fuelTicks==fuel&&d.ticksSinceGaugeSync==sync,"Populated foreign results modified another dimension's train or storage");
            }
            d.accumulators=0;d.accumulatorCharge=0;
        }
        void meter(boolean powered){probe.inventoryTick(stack,h.getLevel(),player,0,true);float value=probe.getMeasurement(h.getLevel(),stack);h.assertTrue(Float.isFinite(value)&&(!powered||Math.abs(value)>1900),"Multimeter lost an accepted transformer reading: "+value+" tick="+h.getTick()+" trainV="+voltage+" spans="+spans.size()+" "+org.cha0scollective.wwpg.api.neoforge.ElectricalMeasurements.readVoltageBetween(h.getLevel(),h.absolutePos(core.east()),0,h.absolutePos(core.east()),1));}
        void place(BlockPos support,ItemStack item){if(h.getBlockState(support).isAir())h.setBlock(support,Blocks.STONE);var p=h.makeMockPlayer(GameType.CREATIVE);p.setItemInHand(InteractionHand.MAIN_HAND,item);var abs=h.absolutePos(support);h.assertTrue(item.getItem().useOn(new UseOnContext(p,InteractionHand.MAIN_HAND,new BlockHitResult(abs.getCenter().add(0,.5,0),Direction.UP,abs,false))).consumesAction(),"Native pole/holder placement failed");}
        void scroll(BlockPos p,int value){var setting=((com.simibubi.create.foundation.blockEntity.SmartBlockEntity)h.getBlockEntity(p)).getBehaviour(com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour.TYPE);setting.setValue(value);h.assertTrue(setting.getValue()==value,"Native transformer ratio clamped");}
        void wire(BlockPos a,int ai,BlockPos b,int bi){WiringGameTests.connect(h,h.absolutePos(a),ai,h.absolutePos(b),bi,false);}
        CreativeSourceBlockEntity supply(){return (CreativeSourceBlockEntity)h.getBlockEntity(source);}
        ElectricTrainData data(){return ((ICEETrainExtension)train).getElectricTrainData();}
        Vec3 position(){return carriage.getLeadingPoint().getPosition(graph);}
        void cleanup(){active=null;var state=InfrastructureSavedData.load(h.getLevel()).wireSimulationState;for(var handle:neighbors)if(handle.valid())state.invalidateHandle(handle);if(train!=null){Create.RAILWAYS.trains.remove(train.id);var entity=carriage.anyAvailableEntity();if(entity!=null)entity.discard();}}
    }
}
