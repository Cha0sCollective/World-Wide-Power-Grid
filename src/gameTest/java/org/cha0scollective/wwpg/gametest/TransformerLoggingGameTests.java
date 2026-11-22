package org.cha0scollective.wwpg.gametest;

import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.*;
import org.cha0scollective.wwpg.api.*;
import org.cha0scollective.wwpg.bridge.OwnedTransformerCouplings;
import org.cha0scollective.wwpg.bridge.TransformerStamp;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.node.*;
import org.slf4j.Logger;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Actual transformed constructor: WWPG is quiet, foreign ideal models still warn. */
@GameTestHolder("wwpg_transformer_logging") @PrefixGameTestTemplate(false)
public final class TransformerLoggingGameTests {
    @BeforeBatch(batch="wwpg_transformer_logging") public static void setup(ServerLevel level){DynamicGameTests.substeps(level);}

    @GameTest(template="empty",batch="wwpg_transformer_logging",timeoutTicks=25)
    public static void ceeAndApiCouplingsStayIdealAndOnlyForeignConstructionWarns(GameTestHelper h){
        var original=ElectricalNetwork.LOGGER;var warnings=new AtomicInteger();ElectricalNetwork.LOGGER=recorder(original,warnings);
        try {
            var n=new ElectricalNetwork(false);var a=new FloatingNode();var b=new FloatingNode();var c=new FloatingNode();var d=new FloatingNode();
            for(var node:List.of(a,b,c,d))n.addNode(node);
            var cee=new TransformerStamp(n,a,b,c,d,2);
            var owned=(TransformerCoupling)field(cee,"coupling");
            h.assertTrue(owned.getResistance()==0,"CEE coupling resistance changed");
            var ctor=Class.forName("org.cha0scollective.wwpg.extension.ApiElement").getDeclaredConstructor(Element.class,Map.class,ElectricalNetwork.class);ctor.setAccessible(true);
            var element=ctor.newInstance(new Element("transformer","a","b",new ElectricalModel.Transformer("c","d",2)),Map.of("a",a,"b",b,"c",c,"d",d),n);
            h.assertTrue(((TransformerCoupling)field(element,"transformer")).getResistance()==0,"API coupling resistance changed");
            h.assertTrue(warnings.get()==0,"WWPG ideal construction still warns");
            TransformerCoupling.create(2,a,b,c,d);
            h.assertTrue(warnings.get()==1,"Foreign ideal coupling lost its warning");
            OwnedTransformerCouplings.create(2,a,b,c,d);
            h.assertTrue(warnings.get()==1,"Owned context leaked or quiet path failed");
            TransformerCoupling.create(2,.1f,a,b,c,d);
            h.assertTrue(warnings.get()==1,"Nonzero-resistance coupling unexpectedly warned");
            org.cha0scollective.wwpg.WorldWidePowerGrid.LOGGER.info("WWPG_TRANSFORMER_LOGGING ceeResistance=0 apiResistance=0 ownedWarnings=0 foreignWarnings=1");
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}finally{ElectricalNetwork.LOGGER=original;}
        h.succeed();
    }

    @GameTest(template="empty",batch="wwpg_transformer_logging",timeoutTicks=25)
    public static void failedConstructionCannotSilenceNextForeignWarning(GameTestHelper h){
        var original=ElectricalNetwork.LOGGER;var warnings=new AtomicInteger();ElectricalNetwork.LOGGER=recorder(original,warnings);
        try {
            var a=new FloatingNode();var b=new FloatingNode();var c=new FloatingNode();var d=new FloatingNode();
            boolean failed=false;try{OwnedTransformerCouplings.create(2,a,b,c,null);}catch(NullPointerException expected){failed=true;}
            h.assertTrue(failed&&!OwnedTransformerCouplings.constructing(),"Failed constructor leaked its warning scope");
            TransformerCoupling.create(2,a,b,c,d);h.assertTrue(warnings.get()==1,"Failure silenced a later foreign warning");
        }finally{ElectricalNetwork.LOGGER=original;}
        h.succeed();
    }

    private static Object field(Object object,String name)throws ReflectiveOperationException{var field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);}
    private static Logger recorder(Logger original,AtomicInteger warnings){return (Logger)Proxy.newProxyInstance(Logger.class.getClassLoader(),new Class<?>[]{Logger.class},(proxy,method,args)->{
        if(method.getName().equals("warn")&&args!=null&&args.length==1&&"Zero-resistance transformer couplings can cause unstable behaviour.".equals(args[0]))warnings.incrementAndGet();
        return method.invoke(original,args);
    });}
}
