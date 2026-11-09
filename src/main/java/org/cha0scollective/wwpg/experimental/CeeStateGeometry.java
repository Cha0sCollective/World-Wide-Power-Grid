package org.cha0scollective.wwpg.experimental;

import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.george_vi.electroenergetics.content.electronic_components.resistor.ResistorBlock;
import com.george_vi.electroenergetics.content.electronic_components.capacitor.CapacitorBlock;
import com.george_vi.electroenergetics.content.electronic_components.inductor.InductorBlock;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryBlock;
import com.george_vi.electroenergetics.content.transmission_distribution.transformer.TransformerBlock;
import com.george_vi.electroenergetics.content.resistive_heater.ResistiveHeaterBlock;
import com.george_vi.electroenergetics.content.connector.ConnectorBlock;
import com.george_vi.electroenergetics.content.connector.InsulatorBlock;
import com.george_vi.electroenergetics.content.bulb.BulbBlock;
import com.george_vi.electroenergetics.content.fuse.FuseBlock;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.cha0scollective.wwpg.WorldWidePowerGrid;

/** Opt-in immutable geometry for reviewed CEE built-ins. No world/owner state is cached. */
public final class CeeStateGeometry {
    public static final boolean ENABLED = Boolean.getBoolean("wwpg.experimental.ceeStateGeometry");
    private static final int MAX_STATES = 2048;
    // Exact classes only. Indicator bulbs mutate a shared node map and are excluded;
    // panels and all custom/world-dependent geometry retain their original calls.
    private static final Set<Class<?>> REVIEWED = Set.of(ResistorBlock.class, CapacitorBlock.class,
            InductorBlock.class, CreativeBatteryBlock.class, TransformerBlock.class,
            ResistiveHeaterBlock.class, ConnectorBlock.class, InsulatorBlock.class,
            BulbBlock.class, FuseBlock.class);
    private static final ClassValue<Boolean> STATE_ONLY = new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> type) {
            if (!REVIEWED.contains(type)) return false;
            try {
                return type.getMethod("getNodePositions", Level.class, BlockPos.class, BlockState.class).getDeclaringClass() == type
                        && type.getMethod("isNodeAccessible", Level.class, BlockPos.class, BlockState.class, int.class)
                            .getDeclaringClass() == ElectricalDeviceBlock.class;
            } catch (ReflectiveOperationException | SecurityException error) { return false; }
        }
    };
    public record Snapshot(Map<Integer, Vec3> positions, IntSet nativeIds) { }
    private static final class State {
        final Map<BlockState, Snapshot> values = new IdentityHashMap<>();
        long requests, hits, builds, fallback;
    }
    // Only a server's owning thread can enter. Values contain block states and
    // immutable coordinates/IDs, never a level, position, entity or circuit.
    private static final ThreadLocal<State> CACHE = ThreadLocal.withInitial(State::new);
    private CeeStateGeometry() { }

    public static boolean stateOnly(Class<?> type) { return STATE_ONLY.get(type); }

    /** Null preserves the complete existing callback path. Loaded-owner guards stay at callers. */
    public static Snapshot snapshot(ElectricalDeviceBlock<?> block, Level level, BlockPos pos, BlockState state) {
        if (!ENABLED || !(level instanceof ServerLevel server) || !server.getServer().isSameThread()) return null;
        var cache = CACHE.get();
        cache.requests++;
        if (state.getBlock() != block || !stateOnly(block.getClass())) { cache.fallback++; return null; }
        var found = cache.values.get(state);
        if (found != null) { cache.hits++; return found; }
        if (cache.values.size() >= MAX_STATES) { cache.fallback++; return null; }
        // These exact built-ins read only state and fixed node configurations.
        // Their inherited accessibility method is always true. Copy the original
        // map; do not expose or change CEE's memoized maps.
        var positions = Collections.unmodifiableMap(new TreeMap<>(block.getNodePositions(level, pos, state)));
        var ids = IntSets.unmodifiable(new IntOpenHashSet(positions.keySet()));
        found = new Snapshot(positions, ids);
        cache.values.put(state, found);
        cache.builds++;
        return found;
    }

    public static void reportAndClear() {
        if (ENABLED) {
            var cache = CACHE.get();
            WorldWidePowerGrid.LOGGER.info("WWPG experimental CEE state geometry: requests={}, hits={}, builds={}, fallback={}, states={}",
                    cache.requests, cache.hits, cache.builds, cache.fallback, cache.values.size());
        }
        CACHE.remove();
    }
}
