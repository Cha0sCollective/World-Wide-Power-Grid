package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.events.AddToElectricGraphEvent;
import com.george_vi.electroenergetics.foundation.nodes.AttachedNode;
import com.george_vi.electroenergetics.foundation.nodes.Node;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.api.cee.*;
import java.util.function.Consumer;

@GameTestHolder("wwpg_audit_endpoints")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid="wwpg_tests",bus=EventBusSubscriber.Bus.MOD)
public final class AuditEndpointGameTests {
    public static final class Property extends ElectricalProperties {
        int stored,beforeCalls,prepareCalls;boolean fail,failCapture;
        Property(){super(100,0,0);}
    }
    private static final class UnsupportedNode extends Node { }
    @SubscribeEvent public static void register(RegisterCeePropertyAdaptersEvent event) {
        if(!System.getProperty("neoforge.enabledGameTestNamespaces","").contains("wwpg_audit_endpoints"))return;
        event.register("wwpg_tests:audit",Property.class,new CeePropertyAdapter<>() {
            public CeeElectricalParameters prepare(Property p){++p.prepareCalls;if(p.failCapture){++p.stored;throw new IllegalStateException("Audit deliberate capture fault");}return new CeeElectricalParameters(.01,0,0,false);}
            public boolean dynamic(){return true;}
            public CeeStateCheckpoint checkpoint(Property p){int stored=p.stored;return ()->p.stored=stored;}
            public void beforeSubstep(Property p,CeeSubstepContext context){++p.beforeCalls;++p.stored;if(p.fail)throw new IllegalStateException("Audit deliberate preparation fault");}
        });
    }
    @GameTest(template="empty",timeoutTicks=80)
    public static void missingEndpointIsRejectedBeforeCallbacks(GameTestHelper h){endpoint(h,false,false);}
    @GameTest(template="empty",timeoutTicks=80)
    public static void missingEndpointAndThrowingCallbackStayIsolated(GameTestHelper h){endpoint(h,true,false);}
    @GameTest(template="empty",timeoutTicks=80)
    public static void explicitIsolationRollsBackBinding(GameTestHelper h){endpoint(h,false,true);}
    @GameTest(template="empty",timeoutTicks=80)
    public static void knownEndpointCallbackFailureRollsBack(GameTestHelper h){endpoint(h,true,false,false,false);}
    @GameTest(template="empty",timeoutTicks=80)
    public static void captureFailureRollsBack(GameTestHelper h){endpoint(h,false,false,false,true);}
    private static void endpoint(GameTestHelper h,boolean fail,boolean explicit) {
        endpoint(h,fail,explicit,!explicit,false);
    }
    private static void endpoint(GameTestHelper h,boolean fail,boolean explicit,boolean missing,boolean captureFailure) {
        var p=new Property();p.fail=fail;p.failCapture=captureFailure;
        var source=new net.minecraft.core.BlockPos(1,2,1);var load=new net.minecraft.core.BlockPos(4,2,1);
        h.setBlock(source,org.patryk3211.powergrid.collections.ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
        h.setBlock(load,org.patryk3211.powergrid.collections.ModdedBlocks.CREATIVE_RESISTOR.get());
        h.runAtTickTime(5,()->{
            ((org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity)h.getBlockEntity(source)).setValue(10);
            ((org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity)h.getBlockEntity(load)).setValue(10);
            for(int port=0;port<2;++port)WiringGameTests.connect(h,h.absolutePos(source),port,h.absolutePos(load),port,true);
        });
        var owner="audit:"+h.absolutePos(net.minecraft.core.BlockPos.ZERO);
        var first=new AttachedNode(0,owner);var second=new AttachedNode(1,owner);
        var unsupported=new UnsupportedNode();boolean[] repaired={false};
        Consumer<AddToElectricGraphEvent> listener=event->{
            if(event.level!=h.getLevel())return;
            event.builder.connect(!repaired[0]&&missing?unsupported:first,second,p);
            if(!repaired[0]&&explicit)event.builder.connect(first,new AttachedNode(2,owner),new ElectricalProperties(10,0,0){});
        };
        NeoForge.EVENT_BUS.addListener(AddToElectricGraphEvent.class,listener);
        h.runAtTickTime(20,()->{
            try {
                h.assertTrue(p.stored==0,"Rejected adapter advanced stored state");
                if(missing)h.assertTrue(p.beforeCalls==0&&p.prepareCalls==0,"Unsupported endpoint invoked adapter preparation");
                var positive=new org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint(h.absolutePos(load),0).getNode(h.getLevel());
                var negative=new org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint(h.absolutePos(load),1).getNode(h.getLevel());
                BoardComponentGameTests.near(h,positive.getVoltage()-negative.getVoltage(),10,.01,"Healthy circuit beside rejected adapter");
                repaired[0]=true;p.fail=p.failCapture=false;
            } catch(Throwable error){NeoForge.EVENT_BUS.unregister(listener);throw error;}
        });
        h.runAtTickTime(45,()->{
            NeoForge.EVENT_BUS.unregister(listener);
            h.assertTrue(p.stored>0&&p.beforeCalls>0,"Supported replacement did not resume adapter callbacks");
            h.succeed();
        });
    }
}
