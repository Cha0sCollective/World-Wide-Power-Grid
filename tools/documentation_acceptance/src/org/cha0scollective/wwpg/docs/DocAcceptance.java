package org.cha0scollective.wwpg.docs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.cha0scollective.wwpg.api.*;
import org.cha0scollective.wwpg.api.neoforge.*;
import org.cha0scollective.wwpg.gametest.WiringGameTests;
import org.cha0scollective.wwpg.mixin.ElectricalNetworkAccessor;
import org.example.tutorialload.*;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.collections.ModdedConfigs;
import org.patryk3211.powergrid.config.CSolver;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import java.util.*;
import java.util.function.Supplier;
import java.nio.file.*;
import com.google.gson.GsonBuilder;

/** Test-only harness. The independent starter imports no WWPG internals or upstream classes. */
@Mod("wwpg_doc_acceptance")
@GameTestHolder("wwpg_doc")
@PrefixGameTestTemplate(false)
public final class DocAcceptance {
    private static final String ID = "wwpg_doc_acceptance";
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, ID);
    private static final Supplier<FaultBlock> FAULT = BLOCKS.register("mismatch", FaultBlock::new);
    private static final Map<BlockPos,DeviceResults> FAULT_RESULTS = new HashMap<>();
    private static final List<Terminal> GEOMETRY = List.of(new Terminal("positive",0,new LocalPoint(.25,.85,0),.12),new Terminal("negative",1,new LocalPoint(.75,.85,0),.12));
    public DocAcceptance(IEventBus bus) {
        if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT && Boolean.getBoolean("wwpg.doc.client")) new DocClientAcceptance();
        BLOCKS.register(bus);
        bus.addListener((RegisterCapabilitiesEvent event) -> ElectricalProviders.registerBlock(event,ID+":mismatch",
                (level,pos,state,entity,context) -> new ElectricalDevice() {
                    public String providerId() { return ID+":mismatch"; }
                    public List<Terminal> terminals() { return GEOMETRY; }
                    public CircuitDescription prepare(TickContext tick) {
                        var described = state.getValue(FaultBlock.WRONG_RADIUS) ? List.of(new Terminal("positive",0,GEOMETRY.getFirst().position(),.13),GEOMETRY.getLast()) : List.of(GEOMETRY.getLast(),GEOMETRY.getFirst());
                        return state.getValue(FaultBlock.REPAIRED) ? new CircuitDescription(described,List.of(),List.of(new Element("load","positive","negative",new ElectricalModel.Resistor(100))),Set.of("negative")) : CircuitDescription.empty();
                    }
                    public void commit(DeviceResults results) { FAULT_RESULTS.put(pos.immutable(),results); }
                }, FAULT.get()));
    }
    static final class FaultBlock extends Block {
        static final BooleanProperty REPAIRED=BooleanProperty.create("repaired"), WRONG_RADIUS=BooleanProperty.create("wrong_radius");
        FaultBlock() { super(BlockBehaviour.Properties.of()); registerDefaultState(stateDefinition.any().setValue(REPAIRED,false).setValue(WRONG_RADIUS,false)); }
        protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b) { b.add(REPAIRED,WRONG_RADIUS); }
    }
    private static void backend(GameTestHelper h) {
        var expected=CSolver.SolverBackend.valueOf(System.getProperty("wwpg.test.backend","NATIVE"));
        h.assertTrue(expected.isSupported(),"Requested backend unavailable");
        ModdedConfigs.server().electricity.solver.solverBackend.set(expected);
        ModdedConfigs.server().electricity.solver.multiTicks.set(16);
    }
    private static void activeBackend(GameTestHelper h) {
        var expected=CSolver.SolverBackend.valueOf(System.getProperty("wwpg.test.backend","NATIVE"));
        var types=GlobalElectricNetworks.getWorldNetworks(h.getLevel()).subnetworks.stream().filter(n->!n.isEmpty()).map(n->((ElectricalNetworkAccessor)n).wwpg$solver().type()).toList();
        h.assertTrue(!types.isEmpty()&&types.stream().allMatch(t->t==expected),"Actual backends differ: "+types);
    }
    private static void near(GameTestHelper h,double value,double expected,double tolerance,String label) { h.assertTrue(Double.isFinite(value)&&Math.abs(value-expected)<=tolerance,label+" expected="+expected+" actual="+value); }
    private static void source(GameTestHelper h,BlockPos pos) {
        h.getLevel().setBlockAndUpdate(pos,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get().defaultBlockState());
        var entity=(CreativeSourceBlockEntity)h.getLevel().getBlockEntity(pos);
        ((SmartBlockEntity)entity).getBehaviour(ScrollValueBehaviour.TYPE).setValue(20);
        entity.setValue(20);entity.setChanged();
    }
    private static void report(String name,Map<String,Object> values) {
        try {
            Files.createDirectories(Path.of("doc-evidence"));
            var data=new LinkedHashMap<String,Object>(values);data.put("backend",System.getProperty("wwpg.test.backend"));data.put("phase",System.getProperty("wwpg.doc.phase","SETUP"));data.put("passed",true);
            data.put("runtime_sha256",artifactHash("wwpg"));data.put("starter_sha256",artifactHash("tutorial_load"));
            Files.writeString(Path.of("doc-evidence",name+"-"+System.getProperty("wwpg.doc.phase","SETUP")+"-"+System.currentTimeMillis()+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(data));
        } catch(Exception e) { throw new IllegalStateException("Could not retain documentation evidence",e); }
    }
    private static String artifactHash(String modId) throws Exception {
        var path=net.neoforged.fml.ModList.get().getModFileById(modId).getFile().getFilePath();
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
    private static void meters(GameTestHelper h,BlockPos source,BlockPos load) {
        var player=h.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
        player.setPos(load.getCenter());
        var stack=org.patryk3211.powergrid.collections.ModdedItems.MULTIMETER.asStack();
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,stack);
        for(int terminal=0;terminal<2;terminal++) {
            var endpoint=new org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint(load,terminal);
            var hit=new net.minecraft.world.phys.BlockHitResult(endpoint.getExactPosition(h.getLevel()),net.minecraft.core.Direction.NORTH,load,false);
            var result=h.getLevel().getBlockState(load).useItemOn(stack,h.getLevel(),player,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
            h.assertTrue(result==net.minecraft.world.ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION,"Load intercepted the held native meter");
            h.assertTrue(stack.getItem().useOn(new net.minecraft.world.item.context.UseOnContext(player,net.minecraft.world.InteractionHand.MAIN_HAND,hit)).consumesAction(),"Meter rejected tutorial terminal");
        }
        var meter=(org.patryk3211.powergrid.equipment.multimeter.MultimeterItem)stack.getItem();
        meter.inventoryTick(stack,h.getLevel(),player,0,true);
        near(h,meter.getMeasurement(h.getLevel(),stack),20,.1,"Native multimeter on starter");
        var clampPlayer=h.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);clampPlayer.setPos(load.getCenter());
        var clamp=com.george_vi.electroenergetics.CEEItems.CLAMP_METER.asStack();clampPlayer.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,clamp);
        var wire=h.getLevel().getEntitiesOfClass(org.patryk3211.powergrid.electricity.wire.WireEntity.class,new net.minecraft.world.phys.AABB(source).inflate(7)).stream().filter(w->w.isConnectedTo(source,0)&&w.isConnectedTo(load,0)).findFirst().orElseThrow();
        h.assertTrue(wire.interact(clampPlayer,net.minecraft.world.InteractionHand.MAIN_HAND).consumesAction(),"Native CEE clamp rejected starter's PG wire");
        clamp.getItem().onUseTick(h.getLevel(),clampPlayer,clamp,9000);
        var data=clamp.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        near(h,Math.abs(data.getFloat(org.cha0scollective.wwpg.equipment.HandheldMeters.CURRENT)),.2,.002,"Native clamp current");
        clampPlayer.releaseUsingItem();
    }
    private static void control(GameTestHelper h,BlockPos pos,net.minecraft.core.Direction face,boolean sneak) {
        var player=h.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);player.setShiftKeyDown(sneak);
        var hit=new net.minecraft.world.phys.BlockHitResult(pos.getCenter().relative(face,.5),face,pos,false);
        h.assertTrue(h.getLevel().getBlockState(pos).useWithoutItem(h.getLevel(),player,hit).consumesAction(),"Native empty-hand control rejected");
    }
    @GameTest(template="empty",timeoutTicks=240)
    public static void controlsGeometryUnavailableAndBothWireSystems(GameTestHelper h) {
        backend(h);
        var level=h.getLevel();var supply=h.absolutePos(new BlockPos(1,2,1));var load=h.absolutePos(new BlockPos(5,2,1));
        source(h,supply);level.setBlockAndUpdate(load,TutorialLoadMod.LOAD.get().defaultBlockState());
        var device=(LoadDevice)level.getBlockEntity(load);
        var terminals=device.terminals();
        h.runAtTickTime(8,()-> { WiringGameTests.connect(h,supply,0,load,0,true);WiringGameTests.connect(h,supply,1,load,1,true); });
        h.runAtTickTime(25,()-> {
            h.assertTrue(level.getBlockState(load).getValue(LoadBlock.LIT),"Powered load is dark");meters(h,supply,load);
            near(h,ElectricalMeasurements.readVoltageBetween(level,load,0,load,1).voltage().orElseThrow().lastVoltageVolts(),20,.1,"Native terminal voltage");
            h.assertTrue(device.reading().contains("0.200")||device.reading().contains("0.199"),"100 ohm readout "+device.reading());
            control(h,load,net.minecraft.core.Direction.NORTH,false);
            h.assertTrue(device.enabled()&&device.resistanceOhms()==100,"Read-only front click changed settings");
            control(h,load,net.minecraft.core.Direction.NORTH,true);
            h.assertTrue(device.reading().startsWith("unavailable: waiting for next solve"),"Configuration reused an old measurement");
        });
        h.runAtTickTime(40,()-> { h.assertTrue(device.resistanceOhms()==200&&device.reading().contains("0.100"),"200 ohm readout "+device.reading());control(h,load,net.minecraft.core.Direction.NORTH,true); });
        h.runAtTickTime(55,()-> {
            h.assertTrue(device.resistanceOhms()==400&&device.reading().contains("0.050"),"400 ohm readout "+device.reading());
            control(h,load,net.minecraft.core.Direction.UP,false);
            h.assertTrue(new HashSet<>(device.prepare(new TickContext(0,.05,16)).terminals()).equals(new HashSet<>(terminals)),"Off state changed terminal records");
        });
        h.runAtTickTime(70,()-> {
            h.assertTrue(!level.getBlockState(load).getValue(LoadBlock.LIT)&&device.reading().contains("0.000 A"),"Off state conducted "+device.reading());
            device.commit(DeviceResults.unavailable(new TickContext(1,.05,16),MeasurementStatus.NOT_READY,"documentation fixture"));
            h.assertTrue(device.reading().startsWith("unavailable")&&!device.enabled()&&device.resistanceOhms()==400,"Unavailable result changed settings/readout");
            level.setBlockAndUpdate(load,Blocks.AIR.defaultBlockState());
        });
        h.runAtTickTime(85,()->level.setBlockAndUpdate(load,TutorialLoadMod.LOAD.get().defaultBlockState()));
        h.runAtTickTime(95,()-> { WiringGameTests.connect(h,supply,0,load,0,false);WiringGameTests.connect(h,supply,1,load,1,false); });
        h.runAtTickTime(120,()-> {
            var restored=(LoadDevice)level.getBlockEntity(load);
            h.assertTrue(restored.enabled()&&restored.resistanceOhms()==100,"Replacement did not start fresh");
            h.assertTrue(level.getBlockState(load).getValue(LoadBlock.LIT)&&restored.reading().contains("0.200"),"CEE wired load failed "+restored.reading());
            activeBackend(h);report("controls",Map.of("pg_wires",true,"cee_wires",true,"resistances",List.of(100,200,400),"unavailable_cleared",true,"replacement",true));h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=160)
    public static void mismatchIsReportedAndRepairRecovers(GameTestHelper h) {
        backend(h);var radiusPos=h.absolutePos(new BlockPos(4,2,2));
        h.getLevel().setBlockAndUpdate(radiusPos,FAULT.get().defaultBlockState().setValue(FaultBlock.REPAIRED,true).setValue(FaultBlock.WRONG_RADIUS,true));ElectricalDevices.changed(h.getLevel(),radiusPos);
        var pos=h.absolutePos(new BlockPos(2,2,2));h.getLevel().setBlockAndUpdate(pos,FAULT.get().defaultBlockState());ElectricalDevices.changed(h.getLevel(),pos);
        h.runAtTickTime(15,()-> {
            var results=FAULT_RESULTS.get(pos);
            h.assertTrue(results!=null&&results.status()==MeasurementStatus.ISOLATED,"Mismatch did not isolate provider");
            h.assertTrue(FAULT_RESULTS.get(radiusPos).status()==MeasurementStatus.ISOLATED,"Equal counts with a different hit radius were accepted");
            var diagnostic=org.cha0scollective.wwpg.extension.ApiCircuits.get(h.getLevel()).diagnostics().stream().filter(line->line.contains("Prepared terminals differ from read-only terminal geometry")).findFirst().orElseThrow();
            h.assertTrue(results.nodes().isEmpty()&&results.elements().isEmpty(),"Mismatch reused old measurements");
            report("diagnostic",Map.of("actual_diagnostic",diagnostic,"snapshot_detail",results.detail(),"status",results.status().toString()));
            h.getLevel().setBlockAndUpdate(pos,FAULT.get().defaultBlockState().setValue(FaultBlock.REPAIRED,true));ElectricalDevices.changed(h.getLevel(),pos);
            h.getLevel().setBlockAndUpdate(radiusPos,FAULT.get().defaultBlockState().setValue(FaultBlock.REPAIRED,true));ElectricalDevices.changed(h.getLevel(),radiusPos);
        });
        h.runAtTickTime(35,()-> { h.assertTrue(FAULT_RESULTS.get(pos).status()==MeasurementStatus.VALID&&FAULT_RESULTS.get(radiusPos).status()==MeasurementStatus.VALID,"Reordered repaired terminal sets did not recover");report("repair",Map.of("status","VALID"));h.succeed(); });
    }
    private static final BlockPos SAVED=new BlockPos(16005,64,16000);
    @GameTest(template="empty",timeoutTicks=160)
    public static void savedSettingsSurviveColdRestartBeforeFirstSolve(GameTestHelper h) {
        backend(h);var level=h.getLevel();level.getChunk(SAVED);level.setChunkForced(SAVED.getX()>>4,SAVED.getZ()>>4,true);
        boolean verify=System.getProperty("wwpg.doc.phase","SETUP").equals("VERIFY");
        if(verify) {
            h.assertTrue(level.getBlockEntity(SAVED) instanceof LoadDevice,"Saved starter block missing");
            var loaded=(LoadDevice)level.getBlockEntity(SAVED);
            h.assertTrue(!loaded.enabled()&&loaded.resistanceOhms()==400,"Saved OFF / 400 ohm settings were lost before solve");
            h.assertTrue(loaded.reading().startsWith("unavailable"),"Settings were checked after first solve");
        } else {
            level.setBlockAndUpdate(SAVED,TutorialLoadMod.LOAD.get().defaultBlockState());
            var loaded=(LoadDevice)level.getBlockEntity(SAVED);loaded.toggleEnabled();loaded.cycleResistance();loaded.cycleResistance();
        }
        h.runAtTickTime(25,()-> {
            var loaded=(LoadDevice)level.getBlockEntity(SAVED);
            h.assertTrue(!loaded.enabled()&&loaded.resistanceOhms()==400,"Saved configuration advanced/reset");
            level.getDataStorage().save();level.getChunkSource().save(true);
            report("saved-settings",Map.of("enabled",loaded.enabled(),"resistance",loaded.resistanceOhms(),"checked_before_first_solve",verify));h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=1200)
    public static void actualChunkReloadKeepsSettingsAndConnectedWires(GameTestHelper h) {
        backend(h);var level=h.getLevel();var supply=new BlockPos(24000,64,24000);var load=supply.east(5);
        var chunk=new net.minecraft.world.level.ChunkPos(supply);var unloads=new java.util.concurrent.atomic.AtomicInteger();
        java.util.function.Consumer<net.neoforged.neoforge.event.level.ChunkEvent.Unload> listener=event->{
            if(event.getLevel()==level&&event.getChunk().getPos().equals(chunk))unloads.incrementAndGet();
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.level.ChunkEvent.Unload.class,listener);
        level.setChunkForced(chunk.x,chunk.z,true);level.getChunkAt(supply);
        for(var at:List.of(supply,load)){level.setBlockAndUpdate(at,Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(at.below(),Blocks.STONE.defaultBlockState());}
        source(h,supply);level.setBlockAndUpdate(load,TutorialLoadMod.LOAD.get().defaultBlockState());
        h.startSequence().thenIdle(10).thenExecute(()->{
            WiringGameTests.connect(h,supply,0,load,0,true);WiringGameTests.connect(h,supply,1,load,1,true);
            var device=(LoadDevice)level.getBlockEntity(load);device.toggleEnabled();device.cycleResistance();device.cycleResistance();
        }).thenIdle(20).thenExecute(()->{
            level.getDataStorage().save();level.getChunkSource().save(true);level.setChunkForced(chunk.x,chunk.z,false);
        }).thenIdle(330).thenWaitUntil(()->{
            level.getChunkSource().tick(()->true,true);
            h.assertTrue(level.getChunkSource().getChunkNow(chunk.x,chunk.z)==null&&unloads.get()>0,"Waiting for actual unload");
        }).thenExecute(()->{
            h.assertTrue(ElectricalMeasurements.read(level,load).status()==MeasurementStatus.NOT_READY,"Unloaded device exposed stale results");
            level.setChunkForced(chunk.x,chunk.z,true);level.getChunkAt(load);
            var device=(LoadDevice)level.getBlockEntity(load);
            h.assertTrue(!device.enabled()&&device.resistanceOhms()==400,"Reload lost saved configuration before first solve");
            h.assertTrue(device.reading().startsWith("unavailable"),"Reload check occurred after solving");
            device.toggleEnabled();
        }).thenWaitUntil(()->{
            var device=(LoadDevice)level.getBlockEntity(load);
            h.assertTrue(level.getBlockState(load).getValue(LoadBlock.LIT)&&device.reading().contains("0.050"),"Waiting for restored connected circuit "+device.reading());
        }).thenExecute(()->{
            activeBackend(h);report("chunk-reload",Map.of("actual_unloads",unloads.get(),"settings_before_solve",true,"restored_wires_power_load",true));
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);level.setChunkForced(chunk.x,chunk.z,false);
        }).thenSucceed();
    }

}
