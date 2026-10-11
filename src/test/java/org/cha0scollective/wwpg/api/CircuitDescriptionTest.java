package org.cha0scollective.wwpg.api;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CircuitDescriptionTest {
    private static Terminal terminal(String key, int id) {
        return new Terminal(key, id, new LocalPoint(.5, .5, .5), .125);
    }

    @Test void parallelElementsAndNativeTerminalOrderHaveIndependentIdentities() {
        var positive = terminal("positive", 9);
        var negative = terminal("negative", 2);
        var first = new Element("lamp", "positive", "negative", new ElectricalModel.Resistor(100));
        var second = new Element("meter", "positive", "negative", new ElectricalModel.Resistor(1e6));
        var reverse = new Element("reverse_probe", "negative", "positive", new ElectricalModel.Resistor(100));
        var description = new CircuitDescription(List.of(negative, positive), List.of(),
                List.of(first, second, reverse), Set.of("negative"));
        assertEquals(2, description.terminals().getFirst().nativeId());
        assertEquals(9, description.terminals().getLast().nativeId());
        assertEquals("lamp", description.elements().getFirst().id());
        assertEquals("negative", description.elements().getLast().positiveNode());
        assertEquals(3, description.elements().size());
    }

    @Test void preparedDescriptionDefensivelyCapturesAllCollections() {
        var terminals = new ArrayList<>(List.of(terminal("p", 17), terminal("n", 3)));
        var internal = new ArrayList<>(List.of(new InternalNode("sense")));
        var elements = new ArrayList<>(List.of(new Element("r", "p", "sense", new ElectricalModel.Resistor(20))));
        var grounds = new HashSet<>(Set.of("n"));
        var snapshot = new CircuitDescription(terminals, internal, elements, grounds);
        terminals.clear(); internal.clear(); elements.clear(); grounds.clear();
        assertEquals(2, snapshot.terminals().size());
        assertEquals("sense", snapshot.internalNodes().getFirst().key());
        assertEquals("r", snapshot.elements().getFirst().id());
        assertEquals(Set.of("n"), snapshot.grounds());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.terminals().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.grounds().clear());
    }

    @Test void rejectsDuplicatesAndAllUndeclaredReferencesWithOwnerDiagnostics() {
        var terminals = List.of(terminal("p", 0), terminal("n", 1));
        var duplicate = new Element("load", "p", "n", new ElectricalModel.Resistor(10));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> new CircuitDescription(terminals,
                List.of(), List.of(duplicate, duplicate), Set.of())).getMessage().contains("load"));
        assertThrows(IllegalArgumentException.class, () -> new CircuitDescription(
                List.of(terminal("p", 0), terminal("n", 0)), List.of(), List.of(), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new CircuitDescription(terminals,
                List.of(new InternalNode("p")), List.of(), Set.of()));
        var error = assertThrows(IllegalArgumentException.class, () -> new CircuitDescription(terminals, List.of(),
                List.of(new Element("faulty_load", "p", "missing", new ElectricalModel.Resistor(10))), Set.of()));
        assertTrue(error.getMessage().contains("faulty_load"));
        assertTrue(error.getMessage().contains("missing"));
        assertThrows(IllegalArgumentException.class, () -> new CircuitDescription(terminals, List.of(), List.of(), Set.of("missing")));
    }

    @Test void validatesTransformerAndControlledSourceInternalNodes() {
        var terminals = List.of(terminal("p", 0), terminal("n", 1));
        var internal = List.of(new InternalNode("sp"), new InternalNode("sn"));
        var transformer = new Element("transformer", "p", "n", new ElectricalModel.Transformer("sp", "sn", -.5));
        var controlled = new Element("amplifier", "sp", "sn", new ElectricalModel.ControlledVoltageSource("p", "n", 2));
        var description = new CircuitDescription(terminals, internal, List.of(transformer, controlled), Set.of("n"));
        assertEquals(2, description.elements().size());
        assertThrows(IllegalArgumentException.class, () -> new CircuitDescription(terminals, List.of(), List.of(transformer), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new CircuitDescription(terminals, List.of(),
                List.of(new Element("bad_control", "p", "n", new ElectricalModel.ControlledCurrentSource("missing", "n", 1))), Set.of()));
    }

    @Test void identityDoesNotDependOnGeometryOrTemporaryOrdinal() {
        var first = new EndpointIdentity(new DeviceIdentity("minecraft:overworld", 10, 64, 20, "example:panel", "attachment_2"), "common");
        var restored = new EndpointIdentity(new DeviceIdentity("minecraft:overworld", 10, 64, 20, "example:panel", "attachment_2"), "common");
        var nether = new EndpointIdentity(new DeviceIdentity("minecraft:the_nether", 10, 64, 20, "example:panel", "attachment_2"), "common");
        assertEquals(first, restored);
        assertNotEquals(first, nether);
        assertThrows(IllegalArgumentException.class, () -> new DeviceIdentity("overworld", 0, 0, 0, "example:load", "main"));
        assertThrows(IllegalArgumentException.class, () -> new DeviceIdentity("minecraft:overworld", 0, 0, 0, "unqualified", "main"));
    }

    @Test void actionableBoundsPreventUnboundedOrNonfiniteDescriptions() {
        assertThrows(IllegalArgumentException.class, () -> terminal("bad", -1));
        assertThrows(IllegalArgumentException.class, () -> terminal("bad", 4096));
        assertThrows(IllegalArgumentException.class, () -> terminal("UPPER", 0));
        assertThrows(IllegalArgumentException.class, () -> new LocalPoint(Double.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Terminal("p", 0, new LocalPoint(0, 0, 0), 0));
        assertThrows(IllegalArgumentException.class, () -> new Element("short", "p", "p", new ElectricalModel.Resistor(10)));
        assertEquals(CircuitDescription.empty(), new CircuitDescription(List.of(), List.of(), List.of(), Set.of()));
    }
}
