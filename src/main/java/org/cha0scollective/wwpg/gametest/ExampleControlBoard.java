package org.cha0scollective.wwpg.gametest;

import org.patryk3211.powergrid.circuits.components.CapacitorComponent;
import org.patryk3211.powergrid.circuits.components.Components;
import org.patryk3211.powergrid.circuits.schematic.CircuitSchematic;
import org.patryk3211.powergrid.circuits.schematic.PlacedComponent;
import org.patryk3211.powergrid.circuits.schematic.Point;

/** Native PG relay contacts actually carry the example lamps' power. */
final class ExampleControlBoard {
    static final int COIL_POSITIVE = 0, COIL_RETURN = 1, OFF_LAMP = 2, LAMP_FEED = 3, RUN_LAMP = 4;

    static CircuitSchematic schematic() {
        var schematic = new CircuitSchematic();
        schematic.setName("WWPG lamp relay with capacitor off-delay");
        Point[] connectors = {new Point(0, 0), new Point(0, 13), new Point(13, 0), new Point(13, 5), new Point(13, 13)};
        for (var at : connectors)
            schematic.placeComponent(new PlacedComponent(Components.CONNECTOR.get(), at.x(), at.y(), null), at.x(), at.y());
        schematic.placeComponent(new PlacedComponent(Components.RELAY.get(), 6, 5, null), 6, 5);
        var capacitor = new PlacedComponent(Components.CAPACITOR.get(), 2, 6, null);
        capacitor.set(CapacitorComponent.CAPACITANCE, .04f);
        schematic.placeComponent(capacitor, 2, 6);

        var router = new BoardFixture.Router(schematic);
        Point[] relayPads = {new Point(6, 5), new Point(6, 7), new Point(8, 5), new Point(9, 6), new Point(8, 7)};
        for (int port = 0; port < connectors.length; port++) {
            var at = connectors[port];
            router.route(relayPads[port], new Point(at.x() + 1, at.y() + 1));
        }
        // Branch onto the existing coil nets, without joining either lamp contact.
        router.route(new Point(2, 7), relayPads[0]);
        router.route(new Point(4, 7), relayPads[1]);
        return schematic;
    }
}
