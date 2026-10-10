package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.CEEWireInteractionBehaviours;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.foundation.nodes.NodeConnectionPoint;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.simibubi.create.api.contraption.train.PortalTrackProvider;
import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.WorldWidePowerGrid;
import org.cha0scollective.wwpg.mixin.NetherTransformerAccessor;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.WorldNetworks;
import org.patryk3211.powergrid.electricity.transformer.NetherTransformerBlockEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@GameTestHolder("wwpg_distribution")
@PrefixGameTestTemplate(false)
public final class NetherLifecycleGameTests {
    // Reserve both ends away from DimensionGameTests' (512,64,512) source:
    // those parallel fixtures otherwise replace the prepared Nether portal.
    private static final BlockPos PORTAL = new BlockPos(32768, 64, 32768), OTHER_PORTAL = new BlockPos(4096, 64, 4096);
    private static final BlockPos PRIMARY = PORTAL.west().above(), SUPPLY = PRIMARY.west(3);
    private static final Path SNAPSHOT = Path.of("nether-before-restart.nbt");

    @GameTest(template = "empty", timeoutTicks = 3000)
    public static void linkedNetherEndsSurviveColdRestartAndFiveIndependentActualChunkCycles(GameTestHelper h) throws IOException {
        var over = h.getLevel();
        var nether = over.getServer().getLevel(Level.NETHER);
        boolean verify = System.getProperty("wwpg.test.restartPhase", "SETUP").equals("VERIFY");
        var expected = new CompoundTag[]{verify ? NbtIo.readCompressed(SNAPSHOT, NbtAccounter.unlimitedHeap()) : null};
        var secondary = new BlockPos[]{verify ? new BlockPos(expected[0].getInt("X"), expected[0].getInt("Y"), expected[0].getInt("Z")) : null};
        // A circuit may continue ticking between a requested save and actual
        // unload. Compare restoration with the bytes actually stored on disk.
        var coldPrimary = verify ? disk(over, PRIMARY) : null;
        var coldSecondary = verify ? disk(nether, secondary[0]) : null;
        StationaryNetherGameTests.force(over, PORTAL, true);
        StationaryNetherGameTests.force(nether, OTHER_PORTAL, true);
        var primaryIds = new HashSet<WorldNetworks.PartId>();
        var secondaryIds = new HashSet<WorldNetworks.PartId>();
        var primaryUnloads = new AtomicInteger();
        var secondaryUnloads = new AtomicInteger();
        Consumer<ChunkEvent.Unload> listener = event -> {
            if (event.getLevel() == over && event.getChunk().getPos().equals(new ChunkPos(PRIMARY))) primaryUnloads.incrementAndGet();
            if (secondary[0] != null && event.getLevel() == nether
                    && event.getChunk().getPos().equals(new ChunkPos(secondary[0]))) secondaryUnloads.incrementAndGet();
        };
        NeoForge.EVENT_BUS.addListener(ChunkEvent.Unload.class, listener);
        if (!verify) {
            StationaryNetherGameTests.portal(h, over, PORTAL);
            StationaryNetherGameTests.portal(h, nether, OTHER_PORTAL);
        } else {
            // Read both native saved histories before either loaded endpoint can solve.
            saved(h, over, PRIMARY, coldPrimary, "Cold primary");
            saved(h, nether, secondary[0], coldSecondary, "Cold secondary");
            h.assertTrue(coldPrimary.getUUID("Link").equals(expected[0].getCompound("Primary").getUUID("Link"))
                    && coldSecondary.getUUID("Link").equals(expected[0].getCompound("Secondary").getUUID("Link")),
                    "Cold restart changed the persistent link identity");
            h.assertTrue(decoration(over).equals(expected[0].getCompound("WireAttachment")),
                    "Cold restart lost native wire decoration before its first solve");
        }
        var sequence = h.startSequence().thenWaitUntil(() -> h.assertTrue(over.isPositionEntityTicking(PRIMARY)
                && nether.isPositionEntityTicking(OTHER_PORTAL), "Waiting for both stationary portal chunks"))
                .thenExecute(() -> { if (!verify) StationaryNetherGameTests.assemble(h, over, PRIMARY.below()); })
                .thenWaitUntil(() -> h.assertTrue(over.getBlockEntity(PRIMARY) instanceof NetherTransformerBlockEntity,
                        "Waiting for native linked primary"))
                .thenExecute(() -> {
                    if (verify) return;
                    var exit = PortalTrackProvider.getOtherSide(over, new BlockFace(PRIMARY.below(), Direction.EAST));
                    h.assertTrue(exit != null && exit.level() == nether, "Native Nether destination is missing");
                    secondary[0] = exit.face().getPos().above();
                    WorldWidePowerGrid.LOGGER.info("NETHER_LIFECYCLE_SETUP: destination={} state={} entity={}",
                            secondary[0], nether.getBlockState(secondary[0]), nether.getBlockEntity(secondary[0]));
                    over.setBlockAndUpdate(SUPPLY, CEEBlocks.CREATIVE_BATTERY.getDefaultState());
                    nether.setBlockAndUpdate(secondary[0].north(3), CEEBlocks.CREATIVE_RESISTOR.getDefaultState());
                }).thenWaitUntil(() -> {
                    h.assertTrue(nether.getBlockEntity(secondary[0]) instanceof NetherTransformerBlockEntity,
                            "Waiting for the native destination transformer at " + secondary[0]
                                    + ": " + nether.getBlockState(secondary[0]));
                    h.assertTrue(org.cha0scollective.wwpg.wiring.Terminals.pgLocalPosition(nether, secondary[0], 0) != null
                                    && org.cha0scollective.wwpg.wiring.Terminals.pgLocalPosition(nether, secondary[0], 1) != null,
                            "Waiting for native destination terminals at " + secondary[0]);
                }).thenIdle(10).thenExecute(() -> {
                    if (verify) return;
                    DevicesSavedData.load(over).getDevice(SUPPLY, CreativeBatteryDevice.class).voltage = 40;
                    var load = secondary[0].north(3);
                    // A PG cord leg and CEE spool leg on each side retain native identities.
                    WiringGameTests.connect(h, over, SUPPLY, 1, PRIMARY, 0, true);
                    WiringGameTests.connect(h, over, SUPPLY, 0, PRIMARY, 1, false);
                    WiringGameTests.connect(h, nether, secondary[0], 0, load, 0, false);
                    WiringGameTests.connect(h, nether, secondary[0], 1, load, 1, true);
                    var wire = nativeSpool();
                    CEEWireInteractionBehaviours.BANNER.get().interactWire(new NodeConnectionPoint(wire.node1(), wire.node2(), .5f),
                            over, h.makeMockPlayer(GameType.SURVIVAL), Items.RED_BANNER.getDefaultInstance());
                }).thenWaitUntil(() -> {
                    h.assertTrue(over.areEntitiesLoaded(new ChunkPos(PRIMARY).toLong())
                            && nether.areEntitiesLoaded(new ChunkPos(secondary[0]).toLong()), "Waiting for saved native wire entities");
                    near(h, StationaryNetherGameTests.voltage(nether, secondary[0].north(3)), 40, .1,
                            "Linked mixed load after initial/cold startup");
                }).thenExecute(() -> {
                    primaryIds.addAll(ids(over, PRIMARY)); secondaryIds.addAll(ids(nether, secondary[0]));
                    h.assertTrue(primaryIds.size() == 1 && secondaryIds.size() == 1, "Expected one native PG wire identity at each end");
                    if (verify) {
                        h.assertTrue(primaryIds.iterator().next().toString().equals(expected[0].getString("PrimaryWire"))
                                && secondaryIds.iterator().next().toString().equals(expected[0].getString("SecondaryWire")),
                                "Cold restart replaced or duplicated Nether wire identities");
                    }
                    h.assertTrue(decoration(over).getString("BaseColor").equals("red"), "Native saved wire decoration was not installed");
                });
        for (int cycle = 1; cycle <= 5; cycle++) {
            final int index = cycle;
            final boolean unloadPrimary = cycle % 2 == 1;
            var level = unloadPrimary ? over : nether;
            var anchor = unloadPrimary ? PORTAL : OTHER_PORTAL;
            var before = new CompoundTag[]{null};
            sequence.thenExecute(() -> {
                var endpoint = unloadPrimary ? PRIMARY : secondary[0];
                before[0] = level.getBlockEntity(endpoint).saveWithoutMetadata(level.registryAccess());
                level.getDataStorage().save(); level.getChunkSource().save(true);
                StationaryNetherGameTests.force(level, anchor, false);
            }).thenIdle(330).thenWaitUntil(() -> {
                level.getChunkSource().tick(() -> true, true);
                var chunk = new ChunkPos(unloadPrimary ? PRIMARY : secondary[0]);
                h.assertTrue(level.getChunkSource().getChunkNow(chunk.x, chunk.z) == null
                        && (unloadPrimary ? primaryUnloads.get() >= (index + 1) / 2 : secondaryUnloads.get() >= index / 2),
                        "Waiting for actual independently unloaded Nether end " + index);
            }).thenExecute(() -> {
                var survivingLevel = unloadPrimary ? nether : over;
                var survivingPos = unloadPrimary ? secondary[0] : PRIMARY;
                var controller = ((NetherTransformerAccessor) survivingLevel.getBlockEntity(survivingPos)).wwpg$controller();
                h.assertTrue(controller.secondary == null && controller.getCurrent() == 0,
                        "Unloaded Nether end retained a live controller or source current");
                if (unloadPrimary) near(h, StationaryNetherGameTests.voltage(nether, secondary[0].north(3)), 0, .001,
                        "Unloaded source left Nether ghost power");
                var persisted = disk(level, unloadPrimary ? PRIMARY : secondary[0]);
                h.assertTrue(persisted.getUUID("Link").equals(before[0].getUUID("Link")),
                        "Actual unload changed its native saved link identity");
                before[0] = persisted;
                for (int x = (anchor.getX() >> 4) - 1; x <= (anchor.getX() >> 4) + 1; x++)
                    for (int z = (anchor.getZ() >> 4) - 1; z <= (anchor.getZ() >> 4) + 1; z++)
                        DelayedEntityLoads.hold(level, new ChunkPos(x, z), 60);
                StationaryNetherGameTests.force(level, anchor, true);
                saved(h, level, unloadPrimary ? PRIMARY : secondary[0], before[0], "Chunk cycle " + index);
            }).thenIdle(20).thenExecute(() -> {
                var chunk = new ChunkPos(unloadPrimary ? PRIMARY : secondary[0]);
                h.assertTrue(!level.areEntitiesLoaded(chunk.toLong()), "Delayed Nether wire entity loading was not exercised");
                var world = GlobalElectricNetworks.getWorldNetworks(level);
                h.assertTrue((unloadPrimary ? primaryIds : secondaryIds).stream().allMatch(id -> world.getPart(id) != null),
                        "Native Nether wire identity expired while its entities were delayed");
                if (unloadPrimary) near(h, StationaryNetherGameTests.voltage(nether, secondary[0].north(3)), 0, .001,
                        "Unready source-side entities allowed stale Nether power");
            }).thenWaitUntil(() -> {
                h.assertTrue(over.areEntitiesLoaded(new ChunkPos(PRIMARY).toLong())
                        && nether.areEntitiesLoaded(new ChunkPos(secondary[0]).toLong()), "Waiting for both linked-end wire entities");
                near(h, StationaryNetherGameTests.voltage(nether, secondary[0].north(3)), 40, .1,
                        "Independent-end reload did not recover mixed power");
            }).thenExecute(() -> {
                h.assertTrue(ids(over, PRIMARY).equals(primaryIds) && ids(nether, secondary[0]).equals(secondaryIds),
                        "Independent-end reload replaced or duplicated native wires");
                h.assertTrue(decoration(over).getString("BaseColor").equals("red"), "Actual chunk reload lost wire decoration");
                WorldWidePowerGrid.LOGGER.info("NETHER_CYCLE: cycle={} unloaded={} primaryUnloads={} secondaryUnloads={}",
                        index, unloadPrimary ? "primary" : "secondary", primaryUnloads.get(), secondaryUnloads.get());
            });
        }
        sequence.thenExecute(() -> {
            var snapshot = new CompoundTag();
            snapshot.putInt("X", secondary[0].getX()); snapshot.putInt("Y", secondary[0].getY()); snapshot.putInt("Z", secondary[0].getZ());
            snapshot.put("Primary", over.getBlockEntity(PRIMARY).saveWithoutMetadata(over.registryAccess()));
            snapshot.put("Secondary", nether.getBlockEntity(secondary[0]).saveWithoutMetadata(nether.registryAccess()));
            snapshot.putString("PrimaryWire", primaryIds.iterator().next().toString());
            snapshot.putString("SecondaryWire", secondaryIds.iterator().next().toString());
            snapshot.put("WireAttachment", decoration(over));
            try { NbtIo.writeCompressed(snapshot, SNAPSHOT); }
            catch (IOException e) { throw new IllegalStateException(e); }
            over.getDataStorage().save(); over.getChunkSource().save(true);
            nether.getDataStorage().save(); nether.getChunkSource().save(true);
            NeoForge.EVENT_BUS.unregister(listener);
            // Keep the saved linked circuit for the next separate server JVM.
            StationaryNetherGameTests.force(over, PORTAL, false);
            StationaryNetherGameTests.force(nether, OTHER_PORTAL, false);
            DynamicGameTests.audit(h);
        }).thenSucceed();
    }
    private static Set<WorldNetworks.PartId> ids(ServerLevel level, BlockPos endpoint) {
        var result = new HashSet<WorldNetworks.PartId>();
        var world = GlobalElectricNetworks.getWorldNetworks(level);
        for (int terminal = 0; terminal < 2; terminal++) {
            var parts = world.findConnectedWires(new BlockWireEndpoint(endpoint, terminal));
            if (parts != null) for (var part : parts) result.add(part.persistentOwnerId);
        }
        return result;
    }
    private static void saved(GameTestHelper h, ServerLevel level, BlockPos pos, CompoundTag expected, String label) {
        var entity = level.getBlockEntity(pos);
        h.assertTrue(entity instanceof NetherTransformerBlockEntity, label + " lost its native transformer");
        var actual = entity.saveWithoutMetadata(level.registryAccess());
        h.assertTrue(actual.getUUID("Link").equals(expected.getUUID("Link"))
                        && actual.getBoolean("Secondary") == expected.getBoolean("Secondary")
                        && actual.getList("TrAvgDat", 6).equals(expected.getList("TrAvgDat", 6))
                        && actual.getInt("AvgHead") == expected.getInt("AvgHead"),
                label + " lost native link, polarity, history or sample position before its first solve");
    }
    private static CompoundTag disk(ServerLevel level, BlockPos pos) {
        var chunk = level.getChunkSource().chunkMap.read(new ChunkPos(pos)).join().orElseThrow(() ->
                new IllegalStateException("Missing saved Nether chunk at " + pos));
        for (var value : chunk.getList("block_entities", 10)) {
            var tag = (CompoundTag) value;
            if (tag.getInt("x") == pos.getX() && tag.getInt("y") == pos.getY() && tag.getInt("z") == pos.getZ()) return tag;
        }
        throw new IllegalStateException("Missing serialized native Nether transformer at " + pos);
    }
    private static InWorldNodeConnection nativeSpool() {
        return new InWorldNodeConnection(new InWorldNode(0, SUPPLY), new InWorldNode(1, PRIMARY));
    }
    private static CompoundTag decoration(ServerLevel level) {
        var wire = InfrastructureSavedData.load(level).getConnectionData(nativeSpool());
        if (wire == null || wire.attachments().size() != 1) throw new net.minecraft.gametest.framework.GameTestAssertException("Saved native banner attachment is missing");
        var entry = wire.attachments().getFirst();
        var tag = entry.getSecond().write();
        tag.putFloat("Point", entry.getFirst());
        return tag;
    }
    private static void near(GameTestHelper h, double value, double expected, double tolerance, String message) {
        BoardComponentGameTests.near(h, value, expected, tolerance, message);
    }
}
