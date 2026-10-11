package org.cha0scollective.wwpg.extension;

import org.cha0scollective.wwpg.api.Element;
import org.cha0scollective.wwpg.api.ElementState;
import org.cha0scollective.wwpg.api.ElectricalModel;
import org.cha0scollective.wwpg.api.TickContext;
import org.junit.jupiter.api.Test;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode;
import org.patryk3211.powergrid.electricity.sim.node.VoltageSourceCoupling;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiGroundTest {
    @Test void loadedGroundIsExactlyZeroWithoutChangingSourceCurrent() {
        var network = new ElectricalNetwork(false);
        network.warmUp(-1);
        var positive = new FloatingNode();
        var negative = new FloatingNode();
        network.addNode(positive);
        network.addNode(negative);
        var source = new VoltageSourceCoupling(positive, negative, 0f);
        source.setVoltage(10);
        network.addNode(source);
        network.addWire(new ElectricWire(1, positive, negative));
        var ground = new ApiGround(negative);
        network.calculate(1);
        assertTrue(network.isConverged());
        assertEquals(0, negative.getVoltage(), 1e-12);
        assertEquals(10, positive.getVoltage(), 1e-10);
        assertEquals(-10, source.getCurrent(), 1e-10);
        assertSame(negative, ground.node());
        assertSame(network, ground.network());
    }

    @Test void multipleGroundsAndChangedGroundKeepCorrectPolarity() {
        var network = new ElectricalNetwork(false);
        network.warmUp(-1);
        var positive = new FloatingNode();
        var negative = new FloatingNode();
        var otherReturn = new FloatingNode();
        network.addNode(positive);
        network.addNode(negative);
        network.addNode(otherReturn);
        var source = new VoltageSourceCoupling(positive, negative, 0f);
        source.setVoltage(10);
        network.addNode(source);
        network.addWire(new ElectricWire(2, positive, otherReturn));
        network.addWire(new ElectricWire(1, negative, otherReturn));
        var ground = new ApiGround(negative);
        var secondGround = new ApiGround(otherReturn);
        network.calculate(1);
        assertTrue(network.isConverged());
        assertEquals(0, negative.getVoltage(), 1e-12);
        assertEquals(0, otherReturn.getVoltage(), 1e-12);
        assertEquals(-5, source.getCurrent(), 1e-10);

        secondGround.remove();
        ground.remove();
        var moved = new ApiGround(positive);
        network.calculate(1);
        assertTrue(network.isConverged());
        assertEquals(0, positive.getVoltage(), 1e-12);
        assertEquals(-10, negative.getVoltage(), 1e-10);
        assertEquals(-20.0 / 3, otherReturn.getVoltage(), 1e-10);
        assertEquals(-10.0 / 3, source.getCurrent(), 1e-10);
        assertSame(network, moved.network());
    }

    @Test void changingReferenceDuringStorageWindowDoesNotAddElectricalSteps() {
        var network = new ElectricalNetwork(false);
        network.warmUp(-1);
        var positive = new FloatingNode();
        var negative = new FloatingNode();
        var stored = new FloatingNode();
        network.addNode(positive);
        network.addNode(negative);
        network.addNode(stored);
        var nodes = Map.of("positive", positive, "negative", negative, "stored", stored);
        var descriptions = List.of(
                new Element("source", "positive", "negative", new ElectricalModel.VoltageSource(10)),
                new Element("feed", "positive", "stored", new ElectricalModel.Resistor(100)),
                new Element("storage", "stored", "negative", new ElectricalModel.Capacitor(.01, 0)));
        var context = new TickContext(0, .05, 16);
        var elements = descriptions.stream().map(description -> {
            var element = new ApiElement(description, nodes, network);
            element.update(description);
            element.before(context, 0);
            return element;
        }).toList();
        var ground = new ApiGround(negative);
        network.prepare(context.substeps());
        for (int step = 0; step < context.substeps(); ++step) {
            if (step == 8) {
                ground.remove();
                ground = new ApiGround(positive);
                network.prepare(context.substeps());
            }
            for (var element : elements) element.before(context, step);
            network.singleTick();
            elements.forEach(ApiElement::after);
        }
        var capacitor = elements.get(2);
        double expected = 10 * (1 - Math.pow(1 / (1 + context.substepSeconds()), 16));
        assertEquals(expected, ((ElementState.Capacitor) capacitor.state()).voltageVolts(), 1e-8);
        assertTrue(elements.stream().allMatch(element -> element.valid(context)));
        assertEquals(0, positive.getVoltage(), 1e-12);
        assertEquals(-10, negative.getVoltage(), 1e-10);
    }
}
