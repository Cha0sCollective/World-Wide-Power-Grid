package org.cha0scollective.wwpg.gametest.cee12;

import com.george_vi.electroenergetics.*;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.content.transmission_distribution.transformer.TransformerDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import org.cha0scollective.wwpg.gametest.DynamicGameTests;
import org.cha0scollective.wwpg.gametest.WiringGameTests;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

/** Signed single-node samples must match the stock CEE control, not only the turns ratio. */
@GameTestHolder("wwpg_cee_transformer_reference") @PrefixGameTestTemplate(false)
public final class TransformerReferenceGameTests {
    private static final String BATCH="wwpg_cee_transformer_reference";
    @BeforeBatch(batch=BATCH) public static void configure(ServerLevel level){DynamicGameTests.substeps(level);}

    @GameTest(template="empty",batch=BATCH,timeoutTicks=130)
    public static void floatingGroundedAndReleasedSecondaryMatchesStock(GameTestHelper h) {
        var f=new Fixture(h); f.start();
        h.runAtTickTime(25,()->{f.expect(0,5000);f.wire(f.t,3,f.g,0);});
        h.runAtTickTime(45,()->{f.expect(-5000,0);f.unwire(f.t,3,f.g,0);});
        h.runAtTickTime(65,()->{f.expect(0,5000);h.succeed();});
    }

    @GameTest(template="empty",batch=BATCH,timeoutTicks=130)
    public static void primaryEarthDoesNotRemoveFloatingSecondaryReference(GameTestHelper h) {
        var f=new Fixture(h); f.start();
        h.runAtTickTime(15,()->f.wire(f.s,0,f.g,0));
        h.runAtTickTime(35,()->{f.expect(0,5000);f.wire(f.t,3,f.g,0);});
        h.runAtTickTime(55,()->{f.expect(-5000,0);f.unwire(f.t,3,f.g,0);});
        h.runAtTickTime(75,()->{f.expect(0,5000);h.succeed();});
    }

    @GameTest(template="empty",batch=BATCH,timeoutTicks=130)
    public static void nativePgBondJoinsWindingReferencesAndRemovalSeparatesThem(GameTestHelper h) {
        var f=new Fixture(h); f.start();
        final org.patryk3211.powergrid.electricity.sim.ElectricWire[] bond={null};
        h.runAtTickTime(25,()->{
            f.expect(0,5000);
            bond[0]=GlobalElectricNetworks.makeSimpleConnection(h.getLevel(),new BlockWireEndpoint(f.s,0),new BlockWireEndpoint(f.t,3),.001f);
        });
        h.runAtTickTime(45,()->{
            f.expect(-5000,0);
            org.cha0scollective.wwpg.bridge.OwnedWireRemoval.remove(bond[0],GlobalElectricNetworks.getWorldNetworks(h.getLevel()).globalGraph);
        });
        h.runAtTickTime(65,()->{f.expect(0,5000);h.succeed();});
    }

    @GameTest(template="empty",batch=BATCH,timeoutTicks=100)
    public static void cascadedIsolatedWindingsEachKeepStockReference(GameTestHelper h) {
        var f=new Fixture(h); f.start();
        var second=h.absolutePos(new BlockPos(7,2,2));
        h.getLevel().setBlockAndUpdate(second.below(),Blocks.STONE.defaultBlockState());
        h.getLevel().setBlockAndUpdate(second,CEEBlocks.TRANSFORMER.getDefaultState());
        h.runAtTickTime(8,()->{
            DevicesSavedData.load(h.getLevel()).getDevice(second,TransformerDevice.class).ratio=2.5;
            f.wire(f.t,2,second,0); f.wire(f.t,3,second,1);
        });
        h.runAtTickTime(35,()->{
            f.expect(0,5000);f.near(f.sample(second,2),0,"Cascaded secondary reference");
            f.near(f.sample(second,3),2000,"Cascaded secondary voltage");h.succeed();
        });
    }

    private static final class Fixture {
        final GameTestHelper h; final BlockPos s,t,g;
        Fixture(GameTestHelper h){this.h=h;s=h.absolutePos(new BlockPos(1,2,1));t=h.absolutePos(new BlockPos(4,2,3));g=h.absolutePos(new BlockPos(7,1,4));}
        void start(){
            for(var p:new BlockPos[]{s,t,g})h.getLevel().setBlockAndUpdate(p.below(),Blocks.STONE.defaultBlockState());
            h.getLevel().setBlockAndUpdate(s,CEEBlocks.CREATIVE_BATTERY.getDefaultState());
            h.getLevel().setBlockAndUpdate(t,CEEBlocks.TRANSFORMER.getDefaultState());
            h.getLevel().setBlockAndUpdate(g,CEEBlocks.GROUND_ROD.getDefaultState());
            h.runAtTickTime(5,()->{
                DevicesSavedData.load(h.getLevel()).getDevice(s,CreativeBatteryDevice.class).voltage=23000;
                DevicesSavedData.load(h.getLevel()).getDevice(t,TransformerDevice.class).ratio=4.6;
                wire(s,0,t,0);wire(s,1,t,1);
            });
        }
        void wire(BlockPos a,int ap,BlockPos b,int bp){WiringGameTests.connect(h,a,ap,b,bp,false);}
        void unwire(BlockPos a,int ap,BlockPos b,int bp){InfrastructureSavedData.load(h.getLevel()).removeConnection(new com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection(new InWorldNode(ap,a),new InWorldNode(bp,b)));}
        double sample(BlockPos p,int pin){
            var values=InfrastructureSavedData.load(h.getLevel()).ticker.lastResults.getVoltages(new InWorldNode(pin,p));
            h.assertTrue(values.length==16,"Missing full node sample history");
            for(double value:values)h.assertTrue(Double.isFinite(value),"Nonfinite transformer node sample");
            return values[values.length-1];
        }
        void near(double actual,double expected,String what){h.assertTrue(Math.abs(actual-expected)<.1,what+": expected "+expected+", got "+actual);}
        void expect(double first,double second){
            near(sample(s,0),0,"Battery reference");near(sample(s,1),23000,"Battery voltage");
            near(sample(t,2),first,"Secondary first node");near(sample(t,3),second,"Secondary second node");
            near(sample(t,3)-sample(t,2),5000,"Secondary differential");
            com.mojang.logging.LogUtils.getLogger().info("WWPG_TRANSFORMER_REFERENCE first={} second={} differential={}",sample(t,2),sample(t,3),sample(t,3)-sample(t,2));
        }
    }
}
