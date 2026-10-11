package org.cha0scollective.wwpg.extension;

import org.cha0scollective.wwpg.api.*;
import org.junit.jupiter.api.Test;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ApiElectricalTest {
    private static final TickContext TICK=new TickContext(0,.05,16);
    private static final class Circuit {
        final ElectricalNetwork network=new ElectricalNetwork(false);
        final Map<String,FloatingNode> nodes=new LinkedHashMap<>();
        final List<ApiElement> elements=new ArrayList<>();
        Circuit(String... keys) {
            network.warmUp(-1);
            for(var key:keys){var node=new FloatingNode();nodes.put(key,node);network.addNode(node);}
            network.addWire(new ElectricWire(.001,nodes.get("negative"),null));
        }
        ApiElement add(String id,String p,String n,ElectricalModel model) {
            var description=new Element(id,p,n,model);var element=new ApiElement(description,nodes,network);
            element.update(description);element.before(TICK,0);elements.add(element);return element;
        }
        void tick(TickContext context) {
            for(var element:elements)element.before(context,0);
            network.prepare(context.substeps());
            for(int step=0;step<context.substeps();step++){
                for(var element:elements)element.before(context,step);
                network.singleTick();elements.forEach(ApiElement::after);
            }
        }
    }
    @Test void parallelElementsPreserveIndividualCurrentsAndSourcePowerDirection() {
        var c=new Circuit("positive","negative");
        var source=c.add("source","positive","negative",new ElectricalModel.VoltageSource(10));
        var a=c.add("a","positive","negative",new ElectricalModel.Resistor(10));
        var b=c.add("b","positive","negative",new ElectricalModel.Resistor(20));c.tick(TICK);
        assertEquals(1,a.result(TICK).meanCurrentAmps(),1e-6);
        assertEquals(.5,b.result(TICK).meanCurrentAmps(),1e-6);
        assertEquals(-1.5,source.result(TICK).meanCurrentAmps(),1e-6);
        assertEquals(-15,source.result(TICK).meanPowerWatts(),1e-5);
        assertEquals(-.75,source.result(TICK).energyJoules(),1e-6);
    }
    @Test void capacitorHistoryAdvancesOncePerAcceptedSubstepAndKeepsParameterIdentity() {
        var c=new Circuit("positive","negative","stored");
        c.add("source","positive","negative",new ElectricalModel.VoltageSource(10));
        c.add("r","positive","stored",new ElectricalModel.Resistor(100));
        var storage=c.add("c","stored","negative",new ElectricalModel.Capacitor(.01,0));c.tick(TICK);
        double expected=10*(1-Math.pow(1/(1+TICK.substepSeconds()),16));
        assertEquals(expected,((ElementState.Capacitor)storage.state()).voltageVolts(),1e-7);
        storage.update(new Element("c","stored","negative",new ElectricalModel.Capacitor(.01,100)));
        c.tick(new TickContext(1,.05,16));
        expected=10*(1-Math.pow(1/(1+TICK.substepSeconds()),32));
        assertEquals(expected,((ElementState.Capacitor)storage.state()).voltageVolts(),1e-7);
    }
    @Test void inductorTransientUsesAuthoritativeTimeAndSignedHistory() {
        var c=new Circuit("positive","negative","coil");
        c.add("source","positive","negative",new ElectricalModel.VoltageSource(10));
        c.add("r","positive","coil",new ElectricalModel.Resistor(10));
        var storage=c.add("l","coil","negative",new ElectricalModel.Inductor(1,0));c.tick(TICK);
        assertEquals(1-Math.pow(1/(1+10*TICK.substepSeconds()),16),((ElementState.Inductor)storage.state()).currentAmps(),1e-7);
    }
    @Test void transformerUsesWindingPolarityAndPassivePrimaryCurrent() {
        var c=new Circuit("positive","negative","spositive","snegative");
        c.add("source","positive","negative",new ElectricalModel.VoltageSource(10));
        var transformer=c.add("t","positive","negative",new ElectricalModel.Transformer("spositive","snegative",2));
        var load=c.add("r","spositive","snegative",new ElectricalModel.Resistor(10));c.tick(TICK);
        assertEquals(20,load.result(TICK).meanVoltageVolts(),1e-5);
        assertEquals(4,transformer.result(TICK).meanCurrentAmps(),1e-5);
        assertEquals(40,transformer.result(TICK).meanPowerWatts(),1e-4);
    }
    @Test void controlledSourcesAreSimultaneousMatrixConstraints() {
        for(boolean current:List.of(false,true)) {
            var c=new Circuit("positive","negative","output");
            c.add("source","positive","negative",new ElectricalModel.VoltageSource(10));
            c.add("control","output","negative",current?new ElectricalModel.ControlledCurrentSource("positive","negative",.1)
                    :new ElectricalModel.ControlledVoltageSource("positive","negative",2));
            var load=c.add("r","output","negative",new ElectricalModel.Resistor(10));c.tick(TICK);
            assertEquals(current?-10:20,load.result(TICK).meanVoltageVolts(),1e-5);
        }
    }
    @Test void nonlinearDiodeRunsInsidePgSolveAndAcRmsUsesAllSamples() {
        var c=new Circuit("positive","negative","anode");
        c.add("source","positive","negative",new ElectricalModel.VoltageSource(10));
        c.add("r","positive","anode",new ElectricalModel.Resistor(100));
        var diode=c.add("d","anode","negative",new ElectricalModel.Diode(1e-9,1,22,1));c.tick(TICK);
        assertTrue(diode.result(TICK).meanVoltageVolts()>.4&&diode.result(TICK).meanVoltageVolts()<1);
        assertTrue(diode.result(TICK).meanCurrentAmps()>.09);
        var ac=new Circuit("positive","negative");
        var source=ac.add("source","positive","negative",new ElectricalModel.AcVoltageSource(10,20,0,0));
        ac.add("r","positive","negative",new ElectricalModel.Resistor(10));ac.tick(TICK);
        assertEquals(10/Math.sqrt(2),source.result(TICK).rmsVoltageVolts(),1e-7);
    }
}
