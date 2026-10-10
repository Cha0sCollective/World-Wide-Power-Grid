package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.bulb.BulbBlock;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.simibubi.create.AllItems;
import com.simibubi.create.api.contraption.train.PortalTrackProvider;
import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;
import org.patryk3211.powergrid.electricity.transformer.NetherTransformerBlockEntity;
import org.patryk3211.powergrid.electricity.transformer.TransformerCoreBlock;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

@GameTestHolder("wwpg_distribution")
@PrefixGameTestTemplate(false)
public final class StationaryNetherGameTests {
    @GameTest(template = "empty", timeoutTicks = 700)
    public static void nativePortalLinkTransfersMixedPowerAndStopsAfterSourceAndPortalRemoval(GameTestHelper h) {
        portalCircuit(h, false);
    }
    @GameTest(template = "empty", timeoutTicks = 700)
    public static void pgOnlyPortalReferenceStopsAfterSourceAndPortalRemoval(GameTestHelper h) {
        portalCircuit(h, true);
    }
    private static void portalCircuit(GameTestHelper h, boolean nativeOnly) {
        var overworld = h.getLevel();
        var nether = overworld.getServer().getLevel(Level.NETHER);
        // Separate destructive portal fixtures from saved electronics at
        // (1536,64,1536) and the durable Nether link at (32768,64,32768).
        // VERIFY runs all fixtures again; a portal must not replace the saved
        // source and discharge its capacitor before its restart check begins.
        var portal = new BlockPos(nativeOnly ? 41024 : 40000, 64, 40000);
        var otherPortal = new BlockPos(nativeOnly ? 5128 : 5000, 64, 5000);
        force(overworld, portal, true);
        force(nether, otherPortal, true);
        portal(h, overworld, portal);
        portal(h, nether, otherPortal);
        var base = portal.west();
        var primary = base.above();
        var supply = base.west(3);
        var localLoad = supply.north(3);
        var remote = new BlockPos[1];
        var remoteSource = new BlockPos[1];
        var remoteLoad = new BlockPos[1];
        h.startSequence().thenWaitUntil(() -> h.assertTrue(overworld.isPositionEntityTicking(base)
                && nether.isPositionEntityTicking(otherPortal), "Waiting for both portal chunks to become ticking"))
        .thenExecute(() -> {
            assemble(h, overworld, base);
        }).thenWaitUntil(() -> h.assertTrue(overworld.getBlockEntity(primary) instanceof NetherTransformerBlockEntity,
                "Waiting for native portal transformation: base=" + overworld.getBlockState(base)
                        + ", top=" + overworld.getBlockState(primary) + ", portal=" + overworld.getBlockState(portal)
                        + ", pending=" + overworld.getBlockTicks().hasScheduledTick(base, ModdedBlocks.TRANSFORMER_CORE.get())))
        .thenExecute(() -> {
            var found = PortalTrackProvider.getOtherSide(overworld, new BlockFace(base, Direction.EAST));
            h.assertTrue(found != null && found.level() == nether, "Native portal provider did not find the Nether side");
            remote[0] = found.face().getPos().above();
            force(nether, remote[0], true);
            h.assertTrue(nether.getBlockEntity(remote[0]) instanceof NetherTransformerBlockEntity,
                    "Native portal workflow did not create the linked remote transformer at " + remote[0]);
            var a = overworld.getBlockEntity(primary).saveWithoutMetadata(overworld.registryAccess());
            var b = nether.getBlockEntity(remote[0]).saveWithoutMetadata(nether.registryAccess());
            h.assertTrue(a.hasUUID("Link") && a.getUUID("Link").equals(b.getUUID("Link"))
                    && a.getBoolean("Secondary") != b.getBoolean("Secondary"), "Native portal link identities do not match");
            remoteSource[0] = remote[0].north(4);
            remoteLoad[0] = remote[0].north(2);
            overworld.setBlockAndUpdate(supply, nativeOnly ? ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.getDefaultState()
                    : CEEBlocks.CREATIVE_BATTERY.getDefaultState());
            overworld.setBlockAndUpdate(localLoad, ModdedBlocks.CREATIVE_RESISTOR.getDefaultState());
            nether.setBlockAndUpdate(remoteSource[0], ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.getDefaultState());
            nether.setBlockAndUpdate(remoteLoad[0], nativeOnly ? ModdedBlocks.CREATIVE_RESISTOR.getDefaultState() : CEEBlocks.BULB.getDefaultState());
        }).thenIdle(10).thenExecute(() -> {
            power(overworld, supply, 40, nativeOnly);
            if (nativeOnly) ((ResistorBlockEntity) nether.getBlockEntity(remoteLoad[0])).setValue(1000);
            ((ResistorBlockEntity) overworld.getBlockEntity(localLoad)).setValue(1000);
            ((CreativeSourceBlockEntity) nether.getBlockEntity(remoteSource[0])).setValue(0);
            WiringGameTests.connect(h, overworld, supply, nativeOnly ? 0 : 1, primary, 0, true);
            WiringGameTests.connect(h, overworld, supply, nativeOnly ? 1 : 0, primary, 1, nativeOnly);
            WiringGameTests.connect(h, overworld, primary, 0, localLoad, 0, nativeOnly);
            WiringGameTests.connect(h, overworld, primary, 1, localLoad, 1, true);
            WiringGameTests.connect(h, nether, remote[0], 0, remoteLoad[0], 0, nativeOnly);
            WiringGameTests.connect(h, nether, remote[0], 1, remoteLoad[0], 1, true);
        }).thenIdle(70).thenExecute(() -> {
            near(h, voltage(nether, remoteLoad[0]), 40, .2, "CEE supply through native Nether link");
            h.assertTrue(nativeOnly || nether.getBlockState(remoteLoad[0]).getValue(BulbBlock.LIGHT) > 0,
                    "Nether link did not visibly power the remote CEE bulb");
            power(overworld, supply, 0, nativeOnly);
        }).thenIdle(70).thenExecute(() -> {
            near(h, voltage(nether, remoteLoad[0]), 0, .001, "Disconnected source left Nether ghost power");
            overworld.setBlockAndUpdate(supply, Blocks.AIR.defaultBlockState());
            WiringGameTests.connect(h, nether, remoteSource[0], 0, remote[0], 0, true);
            WiringGameTests.connect(h, nether, remoteSource[0], 1, remote[0], 1, false);
            ((CreativeSourceBlockEntity) nether.getBlockEntity(remoteSource[0])).setValue(40);
        }).thenIdle(70).thenExecute(() -> {
            near(h, voltage(overworld, localLoad), 40, .2, "PG supply failed to transfer power back to the Overworld");
            ((CreativeSourceBlockEntity) nether.getBlockEntity(remoteSource[0])).setValue(0);
        }).thenIdle(70).thenExecute(() -> {
            near(h, voltage(overworld, localLoad), 0, .001, "Removed reverse supply left Nether ghost power");
            ((CreativeSourceBlockEntity) nether.getBlockEntity(remoteSource[0])).setValue(40);
        }).thenIdle(70).thenExecute(() -> {
            near(h, voltage(overworld, localLoad), 40, .2, "Restored reverse supply failed to transfer power");
            overworld.setBlockAndUpdate(portal, Blocks.AIR.defaultBlockState());
        }).thenIdle(25).thenExecute(() -> {
            h.assertTrue(!ModdedBlocks.NETHER_TRANSFORMER.has(overworld.getBlockState(primary)),
                    "Native transformer remained after portal disruption");
            near(h, voltage(overworld, localLoad), 0, .001, "Disrupted portal left Overworld ghost power");
            overworld.setBlockAndUpdate(supply, Blocks.AIR.defaultBlockState());
            overworld.setBlockAndUpdate(localLoad, Blocks.AIR.defaultBlockState());
            nether.setBlockAndUpdate(remoteSource[0], Blocks.AIR.defaultBlockState());
            nether.setBlockAndUpdate(remoteLoad[0], Blocks.AIR.defaultBlockState());
            force(overworld, portal, false);
            force(nether, otherPortal, false);
            force(nether, remote[0], false);
            DynamicGameTests.audit(h);
        }).thenSucceed();
    }

    static void assemble(GameTestHelper h, ServerLevel level, BlockPos base) {
        var exit = PortalTrackProvider.getOtherSide(level, new BlockFace(base, Direction.EAST));
        h.assertTrue(exit != null, "Native portal provider did not locate the prepared destination");
        org.cha0scollective.wwpg.WorldWidePowerGrid.LOGGER.info("NETHER_FIXTURE: native exit {} in {} facing {}",
                exit.face().getPos(), exit.level().dimension().location(), exit.face().getFace());
        level.setBlockAndUpdate(base, ModdedBlocks.TRANSFORMER_CORE.getDefaultState());
        level.setBlockAndUpdate(base.above(), ModdedBlocks.TRANSFORMER_CORE.getDefaultState());
        var player = new net.minecraft.world.entity.player.Player(level, base, 0,
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "WWPG-link-test")) {
            @Override public boolean isSpectator() { return false; }
            @Override public boolean isCreative() { return false; }
            @Override public void sendSystemMessage(net.minecraft.network.chat.Component message) {
                org.cha0scollective.wwpg.WorldWidePowerGrid.LOGGER.info("NETHER_FIXTURE: {}", message.getString());
            }
        };
        player.setItemInHand(InteractionHand.MAIN_HAND, AllItems.WRENCH.asStack());
        var context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(base.getCenter(), Direction.WEST, base, false));
        h.assertTrue(((TransformerCoreBlock) level.getBlockState(base).getBlock())
                .onWrenched(level.getBlockState(base), context).consumesAction(), "Native Nether assembly wrench failed");
    }

    static void portal(GameTestHelper h, ServerLevel level, BlockPos inner) {
        // Two by three interior in the Y/Z plane, with a complete obsidian frame.
        for (int z = -1; z <= 2; z++) for (int y = -1; y <= 3; y++)
            level.setBlockAndUpdate(inner.offset(0, y, z), (z == -1 || z == 2 || y == -1 || y == 3)
                    ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.AIR.defaultBlockState());
        // Leave physical room on both faces; a valid portal inside Nether terrain
        // does not imply that PG's two-block destination can be placed there.
        for (int x : new int[] {-1, 1}) for (int z = 0; z <= 1; z++) for (int y = 0; y <= 2; y++)
            level.setBlockAndUpdate(inner.offset(x, y, z), Blocks.AIR.defaultBlockState());
        var player = new net.minecraft.world.entity.player.Player(level, inner, 0,
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "WWPG-portal-test")) {
            @Override public boolean isSpectator() { return false; }
            @Override public boolean isCreative() { return false; }
        };
        player.setItemInHand(InteractionHand.MAIN_HAND, net.minecraft.world.item.Items.FLINT_AND_STEEL.getDefaultInstance());
        var floor = inner.below();
        h.assertTrue(player.getMainHandItem().getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(floor.getCenter().add(0, .5, 0), Direction.UP, floor, false))).consumesAction(),
                "Native portal ignition failed");
        h.assertTrue(level.getBlockState(inner).is(Blocks.NETHER_PORTAL), "Complete native portal frame failed to light");
    }
    static void force(ServerLevel level, BlockPos pos, boolean value) {
        for (int x = (pos.getX() >> 4) - 1; x <= (pos.getX() >> 4) + 1; x++)
            for (int z = (pos.getZ() >> 4) - 1; z <= (pos.getZ() >> 4) + 1; z++) {
                level.setChunkForced(x, z, value);
                if (value) level.getChunk(x, z);
            }
    }
    static double voltage(ServerLevel level, BlockPos pos) {
        var first = new BlockWireEndpoint(pos, 0).getNode(level);
        var second = new BlockWireEndpoint(pos, 1).getNode(level);
        if (first == null || second == null) throw new net.minecraft.gametest.framework.GameTestAssertException(
                "Missing native fixture terminal at " + level.dimension().location() + " " + pos + ": " + level.getBlockState(pos));
        return first.getVoltage() - second.getVoltage();
    }
    private static void power(ServerLevel level, BlockPos pos, double value, boolean pg) {
        if (pg) ((CreativeSourceBlockEntity) level.getBlockEntity(pos)).setValue((float) value);
        else DevicesSavedData.load(level).getDevice(pos, CreativeBatteryDevice.class).voltage = value;
    }
    private static void near(GameTestHelper h, double value, double expected, double tolerance, String message) {
        BoardComponentGameTests.near(h, value, expected, tolerance, message);
    }
}
