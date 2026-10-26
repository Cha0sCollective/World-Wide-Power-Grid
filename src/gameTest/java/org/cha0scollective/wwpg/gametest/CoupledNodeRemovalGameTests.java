package org.cha0scollective.wwpg.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.config.CSolver;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.node.CurrentSourceWire;
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode;
import org.patryk3211.powergrid.electricity.sim.special.PentodeWire;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import org.patryk3211.powergrid.circuits.components.Components;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.WorldNetworks;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Reproduces the native three-node stamp reached when an assembly stops ticking. */
@GameTestHolder("wwpg_coupled")
@PrefixGameTestTemplate(false)
public final class CoupledNodeRemovalGameTests {
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void poweredPentodeRemovalWithReducedControlNodeKeepsRemainingCircuitValid(GameTestHelper h) {
        var network = new ElectricalNetwork(false);
        network.switchBackend(CSolver.SolverBackend.valueOf(System.getProperty("wwpg.test.backend", "NATIVE")));
        network.warmUp(-1);
        try {
            var cathode = new FloatingNode(); var anode = new FloatingNode();
            var grid = new FloatingNode(); var screen = new FloatingNode();
            network.addNodes(cathode, anode, grid, screen);
            network.addWire(new ElectricWire(.005, cathode, null));
            supply(network, anode, cathode, 100);
            supply(network, grid, cathode, 0);
            supply(network, screen, cathode, 100);
            var tube = new PentodeWire(8, 6000, 4500, 48, 12, 1.35f, .1f,
                    cathode, anode, grid, screen);
            network.addWire(tube);
            for (int i = 0; i < 80; i++) { network.prepare(16); for (int j = 0; j < 16; j++) network.singleTick(); }
            h.assertTrue(tube.current() > .001, "Removal control must begin with a conducting native pentode");
            network.makeLeaf(grid, cathode);
            network.prepare(16);
            h.assertTrue(network.isLeaf(grid) && grid.getIndex() == -1 && !network.isDirty()
                            && cathode.getIndex() >= 0 && anode.getIndex() >= 0,
                    "The control terminal must be reduced while both plate stamp terminals remain in the matrix");
            // PG's full rebuild skips every stamp touching this leaf. Incremental subtraction must do the same.
            tube.remove();
            h.assertTrue(tube.getNetwork() == null, "Native compound removal retained the parent network");
            for (int i = 0; i < 4; i++) { network.prepare(16); for (int j = 0; j < 16; j++) network.singleTick(); }
            BoardComponentGameTests.near(h, anode.getVoltage() - cathode.getVoltage(), 100, .1,
                    "Remaining supply after reduced-grid pentode removal");
            network.removeLeaf(grid);
            var replacement = new PentodeWire(8, 6000, 4500, 48, 12, 1.35f, .1f,
                    cathode, anode, grid, screen);
            network.addWire(replacement);
            for (int i = 0; i < 80; i++) { network.prepare(16); for (int j = 0; j < 16; j++) network.singleTick(); }
            h.assertTrue(replacement.current() > .001 && !network.isLeaf(grid),
                    "Restoring a native control terminal failed to restore tube conduction");
            h.assertTrue(((org.cha0scollective.wwpg.mixin.ElectricalNetworkAccessor) network).wwpg$solver().type()
                            == CSolver.SolverBackend.valueOf(System.getProperty("wwpg.test.backend", "NATIVE")),
                    "Native tube regression ran a different backend");
            org.cha0scollective.wwpg.WorldWidePowerGrid.LOGGER.info("COUPLED_NODE_REMOVAL_PASSED: backend={}, reducedControl=true, replacementCurrent={}",
                    System.getProperty("wwpg.test.backend"), replacement.current());
        } finally { network.cleanup(); }
        h.assertTrue(org.cha0scollective.wwpg.bridge.SolverAudit.ceeAttempts() == 0, "CEE attempted a second solve");
        h.succeed();
    }
    private static void supply(ElectricalNetwork network, FloatingNode positive, FloatingNode negative, double volts) {
        var source = new CurrentSourceWire(positive, negative, 1000);
        source.setCurrent(volts * 1000);
        network.addWire(source);
    }

    @GameTest(template = "empty", timeoutTicks = 3000)
    public static void mixedPentodeAssemblySurvivesFiveActualChunkCyclesAndColdRestart(GameTestHelper h) throws java.io.IOException {
        var level = h.getLevel();
        var origin = new BlockPos(120000, 62, 60000);
        var center = new ChunkPos(origin);
        var snapshot = Path.of("pentode-before-restart.nbt");
        boolean restore = System.getProperty("wwpg.test.restartPhase", "SETUP").equals("VERIFY");
        force(h, center, true);
        BoardFixture[] fixture = {new BoardFixture(h, Components.PENTODE.get(), p -> {}, origin, restore)};
        var schematic = restore ? NbtIo.readCompressed(snapshot, NbtAccounter.unlimitedHeap()) : null;
        if (restore) h.assertTrue(schematic.equals(fixture[0].board.getSchematic().serializeNbt(level.registryAccess())),
                "Cold restart changed native pentode identities, settings or traces before first solve");
        var unloads = new AtomicInteger();
        Consumer<ChunkEvent.Unload> listener = e -> {
            if (e.getLevel() == level && e.getChunk().getPos().equals(center)) unloads.incrementAndGet();
        };
        NeoForge.EVENT_BUS.addListener(ChunkEvent.Unload.class, listener);
        Set<WorldNetworks.PartId> ids = new HashSet<>();
        var sequence = h.startSequence().thenWaitUntil(() -> h.assertTrue(level.areEntitiesLoaded(center.toLong()),
                "Waiting for pentode wire entities")).thenExecute(() -> {
            if (restore) return;
            var f = fixture[0]; f.connect();
            ShowroomTools.scroll(h, f.supply(2), 100000); f.resistance(2, 10000);
            ShowroomTools.scroll(h, f.supply(3), 6000); f.resistance(3, .01); f.resistance(4, .01);
            ShowroomTools.scroll(h, f.supply(5), 100000); f.resistance(5, 1000);
        }).thenIdle(70).thenWaitUntil(() -> h.assertTrue(fixture[0].current() > .0001,
                "Mixed pentode must conduct before unloading")).thenExecute(() -> {
            ids.addAll(wireIds(h, fixture[0]));
            h.assertTrue(ids.size() == 12, "Expected two native PG wires for each of six pentode pads");
        });
        for (int cycle = 1; cycle <= 5; cycle++) {
            int pass = cycle;
            net.minecraft.nbt.CompoundTag[] before = {null};
            sequence.thenExecute(() -> {
                before[0] = fixture[0].board.getSchematic().serializeNbt(level.registryAccess());
                level.getDataStorage().save(); level.getChunkSource().save(true); force(h, center, false);
            }).thenIdle(330).thenWaitUntil(() -> {
                level.getChunkSource().tick(() -> true, true);
                h.assertTrue(level.getChunkSource().getChunkNow(center.x, center.z) == null && unloads.get() >= pass,
                        "Waiting for actual nonlinear assembly unload " + pass);
            }).thenExecute(() -> {
                DelayedEntityLoads.hold(level, center, 60); force(h, center, true);
                fixture[0] = new BoardFixture(h, Components.PENTODE.get(), p -> {}, origin, true);
                h.assertTrue(before[0].equals(fixture[0].board.getSchematic().serializeNbt(level.registryAccess())),
                        "Actual nonlinear unload changed native identities or design before resumed solving");
            }).thenIdle(20).thenExecute(() -> h.assertTrue(!level.areEntitiesLoaded(center.toLong()),
                    "Nonlinear cycle did not exercise delayed wire loading"))
                    .thenWaitUntil(() -> h.assertTrue(level.areEntitiesLoaded(center.toLong()), "Waiting for restored pentode wires"))
                    .thenIdle(70).thenWaitUntil(() -> h.assertTrue(fixture[0].current() > .0001,
                            "Actual nonlinear unload failed to restore mixed tube conduction"))
                    .thenExecute(() -> {
                        h.assertTrue(ids.equals(wireIds(h, fixture[0])), "Nonlinear assembly reload changed or duplicated PG wire identities");
                        org.cha0scollective.wwpg.WorldWidePowerGrid.LOGGER.info("PENTODE_ACTUAL_CYCLE: backend={}, cycle={}, unloads={}, current={}",
                                System.getProperty("wwpg.test.backend"), pass, unloads.get(), fixture[0].current());
                    });
        }
        sequence.thenExecute(() -> {
            try { NbtIo.writeCompressed(fixture[0].board.getSchematic().serializeNbt(level.registryAccess()), snapshot); }
            catch (java.io.IOException error) { throw new IllegalStateException(error); }
            NeoForge.EVENT_BUS.unregister(listener); force(h, center, false); DynamicGameTests.audit(h);
        }).thenSucceed();
    }
    private static void force(GameTestHelper h, ChunkPos center, boolean value) {
        for (int x = center.x - 1; x <= center.x + 1; x++) for (int z = center.z - 1; z <= center.z + 1; z++) {
            h.getLevel().setChunkForced(x, z, value);
            if (value) h.getLevel().getChunk(x, z);
        }
    }
    private static Set<WorldNetworks.PartId> wireIds(GameTestHelper h, BoardFixture f) {
        var ids = new HashSet<WorldNetworks.PartId>();
        var world = GlobalElectricNetworks.getWorldNetworks(h.getLevel());
        for (int pad : f.pads()) {
            var parts = world.findConnectedWires(new BlockWireEndpoint(f.boardPosition, f.port(pad)));
            if (parts != null) for (var p : parts) ids.add(p.persistentOwnerId);
            var feed = world.findConnectedWires(new BlockWireEndpoint(f.supply(pad), 1));
            if (feed != null) for (var p : feed) ids.add(p.persistentOwnerId);
        }
        return ids;
    }
}
