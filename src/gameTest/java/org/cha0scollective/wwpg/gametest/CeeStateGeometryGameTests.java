package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.electronic_components.resistor.ResistorBlock;
import com.george_vi.electroenergetics.devices.device.SimulatedDevice;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import org.cha0scollective.wwpg.experimental.CeeStateGeometry;
import org.cha0scollective.wwpg.extension.NativeApiReadings;
import org.cha0scollective.wwpg.wiring.Terminals;

@GameTestHolder("wwpg_cee_state_geometry")
@PrefixGameTestTemplate(false)
public final class CeeStateGeometryGameTests {
    @BeforeBatch(batch = "wwpg_cee_state_geometry")
    public static void configure(ServerLevel level) { DynamicGameTests.substeps(level); }

    @GameTest(template = "empty", batch = "wwpg_cee_state_geometry", timeoutTicks = 100)
    public static void everyReviewedStateRetainsCoordinatesIdsAndOrder(GameTestHelper h) {
        int states = 0;
        var types = new HashSet<Class<?>>();
        for (var block : BuiltInRegistries.BLOCK) {
            if (!(block instanceof ElectricalDeviceBlock<?> electric) || !CeeStateGeometry.stateOnly(block.getClass())) continue;
            // Verify every state on the cache path, including light/logging states.
            // Overflow is covered independently below, not mistaken for bad geometry.
            CeeStateGeometry.reportAndClear();
            types.add(block.getClass());
            for (var state : block.getStateDefinition().getPossibleStates()) {
                var expected = new TreeMap<Integer, Vec3>();
                for (var entry : electric.getNodePositions(h.getLevel(), BlockPos.ZERO, state).entrySet())
                    if (electric.isNodeAccessible(h.getLevel(), BlockPos.ZERO, state, entry.getKey())) expected.put(entry.getKey(), entry.getValue());
                var actual = CeeStateGeometry.snapshot(electric, h.getLevel(), BlockPos.ZERO, state);
                h.assertTrue(resolve(electric, h.getLevel(), BlockPos.ZERO, state).equals(new IntOpenHashSet(expected.keySet())), "Native IDs differ for " + state);
                if (CeeStateGeometry.ENABLED) {
                    h.assertTrue(actual != null && actual.positions().equals(expected), "Coordinates differ for " + state);
                    h.assertTrue(new ArrayList<>(actual.positions().keySet()).equals(new ArrayList<>(expected.keySet())), "Native ID order changed");
                    h.assertTrue(actual == CeeStateGeometry.snapshot(electric, h.getLevel(), new BlockPos(10, 20, 30), state), "State geometry depended on owner position");
                    try { actual.positions().put(-1, Vec3.ZERO); h.fail("Cached coordinates are mutable"); } catch (UnsupportedOperationException expectedError) { }
                    try { actual.nativeIds().add(-1); h.fail("Cached IDs are mutable"); } catch (UnsupportedOperationException expectedError) { }
                } else h.assertTrue(actual == null, "Disabled prototype returned a cache entry");
                states++;
            }
        }
        h.assertTrue(types.size() == 10 && states > 100, "Reviewed geometry coverage was incomplete: " + types.size() + "/" + states);
        h.assertTrue(!CeeStateGeometry.stateOnly(CEEBlocks.ELECTRICAL_PANEL.get().getClass())
                && !CeeStateGeometry.stateOnly(CEEBlocks.INDICATOR_BULB.get().getClass())
                && !CeeStateGeometry.stateOnly(CustomResistor.class), "Dynamic or custom type entered the cache");
        com.mojang.logging.LogUtils.getLogger().info("WWPG CEE geometry: {} reviewed types, {} states compared, enabled={}", types.size(), states, CeeStateGeometry.ENABLED);
        h.succeed();
    }

    @GameTest(template = "empty", batch = "wwpg_cee_state_geometry", timeoutTicks = 100)
    public static void overflowFallsBackWithoutLosingTerminalIds(GameTestHelper h) {
        CeeStateGeometry.reportAndClear();
        int states = 0, cached = 0, fallback = 0;
        for (var block : BuiltInRegistries.BLOCK) {
            if (!(block instanceof ElectricalDeviceBlock<?> electric) || !CeeStateGeometry.stateOnly(block.getClass())) continue;
            for (var state : block.getStateDefinition().getPossibleStates()) {
                var expected = new IntOpenHashSet(electric.getNodePositions(h.getLevel(), BlockPos.ZERO, state).keySet());
                var actual = CeeStateGeometry.snapshot(electric, h.getLevel(), BlockPos.ZERO, state);
                if (actual == null) fallback++; else cached++;
                h.assertTrue(resolve(electric, h.getLevel(), BlockPos.ZERO, state).equals(expected), "Overflow lost native terminals for " + state);
                states++;
            }
        }
        h.assertTrue(states > 2048 && fallback > 0 && cached == (CeeStateGeometry.ENABLED ? 2048 : 0), "Cache bound/fallback was not exercised");
        CeeStateGeometry.reportAndClear();
        h.succeed();
    }

    @GameTest(template = "empty", batch = "wwpg_cee_state_geometry", timeoutTicks = 100)
    public static void customSparseCallbacksStayLiveAndFailuresPropagate(GameTestHelper h) {
        var device = new DynamicGeometry();
        var state = Blocks.STONE.defaultBlockState();
        for (int offset : new int[]{0, 10, 0}) {
            device.offset = offset;
            device.calls.clear();
            var expected = new IntOpenHashSet();
            for (int id : device.getNodePositions(h.getLevel(), BlockPos.ZERO, state).keySet())
                if (device.isNodeAccessible(h.getLevel(), BlockPos.ZERO, state, id)) expected.add(id);
            var calls = new ArrayList<>(device.calls);
            device.calls.clear();
            h.assertTrue(resolve(device, h.getLevel(), BlockPos.ZERO, state).equals(expected), "Sparse custom IDs became stale");
            h.assertTrue(device.calls.equals(calls), "Custom geometry callback order/count changed");
        }
        device.fail = true;
        try { resolve(device, h.getLevel(), BlockPos.ZERO, state); h.fail("Geometry failure was hidden"); }
        catch (IllegalStateException expected) { h.assertTrue(expected.getMessage().equals("intentional geometry failure"), "Wrong failure"); }
        device.fail = false;
        h.assertTrue(resolve(device, h.getLevel(), BlockPos.ZERO, state).contains(7), "Geometry failed to recover");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "wwpg_cee_state_geometry", timeoutTicks = 180)
    public static void poweredPanelEditsRemainLive(GameTestHelper h) {
        CeeTerminalGeometryGameTests.poweredSparsePanelOperationsObserveLiveGeometry(h);
    }

    @GameTest(template = "empty", batch = "wwpg_cee_state_geometry", timeoutTicks = 100)
    public static void replacementStillInvalidatesTheOriginalOwner(GameTestHelper h) {
        var relative = new BlockPos(2, 2, 2);
        h.setBlock(relative.below(), Blocks.STONE);
        h.setBlock(relative, CEEBlocks.CAPACITOR.get());
        var pos = h.absolutePos(relative);
        h.startSequence().thenIdle(5).thenExecute(() -> {
            var adapter = Terminals.cee(h.getLevel(), pos);
            h.assertTrue(adapter != null && adapter.behaviour().hasTerminal(0), "Could not warm live terminal geometry");
            h.setBlock(relative, Blocks.STONE);
            h.assertTrue(!adapter.behaviour().hasTerminal(0) && adapter.terminalCount() == 0, "Cached geometry revived a replaced owner");
        }).thenSucceed();
    }

    private static IntSet resolve(ElectricalDeviceBlock<?> block, ServerLevel level, BlockPos pos, BlockState state) {
        try {
            var method = NativeApiReadings.class.getDeclaredMethod("ceeTerminalIds", ElectricalDeviceBlock.class, ServerLevel.class, BlockPos.class, BlockState.class);
            method.setAccessible(true);
            return (IntSet) method.invoke(null, block, level, pos, state);
        } catch (InvocationTargetException error) {
            if (error.getCause() instanceof RuntimeException cause) throw cause;
            throw new AssertionError(error.getCause());
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static final class CustomResistor extends ResistorBlock {
        CustomResistor() { super(Properties.of(), true); }
    }
    private static final class DynamicGeometry implements ElectricalDeviceBlock<SimulatedDevice> {
        final List<String> calls = new ArrayList<>();
        int offset;
        boolean fail;
        @Override public SimulatedDeviceType<SimulatedDevice> getDevice() { return null; }
        @Override public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
            calls.add("positions");
            if (fail) throw new IllegalStateException("intentional geometry failure");
            var result = new TreeMap<Integer, Vec3>();
            result.put(7 + offset, Vec3.ZERO); result.put(42 + offset, new Vec3(1, 0, 0));
            return result;
        }
        @Override public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int node) { return getNodePositions(level, pos, state).get(node); }
        @Override public boolean isNodeAccessible(Level level, BlockPos pos, BlockState state, int node) { calls.add("accessible:" + node); return node == 7 + offset; }
    }
}
