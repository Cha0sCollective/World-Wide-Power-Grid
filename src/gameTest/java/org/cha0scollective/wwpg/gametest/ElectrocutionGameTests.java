package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.CEEMobEffects;
import com.george_vi.electroenergetics.CEEWireTypes;
import com.george_vi.electroenergetics.config.CEEConfigs;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.content.electronic_components.resistor.ResistorDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.foundation.QuadraticWireHelper;
import com.george_vi.electroenergetics.foundation.nodes.AttachedNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import com.george_vi.electroenergetics.simulation.WireType;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import com.george_vi.electroenergetics.simulation.infrastructure.ConnectionEntry;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.WorldWidePowerGrid;
import org.cha0scollective.wwpg.bridge.BridgeSimulationResults;
import org.cha0scollective.wwpg.bridge.ContactHazardHistory;
import org.cha0scollective.wwpg.mixin.ElectrocutionEntryAccessor;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.node.IElectricNode;
import org.patryk3211.powergrid.electricity.sim.solver.ISolverHook;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@GameTestHolder("wwpg_cee_electrocution")
@PrefixGameTestTemplate(false)
public final class ElectrocutionGameTests {
    private static final String BATCH = "wwpg_cee_electrocution";
    private static boolean previousElectrocution;
    private static Rejection activeRejection;

    @BeforeBatch(batch = BATCH)
    public static void setup(ServerLevel level) {
        DynamicGameTests.substeps(level);
        previousElectrocution = CEEConfigs.server().enableElectrocution.get();
        CEEConfigs.server().enableElectrocution.set(true);
    }

    @AfterBatch(batch = BATCH)
    public static void cleanup(ServerLevel level) {
        if (activeRejection != null) activeRejection.close();
        CEEConfigs.server().enableElectrocution.set(previousElectrocution);
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 105)
    public static void copperContactDamagesAndSettles(GameTestHelper h) {
        var fixture = new Fixture(h, CEEWireTypes.COPPER.get(), 23_000, false);
        fixture.start();
        h.runAtTickTime(45, () -> {
            h.assertTrue(fixture.mob.getHealth() < fixture.initialHealth, "Bare wire did not damage the mob");
            h.assertTrue(fixture.mob.isAlive(), "Mob died before sustained contact could be verified");
            h.assertTrue(fixture.bodyNodes.size() == 1, "Body node identity changed during the same contact");
            h.assertTrue(fixture.unavailableAfterSettling == 0, "Wire readings stayed unavailable during contact");
            fixture.assertHealthyNeighbor();
            fixture.mob.discard();
        });
        h.runAtTickTime(60, () -> {
            h.assertTrue(Double.isFinite(fixture.wireVoltage()), "Wire did not recover after contact removal");
            h.assertTrue(!fixture.contacts().containsKey(fixture.mob), "Removed mob left an active contact");
            fixture.finish("contact-settled");
        });
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 80)
    public static void dielectricKeepsNativeImmunity(GameTestHelper h) {
        var fixture = new Fixture(h, CEEWireTypes.COPPER.get(), 23_000, true);
        fixture.start();
        h.runAtTickTime(45, () -> fixture.assertNoContact("dielectric"));
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 80)
    public static void inRatingInsulationKeepsNativeImmunity(GameTestHelper h) {
        var wire = CEEWireTypes.STANDARD.get();
        double voltage = Math.min(100, wire.maxInsulationVoltage() / 2);
        h.assertTrue(voltage > 0, "Fixture wire must have a positive insulation rating");
        var fixture = new Fixture(h, wire, voltage, false);
        fixture.start();
        h.runAtTickTime(45, () -> fixture.assertNoContact("insulated"));
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 105)
    public static void rejectedSolveClearsHazardAndContactRecovers(GameTestHelper h) {
        var fixture = new Fixture(h, CEEWireTypes.COPPER.get(), 23_000, false);
        fixture.start();
        var rejection = new Rejection(fixture);
        h.runAtTickTime(18, rejection::open);
        h.runAtTickTime(45, () -> {
            rejection.close();
            h.assertTrue(rejection.rejections > 0, "Fixture never rejected a solver step");
            h.assertTrue(fixture.mob.getHealth() == fixture.initialHealth, "Rejected solve damaged the mob");
            var results = (BridgeSimulationResults) fixture.results();
            h.assertTrue(!results.available(fixture.first) && Double.isNaN(fixture.wireVoltage()),
                    "Rejected readings must remain unavailable");
            var history = (ContactHazardHistory) fixture.wire();
            h.assertTrue(Double.isNaN(history.wwpg$initializingHazard(h.getLevel().getGameTime())),
                    "Rejected solve retained an accepted hazard");
            fixture.assertHealthyNeighbor();
        });
        h.runAtTickTime(75, () -> {
            h.assertTrue(fixture.mob.getHealth() < fixture.initialHealth, "Contact did not recover after rejection");
            h.assertTrue(Double.isFinite(fixture.wireVoltage()), "Readings did not recover after rejection");
            fixture.finish("reject-recover");
        });
    }

    private static final class Rejection {
        final Fixture fixture;
        final Set<ElectricalNetwork> networks = Collections.newSetFromMap(new IdentityHashMap<>());
        int rejections;
        final ISolverHook hook = new ISolverHook() {
            @Override public Collection<IElectricNode> coupledNodes() { return List.of(); }
            @Override public void startIteration(int iteration) {
                ++rejections;
                throw new IllegalStateException("Electrocution fixture deliberately rejected this solve");
            }
        };
        final java.util.function.Consumer<LevelTickEvent.Pre> listener = this::reject;

        Rejection(Fixture fixture) { this.fixture = fixture; }

        void open() {
            activeRejection = this;
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, listener);
        }

        void reject(LevelTickEvent.Pre event) {
            if (event.getLevel() != fixture.level || fixture.mob == null) return;
            var node = new BlockWireEndpoint(fixture.source, 1).getNode(fixture.level);
            if (node != null && node.getNetwork() != null && networks.add(node.getNetwork()))
                node.getNetwork().innerHooks.add(hook);
        }

        void close() {
            NeoForge.EVENT_BUS.unregister(listener);
            networks.forEach(network -> network.innerHooks.remove(hook));
            networks.clear();
            activeRejection = null;
        }
    }

    private static final class Fixture {
        final GameTestHelper test;
        final ServerLevel level;
        final BlockPos source, load, neighborSource, neighborLoad;
        final InWorldNode first, second;
        final WireType wireType;
        final double voltage;
        final boolean dielectric;
        final Set<AttachedNode> bodyNodes = Collections.newSetFromMap(new IdentityHashMap<>());
        Cow mob;
        float initialHealth;
        int unavailableAfterSettling;

        Fixture(GameTestHelper test, WireType wireType, double voltage, boolean dielectric) {
            this.test = test;
            level = test.getLevel();
            this.wireType = wireType;
            this.voltage = voltage;
            this.dielectric = dielectric;
            source = at(1, 1);
            load = at(11, 1);
            first = new InWorldNode(0, at(3, 1));
            second = new InWorldNode(0, at(9, 1));
            neighborSource = at(1, 7);
            neighborLoad = at(5, 7);
        }

        BlockPos at(int x, int z) { return test.absolutePos(new BlockPos(x, 2, z)); }
        InfrastructureSavedData data() { return InfrastructureSavedData.load(level); }
        SimulationResults results() { return data().ticker.lastResults; }
        double wireVoltage() { return results().getVoltageAt(first); }

        ConnectionEntry wire() {
            var span = new InWorldNodeConnection(first, second);
            return data().wireSimulationState.getAllConnections().stream().filter(entry -> entry.getKey().equals(span))
                    .findFirst().orElseThrow().getValue();
        }

        Map<?, ?> contacts() {
            var module = data().wireElectrocutionModule;
            try {
                var field = module.getClass().getDeclaredField("electrocutions");
                field.setAccessible(true);
                return (Map<?, ?>) field.get(module);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Could not observe native CEE contacts", error);
            }
        }

        void start() {
            for (var pos : List.of(source, first.sourcePos(), second.sourcePos(), load, neighborSource, neighborLoad))
                level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(source, CEEBlocks.CREATIVE_BATTERY.getDefaultState());
            level.setBlockAndUpdate(load, CEEBlocks.CREATIVE_RESISTOR.getDefaultState());
            level.setBlockAndUpdate(first.sourcePos(), CEEBlocks.CONNECTOR.getDefaultState());
            level.setBlockAndUpdate(second.sourcePos(), CEEBlocks.CONNECTOR.getDefaultState());
            level.setBlockAndUpdate(neighborSource, ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.getDefaultState());
            level.setBlockAndUpdate(neighborLoad, ModdedBlocks.CREATIVE_RESISTOR.getDefaultState());
            test.runAtTickTime(5, this::energize);
            test.runAtTickTime(20, this::spawnContact);
            test.onEachTick(this::observe);
        }

        void energize() {
            var devices = DevicesSavedData.load(level);
            devices.getDevice(source, CreativeBatteryDevice.class).voltage = voltage;
            devices.getDevice(load, ResistorDevice.class).properties = ElectricalProperties.resistor(1000);
            var infrastructure = data();
            infrastructure.connect(new InWorldNode(1, source), first, wireType);
            infrastructure.connect(first, second, wireType);
            infrastructure.connect(second, new InWorldNode(0, load), wireType);
            infrastructure.connect(new InWorldNode(0, source), new InWorldNode(1, load), wireType);
            ((CreativeSourceBlockEntity) level.getBlockEntity(neighborSource)).setValue(10);
            ((ResistorBlockEntity) level.getBlockEntity(neighborLoad)).setValue(1000);
            WiringGameTests.connect(test, neighborSource, 0, neighborLoad, 0, true);
            WiringGameTests.connect(test, neighborSource, 1, neighborLoad, 1, true);
        }

        void spawnContact() {
            test.assertTrue(wireVoltage() > voltage * .99, "Fixture must contact the energized span");
            var a = first.getPosition(level);
            var b = second.getPosition(level);
            var wireData = data().getConnectionData(new InWorldNodeConnection(first, second));
            var midpoint = QuadraticWireHelper.posAt(a, b, .5f, wireData.getSag(a.distanceTo(b)));
            mob = EntityType.COW.create(level);
            mob.setNoAi(true);
            mob.setPersistenceRequired();
            mob.setNoGravity(true);
            mob.setOnGround(true);
            mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
            mob.setHealth(1000);
            initialHealth = mob.getHealth();
            if (dielectric) mob.addEffect(new MobEffectInstance(CEEMobEffects.DIELECTRIC, 200));
            mob.setPos(midpoint.x, midpoint.y - .9, midpoint.z);
            level.addFreshEntity(mob);
        }

        void observe() {
            if (mob == null || !mob.isAlive()) return;
            mob.setOnGround(true);
            var entry = (ElectrocutionEntryAccessor) contacts().get(mob);
            if (entry != null) bodyNodes.add(entry.wwpg$centralNode());
            if (test.getTick() > 28 && !Double.isFinite(wireVoltage())) ++unavailableAfterSettling;
        }

        void assertHealthyNeighbor() {
            var a = new BlockWireEndpoint(neighborLoad, 0).getNode(level);
            var b = new BlockWireEndpoint(neighborLoad, 1).getNode(level);
            test.assertTrue(a != null && b != null && Math.abs(a.getVoltage() - b.getVoltage() - 10) < .05,
                    "A neighboring healthy circuit was disturbed");
        }

        void assertNoContact(String protection) {
            test.assertTrue(mob.getHealth() == initialHealth && !contacts().containsKey(mob),
                    protection + " allowed damage or a conductive contact");
            finish(protection);
        }

        void finish(String outcome) {
            assertHealthyNeighbor();
            WorldWidePowerGrid.LOGGER.info("CEE_ELECTROCUTION {} health={} initial={} unavailableAfterSettling={} bodyNodes={} voltage={}",
                    outcome, mob.getHealth(), initialHealth, unavailableAfterSettling, bodyNodes.size(), wireVoltage());
            mob.discard();
            test.succeed();
        }
    }
}
