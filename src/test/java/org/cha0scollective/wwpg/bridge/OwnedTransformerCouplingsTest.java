package org.cha0scollective.wwpg.bridge;

import org.junit.jupiter.api.Test;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.node.*;
import org.slf4j.Logger;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

class OwnedTransformerCouplingsTest {
    @Test void constructorContextEndsOnSuccessAndFailure() {
        var original=ElectricalNetwork.LOGGER;var contexts=new ArrayList<Boolean>();
        ElectricalNetwork.LOGGER=logger(()->contexts.add(OwnedTransformerCouplings.constructing()));
        try {
            var a=new FloatingNode();var b=new FloatingNode();var c=new FloatingNode();var d=new FloatingNode();
            var coupling=OwnedTransformerCouplings.create(2,a,b,c,d);
            assertEquals(0,coupling.getResistance());assertFalse(OwnedTransformerCouplings.constructing());
            TransformerCoupling.create(2,a,b,c,d);
            assertThrows(NullPointerException.class,()->OwnedTransformerCouplings.create(2,a,b,c,null));
            assertFalse(OwnedTransformerCouplings.constructing());
            TransformerCoupling.create(2,a,b,c,d);
            // JVM units run without mixins: observe the context at the upstream warn call.
            assertEquals(List.of(true,false,false),contexts);
        } finally {ElectricalNetwork.LOGGER=original;}
    }

    @Test void anotherThreadNeverInheritsOwnedConstruction() {
        var original=ElectricalNetwork.LOGGER;var contexts=Collections.synchronizedList(new ArrayList<Boolean>());
        ElectricalNetwork.LOGGER=logger(()->{
            boolean owned=OwnedTransformerCouplings.constructing();contexts.add(owned);
            if(owned)CompletableFuture.runAsync(()->TransformerCoupling.create(2,
                    new FloatingNode(),new FloatingNode(),new FloatingNode(),new FloatingNode())).join();
        });
        try {
            OwnedTransformerCouplings.create(2,new FloatingNode(),new FloatingNode(),new FloatingNode(),new FloatingNode());
            assertEquals(List.of(true,false),contexts);assertFalse(OwnedTransformerCouplings.constructing());
        } finally {ElectricalNetwork.LOGGER=original;}
    }

    private static Logger logger(Runnable warn){return (Logger)Proxy.newProxyInstance(Logger.class.getClassLoader(),new Class<?>[]{Logger.class},(proxy,method,args)->{
        if(method.getName().equals("warn"))warn.run();
        return method.getReturnType()==boolean.class?false:null;
    });}
}
