package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.collections.ModdedItems;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.base.ThermalBehaviour;
import org.patryk3211.powergrid.electricity.deviceconnector.DeviceConnectorBlock;
import org.patryk3211.powergrid.electricity.electricswitch.SwitchBlock;
import org.patryk3211.powergrid.electricity.electricswitch.SwitchBlockEntity;
import org.patryk3211.powergrid.electricity.light.fixture.AbstractLightFixtureBlockEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.cha0scollective.wwpg.gametest.ShowroomTools.*;

/** Native playable exhibits, with an explicit record separate from cabinet stock. */
final class StationaryYardStations {
    record Station(String code, String name, BlockPos origin, List<String> content, List<String> instructions) {
        BlockPos source() { return origin; }
        BlockPos control() { return origin.east(3); }
        BlockPos device() { return origin.east(7); }
        BlockPos meter() { return origin.offset(11,0,4); }
    }
    final GameTestHelper h;
    final boolean restore;
    final List<Station> stations = new ArrayList<>();
    final List<Runnable> checks = new ArrayList<>();
    final List<Runnable> configure = new ArrayList<>();

    StationaryYardStations(GameTestHelper h, boolean restore) {
        this.h = h; this.restore = restore;
        for (int x = 8; x <= 17; x++) for (int z = 0; z <= 20; z++) {
            h.getLevel().setChunkForced(x,z,true); h.getLevel().getChunk(x,z);
        }
        if (!restore) {
            for (int x = 130; x <= 283; x++) for (int z = 3; z <= 313; z++)
                put(h, new BlockPos(x,63,z), x == 142 ? Blocks.WHITE_CONCRETE : Blocks.SMOOTH_STONE);
            for (int x = 26; x <= 283; x++) put(h,new BlockPos(x,63,240),Blocks.WHITE_CONCRETE);
            sign(h,new BlockPos(142,64,16),"STATIONARY YARD","New live stations","Switch beside feed","Read each sign");
        }
        lamps(); equipment(); heating();
        new StationaryYardEquipment(this).build();
        new StationaryYardDistribution(this).build();
        new StationaryYardInstruments(this).build();
        new StationaryYardGeneration(this).build();
        new StationaryYardControls(this).build();
        new StationaryYardProtection(this).build();
        new StationaryYardHeldParts(this).build();
        new StationaryYardConnections(this).build();
        new StationaryYardNether(this).build();
        new StationaryYardProcessing(this).build();
        if (net.neoforged.fml.ModList.get().isLoaded("pinout") && net.neoforged.fml.ModList.get().isLoaded("computercraft"))
            StationaryYardPinout.build(this);
        if (!restore) h.runAtTickTime(20, () -> configure.forEach(Runnable::run));
    }

    Station station(String name, List<String> content, String... instructions) {
        int index = stations.size();
        var s = new Station("S"+String.format("%02d",index+1), name,
                new BlockPos(152+(index%5)*26,64,8+(index/5)*18), content, List.of(instructions));
        stations.add(s);
        if (!restore) {
            sign(h,s.origin().south(8),s.code()+" "+(name.length()>12?name.substring(0,12):name),instructions.length>0?instructions[0]:"",
                    instructions.length>1?instructions[1]:"",instructions.length>2?instructions[2]:"");
            sign(h,s.control().south(2),"FEED SWITCH","Empty-hand click","ON / OFF","Stop to repair");
        }
        return s;
    }
    void state(BlockPos p, BlockState state) { if (!restore) h.getLevel().setBlockAndUpdate(p,state); }
    void block(BlockPos p, Block block) { state(p,block.defaultBlockState()); }
    void source(BlockPos p, double voltage) {
        if (CEEBlocks.CREATIVE_BATTERY.has(h.getLevel().getBlockState(p))) scroll(h,p,(int)Math.round(voltage*1000));
        else pgSource(h,p,voltage);
    }
    void feed(Station s, boolean pgSource) {
        block(s.source(),pgSource?ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get():CEEBlocks.CREATIVE_BATTERY.get());
        state(s.control(),ModdedBlocks.MV_SWITCH.getDefaultState().setValue(SwitchBlock.OPEN,false));
    }
    void switchedPair(Station s, BlockPos terminal, double voltage, boolean pgSource) {
        source(s.source(),voltage);
        ((SwitchBlockEntity)h.getLevel().getBlockEntity(s.control())).setState(true);
        wire(h,s.source(),pgSource?0:1,s.control(),0,true);
        wire(h,s.control(),1,terminal,0,false);
        wire(h,s.source(),pgSource?1:0,terminal,1,true);
        blockMeter(s,terminal,0,1,voltage);
    }
    void blockMeter(Station s, BlockPos terminal, int positive, int negative, double expected) {
        // Meter placement happens during construction, never while restoring.
        if (h.getLevel().getBlockEntity(s.meter()) == null) block(s.meter(),ModdedBlocks.VOLTAGE_METER.get());
        scroll(h,s.meter(),expected>200?3:expected>20?2:1);
        wire(h,terminal,positive,s.meter(),0,false); wire(h,terminal,negative,s.meter(),1,true);
    }
    void prepareMeter(Station s) { block(s.meter(),ModdedBlocks.VOLTAGE_METER.get()); }
    void check(Station s, Runnable check) {
        checks.add(() -> { try { check.run(); } catch (RuntimeException error) { h.fail(s.code()+" "+s.name()+": "+error.getMessage()); } });
    }

    private void lamps() {
        var blocks = new Block[]{ModdedBlocks.LIGHT_FIXTURE.get(),ModdedBlocks.CEILING_TILE_LAMP.get(),ModdedBlocks.FACTORY_LIGHT.get()};
        for (var block : blocks) {
            boolean factory=block==ModdedBlocks.FACTORY_LIGHT.get();
            var s = station(block.getName().getString(),factory?List.of("powergrid:factory_light","powergrid:factory_light_light"):List.of(BuiltInRegistries.BLOCK.getKey(block).toString()),"Click feed: light","120 V when ON","Replace native bulb");
            var lamp=factory?s.device().above(3):s.device();
            feed(s,false); block(lamp,block); prepareMeter(s);
            var terminal = factory ? lamp.above() : lamp;
            if (factory) state(terminal,ModdedBlocks.DEVICE_CONNECTOR.getDefaultState().setValue(DeviceConnectorBlock.FACING,Direction.DOWN));
            configure.add(() -> {
                var player = h.makeMockPlayer(GameType.SURVIVAL); var bulb = ModdedItems.LIGHT_BULB.asStack();
                player.setItemInHand(InteractionHand.MAIN_HAND,bulb);
                h.assertTrue(((AbstractLightFixtureBlockEntity)h.getLevel().getBlockEntity(lamp)).replaceBulb(player,InteractionHand.MAIN_HAND,bulb)&&bulb.isEmpty(),"Native bulb installation failed");
                switchedPair(s,terminal,120,false);
            });
            check(s,() -> {
                h.assertTrue(((AbstractLightFixtureBlockEntity)h.getLevel().getBlockEntity(lamp)).getPowerLevel()>0,"Lamp is dark");
                if(factory) h.assertTrue(ModdedBlocks.FACTORY_LIGHT_LIGHT.has(h.getLevel().getBlockState(lamp.below())),"Factory projection has no light blocks");
            });
        }
    }

    private void equipment() {
        var blocks = new Block[]{ModdedBlocks.ELECTRIC_FAN.get(),ModdedBlocks.ELECTROMAGNET.get(),ModdedBlocks.ALARM_BELL.get(),CEEBlocks.BUZZER.get(),ModdedBlocks.CONSTANT_SPEED_MOTOR.get()};
        for (var block : blocks) {
            boolean cee = block == CEEBlocks.BUZZER.get();
            var s = station(block.getName().getString(),List.of(BuiltInRegistries.BLOCK.getKey(block).toString()),"Feed controls output","Goggles: V/current","Switch OFF to stop");
            feed(s,cee); block(s.device(),block); prepareMeter(s);
            var terminal = block == ModdedBlocks.ELECTROMAGNET.get() ? s.device().above() : s.device();
            if (!terminal.equals(s.device())) state(terminal,ModdedBlocks.DEVICE_CONNECTOR.getDefaultState().setValue(DeviceConnectorBlock.FACING,Direction.DOWN));
            configure.add(() -> {
                double voltage = cee || block==ModdedBlocks.CONSTANT_SPEED_MOTOR.get() ? 100 : ((ElectricBlockEntity)h.getLevel().getBlockEntity(s.device())).resistance()*(block==ModdedBlocks.ELECTROMAGNET.get()?3:1);
                switchedPair(s,terminal,voltage,cee);
            });
            if (block==ModdedBlocks.ELECTRIC_FAN.get()) check(s,() -> h.assertTrue(Math.abs(((org.patryk3211.powergrid.electricity.fan.ElectricFanBlockEntity)h.getLevel().getBlockEntity(s.device())).getSpeed())>50,"Fan has no airflow"));
            if (block==ModdedBlocks.ELECTROMAGNET.get()) check(s,() -> h.assertTrue(((org.patryk3211.powergrid.electricity.electromagnet.ElectromagnetBlockEntity)h.getLevel().getBlockEntity(s.device())).getFieldStrength()>.25,"Magnet has no field"));
            if (block==ModdedBlocks.ALARM_BELL.get()) check(s,() -> h.assertTrue(((org.patryk3211.powergrid.electricity.bell.AlarmBellBlockEntity)h.getLevel().getBlockEntity(s.device())).getVolume()>1,"Bell is silent"));
            if (cee) check(s,() -> h.assertTrue(h.getLevel().getBlockEntity(s.device()).saveWithoutMetadata(h.getLevel().registryAccess()).getDouble("Voltage")>99,"Buzzer has no audio voltage"));
            if (block==ModdedBlocks.CONSTANT_SPEED_MOTOR.get()) check(s,() -> h.assertTrue(Math.abs(((org.patryk3211.powergrid.kinetics.motor.ConstantSpeedMotorBlockEntity)h.getLevel().getBlockEntity(s.device())).getGeneratedSpeed())>0,"Motor is stopped"));
        }
    }

    private void heating() {
        var blocks = new Block[]{ModdedBlocks.HEATING_COIL.get(),ModdedBlocks.BASIN_HEATER.get()};
        for (var block : blocks) {
            var s = station(block.getName().getString(),List.of(BuiltInRegistries.BLOCK.getKey(block).toString(),"powergrid:thermometer"),"Feed heats the basin","Thermometer: Celsius","OFF cools; max stays");
            feed(s,false); block(s.device(),block); prepareMeter(s);
            var terminal=s.device().east(); state(terminal,ModdedBlocks.DEVICE_CONNECTOR.getDefaultState().setValue(DeviceConnectorBlock.FACING,Direction.WEST));
            var thermometer=s.device().south(); state(thermometer,ModdedBlocks.THERMOMETER.getDefaultState().setValue(org.patryk3211.powergrid.equipment.thermometer.ThermometerBlock.FACING,Direction.NORTH));
            block(s.device().above(),AllBlocks.BASIN.get());
            configure.add(() -> {
                var device=(ElectricBlockEntity)h.getLevel().getBlockEntity(s.device());
                double power=block==ModdedBlocks.BASIN_HEATER.get()?org.patryk3211.powergrid.electricity.basinheater.BasinHeaterBlockEntity.power()*1.25:
                        org.patryk3211.powergrid.config.ThermalValues.getPower(ModdedBlocks.HEATING_COIL.get())*.7;
                double voltage=Math.sqrt(device.resistance()*power);
                switchedPair(s,terminal,voltage,false);
            });
            check(s,() -> {
                var temperature=((SmartBlockEntity)h.getLevel().getBlockEntity(s.device())).getBehaviour(ThermalBehaviour.TYPE).getTemperature();
                h.assertTrue(temperature>100,"Heater is cold");
                near(h,((org.patryk3211.powergrid.equipment.thermometer.ThermometerBlockEntity)h.getLevel().getBlockEntity(thermometer)).temperature(),temperature,.01,"Thermometer Celsius reading");
            });
        }
    }

    void verifyAndRecord() {
        checkAll(h,checks.toArray(Runnable[]::new));
        ExampleWorldGameTests.cleanDroppedWireItems(h.getLevel(),new net.minecraft.world.phys.AABB(0,-64,0,284,320,314));
        var nether=h.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        ExampleWorldGameTests.cleanDroppedWireItems(nether,new net.minecraft.world.phys.AABB(0,-64,0,64,320,64));
        var report=new JsonObject(); report.addProperty("schema",1); report.addProperty("target","0.1.0-beta.5");
        report.addProperty("stationary_completion",false); report.addProperty("backend",System.getProperty("wwpg.test.backend","NATIVE"));
        var rows=new JsonArray();
        for (var s:stations) {
            var row=new JsonObject(); row.addProperty("code",s.code()); row.addProperty("name",s.name());
            row.addProperty("dimension",h.getLevel().dimension().location().toString()); row.addProperty("x",s.origin().getX()); row.addProperty("y",64); row.addProperty("z",s.origin().getZ());
            row.add("content",new com.google.gson.Gson().toJsonTree(s.content())); row.add("instructions",new com.google.gson.Gson().toJsonTree(s.instructions())); row.addProperty("checked",true); rows.add(row);
        }
        report.add("stations",rows);
        try { Files.writeString(Path.of("stationary-yard-stations.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n"); }
        catch(java.io.IOException error) { throw new IllegalStateException(error); }
    }
}
