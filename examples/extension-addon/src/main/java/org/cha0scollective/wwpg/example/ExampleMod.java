package org.cha0scollective.wwpg.example;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.cha0scollective.wwpg.api.*;
import org.cha0scollective.wwpg.api.neoforge.ElectricalCapabilities;
import org.cha0scollective.wwpg.api.neoforge.ElectricalProviders;
import org.cha0scollective.wwpg.api.neoforge.ElectricalDevices;
import java.util.*;
import java.util.function.Supplier;

/** This project compiles against the published API and NeoForge, never WWPG/PG/CEE implementation. */
@Mod(ExampleMod.ID)
public final class ExampleMod {
    public static final String ID="wwpg_api_example";
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(Registries.BLOCK,ID);
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(Registries.ITEM,ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,ID);
    private static final List<Supplier<ExampleBlock>> MACHINES=new ArrayList<>();
    private static final List<Supplier<Item>> MACHINE_ITEMS=new ArrayList<>();
    static {
        for(var kind:Kind.values()) {
            var block=BLOCKS.register(kind.name().toLowerCase(Locale.ROOT),()->new ExampleBlock(kind));
            MACHINES.add(block);
            MACHINE_ITEMS.add(ITEMS.register(kind.name().toLowerCase(Locale.ROOT),()->new BlockItem(block.get(),new Item.Properties())));
        }
    }
    public static final Supplier<BlockEntityType<Machine>> MACHINE=TYPES.register("machine",()->BlockEntityType.Builder.of(Machine::new,
            MACHINES.stream().map(Supplier::get).toArray(Block[]::new)).build(null));
    public ExampleMod(IEventBus bus) {
        BLOCKS.register(bus);ITEMS.register(bus);TYPES.register(bus);
        bus.addListener(ExampleMod::capabilities);
        bus.addListener(ExampleMod::creative);
        if (Boolean.getBoolean("wwpg.test.packaged")) FaultFixtures.register(bus);
    }
    private static void capabilities(RegisterCapabilitiesEvent event) {
        for(var block:MACHINES) {
            var target=block.get();
            ElectricalProviders.registerBlock(event,ID+":"+target.kind.name().toLowerCase(Locale.ROOT),
                    (level,pos,state,entity,context)->entity instanceof Machine machine?machine:null,target);
        }
    }
    private static void creative(BuildCreativeModeTabContentsEvent event) {
        if(event.getTabKey()==CreativeModeTabs.REDSTONE_BLOCKS)MACHINE_ITEMS.forEach(item->event.accept(item.get()));
    }
    enum Kind { LOAD,SOURCE,AC_SOURCE,CAPACITOR,INDUCTOR,DIODE,TRANSFORMER,VOLTAGE_CONTROL,CURRENT_CONTROL }
    static final class ExampleBlock extends Block implements EntityBlock {
        static final BooleanProperty LIT=BooleanProperty.create("lit"), ENABLED=BooleanProperty.create("enabled");
        final Kind kind;
        ExampleBlock(Kind kind) {
            super(BlockBehaviour.Properties.of().strength(2).lightLevel(s->s.getValue(LIT)?15:0));this.kind=kind;
            registerDefaultState(stateDefinition.any().setValue(LIT,false).setValue(ENABLED,true));
        }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b) { b.add(LIT,ENABLED); }
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return new Machine(pos,state); }
        @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) {
            if(!level.isClientSide) {
                if(player.isShiftKeyDown())level.setBlock(pos,state.cycle(ENABLED),3);
                if(level.getBlockEntity(pos) instanceof Machine machine) {
                    machine.setChanged();player.displayClientMessage(Component.literal(machine.reading),false);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
    }
    public static final class Machine extends BlockEntity implements ElectricalDevice {
        private double retainedVoltage,retainedCurrent;
        private long lastCommit=Long.MIN_VALUE,commitCount;
        String reading="Awaiting electrical power";
        Machine(BlockPos pos,BlockState state) { super(MACHINE.get(),pos,state); }
        @Override public void onLoad() {
            super.onLoad();
            if(level instanceof net.minecraft.server.level.ServerLevel server)ElectricalDevices.changed(server,worldPosition);
        }
        @Override public String providerId() { return ID+":"+((ExampleBlock)getBlockState().getBlock()).kind.name().toLowerCase(Locale.ROOT); }
        @Override public List<Terminal> terminals() {
            var terminals=new ArrayList<>(List.of(new Terminal("positive",0,new LocalPoint(.25,.85,0),.12),
                    new Terminal("negative",1,new LocalPoint(.75,.85,0),.12)));
            var kind=((ExampleBlock)getBlockState().getBlock()).kind;
            if(kind==Kind.TRANSFORMER||kind==Kind.VOLTAGE_CONTROL||kind==Kind.CURRENT_CONTROL) {
                terminals.add(new Terminal("secondary_positive",2,new LocalPoint(.25,.15,0),.12));
                terminals.add(new Terminal("secondary_negative",3,new LocalPoint(.75,.15,0),.12));
            }
            return List.copyOf(terminals);
        }
        @Override public CircuitDescription prepare(TickContext context) {
            var elements=new ArrayList<Element>();var nodes=new ArrayList<InternalNode>();
            boolean enabled=getBlockState().getValue(ExampleBlock.ENABLED);
            var kind=((ExampleBlock)getBlockState().getBlock()).kind;
            switch(kind) {
                case LOAD -> {
                    elements.add(new Element("load_a","positive","negative",new ElectricalModel.Resistor(enabled?100:1e12)));
                    elements.add(new Element("load_b","positive","negative",new ElectricalModel.Resistor(enabled?100:1e12)));
                }
                case SOURCE -> elements.add(new Element("supply","positive","negative",new ElectricalModel.VoltageSource(enabled?20:0)));
                case AC_SOURCE -> elements.add(new Element("supply","positive","negative",new ElectricalModel.AcVoltageSource(enabled?20:0,50,0,0)));
                case CAPACITOR -> {
                    nodes.add(new InternalNode("stored"));
                    elements.add(new Element("ballast","positive","stored",new ElectricalModel.Resistor(100)));
                    elements.add(new Element("storage","stored","negative",new ElectricalModel.Capacitor(.01,retainedVoltage)));
                    elements.add(new Element("discharge","stored","negative",new ElectricalModel.Resistor(enabled?1e12:100)));
                }
                case INDUCTOR -> {
                    nodes.add(new InternalNode("coil"));
                    elements.add(new Element("ballast","positive","coil",new ElectricalModel.Resistor(100)));
                    elements.add(new Element("storage","coil","negative",new ElectricalModel.Inductor(1,retainedCurrent)));
                }
                case DIODE -> elements.add(new Element("junction","positive","negative",new ElectricalModel.Diode(1e-9,1,22,1)));
                case TRANSFORMER -> elements.add(new Element("coupling","positive","negative",new ElectricalModel.Transformer("secondary_positive","secondary_negative",enabled?2:.5)));
                case VOLTAGE_CONTROL -> {
                    elements.add(new Element("input","positive","negative",new ElectricalModel.Resistor(1e6)));
                    elements.add(new Element("controlled","secondary_positive","secondary_negative",new ElectricalModel.ControlledVoltageSource("positive","negative",enabled?2:0)));
                }
                case CURRENT_CONTROL -> {
                    elements.add(new Element("input","positive","negative",new ElectricalModel.Resistor(1e6)));
                    elements.add(new Element("controlled","secondary_positive","secondary_negative",new ElectricalModel.ControlledCurrentSource("positive","negative",enabled?.01:0)));
                }
            }
            return new CircuitDescription(terminals(),nodes,elements,Set.of());
        }
        @Override public void commit(DeviceResults results) {
            if(lastCommit==results.context().gameTime())throw new IllegalStateException("Duplicate API gameplay commit");
            lastCommit=results.context().gameTime();++commitCount;
            boolean lit=false;
            if(results.status()==MeasurementStatus.VALID) {
                results.electricalState().values().forEach(state->{
                    if(state instanceof ElementState.Capacitor c)retainedVoltage=c.voltageVolts();
                    if(state instanceof ElementState.Inductor l)retainedCurrent=l.currentAmps();
                });
                double watts=results.elements().values().stream().mapToDouble(ElementResult::meanPowerWatts).sum();
                double volts=results.node("positive").orElseThrow().lastVoltageVolts()-results.node("negative").orElseThrow().lastVoltageVolts();
                reading=String.format(Locale.ROOT,"%.3f V, %.3f W; stored %.3f V / %.3f A",volts,watts,retainedVoltage,retainedCurrent);
                lit=Math.abs(watts)>.05||Math.abs(retainedVoltage)>1;
            } else reading="Electrical reading unavailable: "+results.status();
            if(level!=null&&!level.isClientSide&&getBlockState().getValue(ExampleBlock.LIT)!=lit)
                level.setBlock(worldPosition,getBlockState().setValue(ExampleBlock.LIT,lit),3);
            setChanged();
        }
        @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries) {
            super.saveAdditional(tag,registries);tag.putInt("WWPGExampleStateVersion",1);tag.putDouble("StoredVoltage",retainedVoltage);
            tag.putDouble("StoredCurrent",retainedCurrent);tag.putLong("CommitCount",commitCount);
        }
        @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries) {
            super.loadAdditional(tag,registries);
            if(tag.contains("WWPGExampleStateVersion")&&tag.getInt("WWPGExampleStateVersion")!=1)throw new IllegalArgumentException("Unknown example storage format");
            retainedVoltage=tag.getDouble("StoredVoltage");retainedCurrent=tag.getDouble("StoredCurrent");commitCount=tag.getLong("CommitCount");
            if(!Double.isFinite(retainedVoltage)||!Double.isFinite(retainedCurrent))throw new IllegalArgumentException("Non-finite example saved history");
        }
    }
}
