package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import org.patryk3211.powergrid.circuits.circuitboard.CircuitBoardBlockEntity;
import org.patryk3211.powergrid.circuits.components.Component;
import org.patryk3211.powergrid.circuits.components.Components;
import org.patryk3211.powergrid.circuits.schematic.CircuitSchematic;
import org.patryk3211.powergrid.circuits.schematic.PlacedComponent;
import org.patryk3211.powergrid.circuits.schematic.Point;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

import java.util.*;
import java.util.function.Consumer;

/** Real board, native traces/components, and a CEE supply with ballast for each pad. */
final class BoardFixture {
    private static final BlockPos BOARD = new BlockPos(3, 2, 3);
    private static final BlockPos[] SUPPLIES = {new BlockPos(1, 2, 1), new BlockPos(3, 2, 1), new BlockPos(5, 2, 1),
            new BlockPos(7, 2, 3), new BlockPos(5, 2, 6), new BlockPos(3, 2, 6), new BlockPos(1, 2, 6), new BlockPos(1, 2, 3)};
    private static final Point[] CONNECTORS = {new Point(0, 0), new Point(5, 0), new Point(10, 0), new Point(13, 4),
            new Point(13, 9), new Point(10, 13), new Point(5, 13), new Point(0, 10)};
    final GameTestHelper h;
    final CircuitBoardBlockEntity board;
    final PlacedComponent component;
    private final Map<Integer, Integer> ports = new TreeMap<>();
    private final Map<Integer, BlockPos> supplies = new TreeMap<>();
    private final Map<Integer, BlockPos> ballasts = new TreeMap<>();
    private final Map<Integer, Double> ballastResistance = new TreeMap<>();

    BoardFixture(GameTestHelper h, Component type, Consumer<PlacedComponent> configure) {
        this.h = h;
        h.setBlock(BOARD.below(), Blocks.STONE); h.setBlock(BOARD, ModdedBlocks.CIRCUIT_BOARD.get());
        board = (CircuitBoardBlockEntity) h.getBlockEntity(BOARD);
        var desired = new PlacedComponent(type, 6, 6, null); configure.accept(desired);
        var schematic = new CircuitSchematic(); schematic.setName("WWPG " + type.getClass().getSimpleName());
        var pads = new TreeMap<Integer, Point>();
        desired.footprint().getPads().forEach((point, pad) -> {
            if (pad.nodeIndex() >= 0) pads.put(pad.nodeIndex(), new Point(point.x() + 6, point.y() + 6));
        });
        int count = 0;
        var routes = new ArrayList<Point[]>();
        for (var entry : pads.entrySet()) {
            if (type.isExternalNode(entry.getKey())) continue;
            var at = CONNECTORS[count];
            schematic.placeComponent(new PlacedComponent(Components.CONNECTOR.get(), at.x(), at.y(), null), at.x(), at.y());
            ports.put(entry.getKey(), count++);
            routes.add(new Point[] {entry.getValue(), new Point(at.x() + 1, at.y() + 1)});
        }
        schematic.placeComponent(desired, 6, 6);
        int maximum = pads.isEmpty() ? -1 : pads.lastKey();
        for (int i = 0; i <= maximum; ++i) if (type.isExternalNode(i)) ports.put(i, count++);
        routes.sort(Comparator.comparingInt(p -> Math.abs(p[0].x() - p[1].x()) + Math.abs(p[0].y() - p[1].y())));
        var unrouted = new CircuitSchematic(schematic);
        boolean routed = false;
        var random = new Random(0);
        for (int attempt = 0; attempt < 100 && !routed; ++attempt) {
            schematic = new CircuitSchematic(unrouted);
            var router = new Router(schematic);
            try {
                for (var route : routes) router.route(route[0], route[1]);
                routed = true;
            } catch (IllegalStateException blocked) {
                Collections.shuffle(routes, random);
            }
        }
        h.assertTrue(routed, "Cannot route independent acceptance board pad nets");
        board.setSchematic(schematic);
        component = board.getComponentsStream().filter(p -> p.component == type).findFirst().orElseThrow();
        h.assertTrue(board.terminalCount() == ports.size(), "Board external terminal count differs from its fixture wiring");
        for (var bundle : board.getSchematic().findNodeBundles())
            h.assertTrue(bundle.size() == 2, "Board fixture accidentally joined independent pad nets: " + bundle);
        h.assertTrue(board.getSchematic().findNodeBundles().size() == routes.size(), "Board fixture has a disconnected pad route");
        int supplyIndex = 0;
        for (int pad : ports.keySet()) {
            var relative = SUPPLIES[supplyIndex++];
            h.setBlock(relative.below(), Blocks.STONE);
            h.setBlock(relative, CEEBlocks.CREATIVE_BATTERY.get());
            h.setBlock(relative.above(), ModdedBlocks.CREATIVE_RESISTOR.get());
            supplies.put(pad, h.absolutePos(relative)); ballasts.put(pad, h.absolutePos(relative.above()));
        }
    }

    void connect() {
        BlockPos previous = null;
        for (int pad : ports.keySet()) {
            var supply = supplies.get(pad); var ballast = ballasts.get(pad);
            voltage(pad, 0); resistance(pad, 0.1);
            WiringGameTests.connect(h, supply, 1, ballast, 0, true);
            WiringGameTests.connect(h, ballast, 1, h.absolutePos(BOARD), ports.get(pad), true);
            if (previous != null) WiringGameTests.connect(h, previous, 0, supply, 0, false);
            previous = supply;
        }
    }
    void voltage(int pad, double value) { DevicesSavedData.load(h.getLevel()).getDevice(supplies.get(pad), CreativeBatteryDevice.class).voltage = value; }
    void resistance(int pad, double value) {
        ballastResistance.put(pad, value);
        ((ResistorBlockEntity) h.getLevel().getBlockEntity(ballasts.get(pad))).setValue(value);
    }
    double feedCurrent(int pad) {
        var pos = ballasts.get(pad);
        return (new BlockWireEndpoint(pos, 0).getNode(h.getLevel()).getVoltage()
                - new BlockWireEndpoint(pos, 1).getNode(h.getLevel()).getVoltage()) / ballastResistance.get(pad);
    }
    double padVoltage(int pad) {
        var reference = new BlockWireEndpoint(supplies.values().iterator().next(), 0).getNode(h.getLevel());
        return new BlockWireEndpoint(h.absolutePos(BOARD), ports.get(pad)).getNode(h.getLevel()).getVoltage() - reference.getVoltage();
    }
    double current() { return Math.abs(component.wires.getFirst().current()); }
    void finish() {
        h.assertTrue(!component.destroyed, "Fixture unexpectedly destroyed its component");
        var saved = board.getSchematic().serializeNbt(h.getLevel().registryAccess());
        board.setSchematic(CircuitSchematic.fromNbt(h.getLevel().registryAccess(), saved));
        h.runAfterDelay(5, () -> {
            h.assertTrue(board.terminalCount() == ports.size(), "Reloading board configuration changed external terminal identities");
            for (int pad : ports.keySet()) h.assertTrue(Double.isFinite(padVoltage(pad)), "Reloading board left a stale electrical endpoint");
            DynamicGameTests.audit(h); h.succeed();
        });
    }

    static final class Router {
        private final CircuitSchematic schematic;
        private final Set<Point> pads = new HashSet<>();
        private final Set<Point> bodies = new HashSet<>();
        private final boolean[][][] occupied = new boolean[2][16][16];
        Router(CircuitSchematic schematic) {
            this.schematic = schematic;
            for (var placed : schematic.components()) {
                placed.footprint().getPads().keySet().forEach(p -> pads.add(new Point(p.x() + placed.x, p.y() + placed.y)));
                for (int x = placed.x; x < placed.x + placed.footprint().getWidth(); ++x)
                    for (int y = placed.y; y < placed.y + placed.footprint().getHeight(); ++y) bodies.add(new Point(x, y));
            }
        }
        void route(Point from, Point to) {
            var queue = new ArrayDeque<Cell>(); var previous = new HashMap<Cell, Cell>();
            for (int layer = 0; layer < 2; ++layer) { var start = new Cell(layer, from.x(), from.y()); queue.add(start); previous.put(start, null); }
            Cell end = null;
            while (!queue.isEmpty()) {
                var at = queue.removeFirst();
                if (at.x == to.x() && at.y == to.y()) { end = at; break; }
                var neighbors = new ArrayList<Cell>();
                for (int[] direction : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}})
                    neighbors.add(new Cell(at.layer, at.x + direction[0], at.y + direction[1]));
                if (!bodies.contains(new Point(at.x, at.y))) neighbors.add(new Cell(1 - at.layer, at.x, at.y));
                for (var next : neighbors) {
                    if (next.x < 0 || next.x > 15 || next.y < 0 || next.y > 15 || previous.containsKey(next)) continue;
                    var p = new Point(next.x, next.y);
                    if (occupied[next.layer][next.x][next.y] || pads.contains(p) && !p.equals(from) && !p.equals(to)) continue;
                    previous.put(next, at); queue.addLast(next);
                }
            }
            if (end == null) throw new IllegalStateException("Cannot route acceptance board " + from + " -> " + to);
            var path = new ArrayList<Cell>(); for (var at = end; at != null; at = previous.get(at)) path.add(at);
            Collections.reverse(path);
            for (int i = 0; i < path.size(); ++i) {
                var at = path.get(i); occupied[at.layer][at.x][at.y] = true;
                if (i == 0) continue;
                var before = path.get(i - 1);
                if (before.layer != at.layer) {
                    schematic.placeComponent(new PlacedComponent(Components.VIA.get(), at.x, at.y, null), at.x, at.y);
                    pads.add(new Point(at.x, at.y));
                } else {
                    var layer = at.layer == 0 ? schematic.front() : schematic.back();
                    if (before.x == at.x) layer.addVerticalLine(at.x, Math.min(at.y, before.y), Math.max(at.y, before.y));
                    else layer.addHorizontalLine(at.y, Math.min(at.x, before.x), Math.max(at.x, before.x));
                }
            }
        }
        private record Cell(int layer, int x, int y) {}
    }
}
