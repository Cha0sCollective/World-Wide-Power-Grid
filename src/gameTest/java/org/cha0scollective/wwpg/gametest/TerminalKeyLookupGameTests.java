package org.cha0scollective.wwpg.gametest;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.experimental.TerminalKeyLookup;
import org.cha0scollective.wwpg.extension.NativeApiReadings;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

/** Differential identity checks against the retained generated record and PG's original hash. */
@GameTestHolder("wwpg_loading")
@PrefixGameTestTemplate(false)
public final class TerminalKeyLookupGameTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void nativeKeysKeepGeneratedEqualityAndImmutablePositions(GameTestHelper h) throws ReflectiveOperationException {
        Method factory = NativeApiReadings.class.getDeclaredMethod("key", BlockPos.class, int.class);
        factory.setAccessible(true);
        Constructor<?> original = Class.forName(NativeApiReadings.class.getName() + "$StandardKey")
                .getDeclaredConstructor(BlockPos.class, int.class);
        original.setAccessible(true);
        var random = new Random(482911);
        for (int i = 0; i < 512; i++) {
            var pos = new BlockPos(random.nextInt(), random.nextInt(), random.nextInt());
            int id = random.nextInt();
            var otherPos = i % 3 == 0 ? pos.offset(1, 0, 0) : new BlockPos(pos.getX(), pos.getY(), pos.getZ());
            int otherId = i % 3 == 1 ? id ^ 1 : id;
            Object expected = original.newInstance(pos, id), expectedOther = original.newInstance(otherPos, otherId);
            Object actual = factory.invoke(null, pos, id), other = factory.invoke(null, otherPos, otherId);
            h.assertTrue(actual.getClass().getSimpleName().equals(TerminalKeyLookup.ENABLED ? "DirectKey" : "StandardKey"),
                    "Requested key implementation was not exercised");
            h.assertTrue(actual.hashCode() == expected.hashCode() && other.hashCode() == expectedOther.hashCode(), "Terminal hash changed");
            h.assertTrue(actual.equals(other) == expected.equals(expectedOther) && other.equals(actual) == expectedOther.equals(expected),
                    "Terminal equality changed");
            h.assertTrue(actual.equals(actual) && !actual.equals(null) && !actual.equals(pos), "Terminal key type/reflexivity changed");
            var mutable = new BlockPos.MutableBlockPos(pos.getX(), pos.getY(), pos.getZ());
            Object frozen = factory.invoke(null, mutable, id);
            var map = new HashMap<Object, String>();
            map.put(frozen, "frame");
            mutable.move(1, -1, 1);
            h.assertTrue("frame".equals(map.get(factory.invoke(null, pos, id))) && frozen.equals(actual),
                    "Mutable position changed a retained frame key");
        }
        var position = new CountedPosition();
        for (int i = 0; i < 3; i++) {
            var otherPosition = i == 1 ? new CountedPosition() : position;
            int otherId = i == 2 ? 8 : 7;
            Object expected = original.newInstance(position, 7), expectedOther = original.newInstance(otherPosition, otherId);
            Object actual = factory.invoke(null, position, 7), other = factory.invoke(null, otherPosition, otherId);
            position.equalCalls = 0;
            boolean equal = expected.equals(expectedOther);
            int expectedCalls = position.equalCalls;
            position.equalCalls = 0;
            h.assertTrue(actual.equals(other) == equal && position.equalCalls == expectedCalls,
                    "Position equality callback/identity short-circuit changed");
        }
        com.mojang.logging.LogUtils.getLogger().info("WWPG terminal key equivalence: nativePairs=512, referenceCases=3, enabled={}", TerminalKeyLookup.ENABLED);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void wireEndpointHashesKeepSavedAndMutableIdentity(GameTestHelper h) {
        BlockPos[] positions = {null, BlockPos.ZERO, new BlockPos(-1, -2048, 1),
                new BlockPos(30_000_000, 4096, -30_000_000), new BlockPos(Integer.MIN_VALUE, Integer.MAX_VALUE, -1)};
        int[] ids = {Integer.MIN_VALUE, -1, 0, 1, 127, 128, 32768, Integer.MAX_VALUE};
        int compared = 0;
        for (var pos : positions) for (int id : ids) {
            var endpoint = new BlockWireEndpoint(pos, id);
            h.assertTrue(endpoint.hashCode() == Objects.hash(pos, id), "PG endpoint hash changed");
            if (pos != null) {
                var tag = new CompoundTag(); endpoint.write(tag);
                var restored = new BlockWireEndpoint(); restored.read(tag);
                var owners = new HashMap<BlockWireEndpoint, Object>();
                Object owner = new Object(); owners.put(endpoint, owner);
                h.assertTrue(endpoint.equals(restored) && owners.get(restored) == owner, "Saved endpoint lost map ownership");
                var mutable = new BlockPos.MutableBlockPos(pos.getX(), pos.getY(), pos.getZ());
                var moving = new BlockWireEndpoint(mutable, id);
                mutable.move(1, -1, 1);
                h.assertTrue(moving.hashCode() == Objects.hash(mutable, id), "Endpoint hash retained a stale mutable position");
                moving.read(tag);
                h.assertTrue(moving.hashCode() == endpoint.hashCode() && owners.get(moving) == owner, "Deserialization retained a stale endpoint hash");
            }
            compared++;
        }
        var position = new CountedPosition();
        var endpoint = new BlockWireEndpoint(position, 128);
        int expected = Objects.hash(position, 128); position.calls = 0;
        h.assertTrue(endpoint.hashCode() == expected && position.calls == 1, "Position hash callback count changed");
        com.mojang.logging.LogUtils.getLogger().info("WWPG terminal key equivalence: endpointCases={}, enabled={}", compared, TerminalKeyLookup.ENABLED);
        h.succeed();
    }

    private static final class CountedPosition extends BlockPos {
        int calls, equalCalls;
        CountedPosition() { super(1, 2, 3); }
        @Override public int hashCode() { calls++; return 0x12345678; }
        @Override public boolean equals(Object other) { equalCalls++; return super.equals(other); }
    }
}
