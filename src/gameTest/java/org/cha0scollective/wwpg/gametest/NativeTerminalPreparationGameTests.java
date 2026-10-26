package org.cha0scollective.wwpg.gametest;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.extension.NativeApiReadings;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.base.ITerminalPlacement;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;

@GameTestHolder("wwpg_loading")
@PrefixGameTestTemplate(false)
public final class NativeTerminalPreparationGameTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void everyNativeBlockStateRetainsOriginalTerminalIds(GameTestHelper h) {
        var prepared = new IdentityHashMap<BlockState, IntSet>();
        int compared = 0;
        for (var block : BuiltInRegistries.BLOCK) {
            if (!BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals("powergrid") || !(block instanceof IElectric electric)) continue;
            for (var state : block.getStateDefinition().getPossibleStates()) {
                var expected = original(electric, state);
                var actual = resolve(electric, state, prepared);
                h.assertTrue(actual.equals(expected), "Prepared terminal IDs differ for " + state);
                var repeated = resolve(electric, state, prepared);
                h.assertTrue(repeated.equals(expected), "Repeated terminal IDs differ for " + state);
                if (prepared.containsKey(state)) h.assertTrue(repeated == actual, "State-only geometry was rebuilt within one preparation");
                h.assertTrue(resolve(electric, state, new IdentityHashMap<>()) != actual,
                        "Terminal identity set escaped its preparation pass");
                compared++;
            }
        }
        h.assertTrue(compared > 100 && prepared.size() > 20, "Native-state equivalence fixture did not exercise shared geometry");
        com.mojang.logging.LogUtils.getLogger().info("WWPG terminal preparation: {} states compared, {} state-only sets", compared, prepared.size());
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void customSparseGeometryAndFailuresKeepOriginalCallbacks(GameTestHelper h) {
        var electric = new CustomTerminals();
        var state = Blocks.STONE.defaultBlockState();
        var prepared = new IdentityHashMap<BlockState, IntSet>();
        for (int offset : new int[]{0, 10, 0}) {
            electric.offset = offset;
            electric.calls.clear();
            var expected = original(electric, state);
            var calls = new ArrayList<>(electric.calls);
            electric.calls.clear();
            h.assertTrue(resolve(electric, state, prepared).equals(expected), "Custom sparse IDs became stale");
            h.assertTrue(electric.calls.equals(calls), "Custom terminal callback order/count changed");
            h.assertTrue(prepared.isEmpty(), "Custom owner was incorrectly treated as state-only");
        }
        electric.fail = true;
        try { resolve(electric, state, prepared); h.fail("Custom geometry failure was hidden"); }
        catch (IllegalStateException expected) { h.assertTrue(expected.getMessage().equals("intentional terminal failure"), "Wrong geometry failure"); }
        electric.fail = false;
        h.assertTrue(resolve(electric, state, prepared).equals(original(electric, state)), "Custom geometry did not recover");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void nativeSmallTransformerKeepsPoweredReadings(GameTestHelper h) {
        PgTransformerGameTests.assembledSmallTransformerStepUp(h);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void nativeMultiblockTransformerKeepsRemappedTerminalIds(GameTestHelper h) {
        PgTransformerGameTests.assembledMediumTransformerStepUp(h);
    }

    private static IntSet original(IElectric electric, BlockState state) {
        int count = electric.terminalCount();
        var result = new IntOpenHashSet(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            var placement = electric.terminal(state, i);
            if (placement != null) result.add(electric.terminalIndexAt(state, placement.getOrigin()));
        }
        return result;
    }

    private static IntSet resolve(IElectric electric, BlockState state, Map<BlockState, IntSet> prepared) {
        try {
            var method = NativeApiReadings.class.getDeclaredMethod("nativeTerminalIds", IElectric.class, BlockState.class, Map.class);
            method.setAccessible(true);
            return (IntSet) method.invoke(null, electric, state, prepared);
        } catch (InvocationTargetException error) {
            if (error.getCause() instanceof RuntimeException cause) throw cause;
            throw new AssertionError(error.getCause());
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static final class CustomTerminals implements IElectric {
        final ArrayList<String> calls = new ArrayList<>();
        int offset;
        boolean fail;
        @Override public int terminalCount() { calls.add("count"); return 3; }
        @Override public ITerminalPlacement terminal(BlockState state, int local) {
            calls.add("terminal:" + local);
            if (fail) throw new IllegalStateException("intentional terminal failure");
            if (local == 1) return null;
            var origin = new Vec3(local + offset, 0, 0);
            return new ITerminalPlacement() {
                @Override public boolean check(Vec3 point) { return origin.equals(point); }
                @Override public Vec3 getOrigin() { return origin; }
            };
        }
        @Override public int terminalIndexAt(BlockState state, Vec3 point) {
            calls.add("index:" + point.x);
            return point.x == offset ? 7 + offset : 42 + offset;
        }
    }
}
