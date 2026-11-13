package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.events.AddToElectricGraphEvent;
import com.george_vi.electroenergetics.foundation.nodes.AttachedNode;
import com.george_vi.electroenergetics.simulation.electrical_properties.*;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.minecraft.gametest.framework.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.*;
import org.cha0scollective.wwpg.api.cee.*;
import org.cha0scollective.wwpg.bridge.CeeResultsAvailability;
import org.cha0scollective.wwpg.compat.cee.CeeCompatibility;
import java.util.function.Consumer;

/** Exact-class addon registration across both native source conventions. */
@GameTestHolder("wwpg_cee_adapters")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid="wwpg_tests",bus=EventBusSubscriber.Bus.MOD)
public final class CeeAdapterReadinessGameTests {
    public static final class Source extends MicroTickingElectricalProperties {
        double lastCaptured;
        Source(){resistance=1;drive(10);}
        void drive(double voltage){currentSource=CeeCompatibility.modern()?voltage:-voltage;}
        public void tick(double[] voltages,int step,int total,int a,int b){}
    }
    private static boolean nonlinearRejected;
    @SubscribeEvent public static void register(RegisterCeePropertyAdaptersEvent event){
        if(!System.getProperty("neoforge.enabledGameTestNamespaces","").contains("wwpg_cee_adapters"))return;
        event.register("wwpg_tests:signed_source",Source.class,new CeePropertyAdapter<>(){
            public CeeElectricalParameters prepare(Source source){source.lastCaptured=source.currentSource();return new CeeElectricalParameters(1,source.currentSource(),0,false);}
        });
        if(CeeCompatibility.modern())try{
            Class<? extends ElectricalProperties> type=Class.forName("org.cha0scollective.wwpg.gametest.cee12.UnsupportedNonlinearProperties").asSubclass(ElectricalProperties.class);
            event.register("wwpg_tests:unsupported_nonlinear",type,ignored->new CeeElectricalParameters(1,0,0,false));
        }catch(IllegalArgumentException expected){
            if(!expected.getMessage().contains("requires iteration support"))throw expected;
            nonlinearRejected=true;
        }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
    }
    @GameTest(template="empty",timeoutTicks=80)
    public static void signedSourceAndCurrentPublication(GameTestHelper h){
        DynamicGameTests.substeps(h.getLevel());
        var source=new Source();String owner="cee-adapter:"+h.absolutePos(net.minecraft.core.BlockPos.ZERO);
        var a=new AttachedNode(0,owner);var b=new AttachedNode(1,owner);var g=new AttachedNode(2,owner);
        Consumer<AddToElectricGraphEvent> listener=event->{if(event.level==h.getLevel()){
            event.builder.connect(a,g,source);event.builder.connect(a,b,ElectricalProperties.resistor(9));
            event.builder.connect(b,g,ElectricalProperties.resistor(10));event.builder.defaultZeroPotential(g,100);
        }};
        NeoForge.EVENT_BUS.addListener(AddToElectricGraphEvent.class,listener);
        h.runAtTickTime(70,()->NeoForge.EVENT_BUS.unregister(listener));
        Runnable positive=()->check(h,a,b,g,5,-.5,.5);
        h.runAtTickTime(20,()->{positive.run();source.drive(-10);});
        h.runAtTickTime(28,()->{try{
            double expected=CeeCompatibility.modern()?-10:10;
            h.assertTrue(source.currentSource()==expected,"Fixture did not reverse its native current");
            h.assertTrue(source.lastCaptured==expected,"Adapter did not capture reversed native current: "+source.lastCaptured);
            check(h,a,b,g,-5,.5,-.5);DynamicGameTests.audit(h);h.succeed();}
            finally{NeoForge.EVENT_BUS.unregister(listener);}});
    }
    private static void check(GameTestHelper h,AttachedNode a,AttachedNode b,AttachedNode g,double voltage,double source,double load){
        var results=InfrastructureSavedData.load(h.getLevel()).ticker.lastResults;
        h.assertTrue(results instanceof CeeResultsAvailability available&&available.available(a,g)&&available.available(b,g),"Addon circuit unavailable");
        double[] bv=results.getVoltages(b),gv=results.getVoltages(g);
        h.assertTrue(bv.length==16&&gv.length==16,"Addon waveform sample count");
        // CEE's getVoltageAt is an RMS magnitude; signed DC comes from its samples.
        BoardComponentGameTests.near(h,bv[15]-gv[15],voltage,.0001,"Addon signed voltage");
        BoardComponentGameTests.near(h,results.getCurrentThrough(a,g),source,.0001,"Addon source current");
        BoardComponentGameTests.near(h,results.getCurrentThrough(b,g),load,.0001,"Addon load current");
    }
    @GameTest(template="empty",timeoutTicks=20)
    public static void modernNonlinearAdapterRequiresIterationSupport(GameTestHelper h){
        h.assertTrue(!CeeCompatibility.modern()||nonlinearRejected,"Nonlinear addon was accepted by the substep-only API");h.succeed();
    }
}
