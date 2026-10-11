package org.cha0scollective.wwpg.equipment;

import com.george_vi.electroenergetics.CEEDataComponents;
import com.george_vi.electroenergetics.content.clamp_meter.ClampMeterItem;
import com.george_vi.electroenergetics.foundation.nodes.NodeConnectionPoint;
import com.george_vi.electroenergetics.foundation.QuadraticWireHelper;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import org.patryk3211.powergrid.collections.ModdedConfigs;
import org.patryk3211.powergrid.electricity.wire.BaseWireEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.CircuitBoardEndpoint;
import org.patryk3211.powergrid.electricity.wire.IWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.WireEndpointType;
import org.patryk3211.powergrid.equipment.multimeter.MultimeterItem;


/** Item selections retain native identities; measurements come from the existing solve. */
public final class HandheldMeters {
    public static final String CEE_WIRE = "WWPG_CeeWire";
    public static final String CURRENT = "WWPG_Current";
    public static final String PG_WIRE = "WWPG_PgWire";
    public static final String UNAVAILABLE = "WWPG_ReadingUnavailable";

    private HandheldMeters() {}


    public static void clearCeeWire(ItemStack stack) {
        stack.remove(CEEDataComponents.NODE_CONNECTION);
        var data = MultimeterItem.getModeData(stack);
        if (!data.contains(CEE_WIRE) && !data.contains(CURRENT)) return;
        data.remove(CEE_WIRE); data.remove(CURRENT);
        MultimeterItem.saveModeData(stack, data);
    }

    public static boolean attachMultimeter(NodeConnectionPoint point, ServerLevel level, Player player, ItemStack stack) {
        if (!(stack.getItem() instanceof MultimeterItem meter)) return false;
        var connection = point.connection();
        var infrastructure = InfrastructureSavedData.load(level);
        var wire = infrastructure.getConnectionData(connection);
        if (wire == null) return false;
        var a = point.node1().getPosition(level);
        var b = point.node2().getPosition(level);
        if (a == null || b == null || point.point() < 0 || point.point() > 1) return false;
        var position = QuadraticWireHelper.posAt(a,b,point.point(),wire.getSag(a.distanceTo(b)));
        double range = ModdedConfigs.server().equipment.multimeterDistance.get();
        if (player.distanceToSqr(position) > range * range) return false;
        meter.setMode(stack, 1);
        stack.set(CEEDataComponents.NODE_CONNECTION, connection);
        var data = MultimeterItem.getModeData(stack);
        data.putBoolean(CEE_WIRE, true);
        data.putDouble("X", position.x); data.putDouble("Y", position.y); data.putDouble("Z", position.z);
        data.putFloat(CURRENT, 0);
        MultimeterItem.saveModeData(stack, data);
        return true;
    }

    public static void tickMultimeter(ItemStack stack, ServerLevel level) {
        var meter = (MultimeterItem) stack.getItem();
        var data = MultimeterItem.getModeData(stack);
        data.remove(UNAVAILABLE);
        if (meter.getMode(stack) == 0) {
            // CEE's adapter is not a placed PG block entity. Synchronize the
            // probe voltages in the native item's data, which PG already reads.
            cacheVoltage(level, data, "Pos", "PosV");
            cacheVoltage(level, data, "Neg", "NegV");
            MultimeterItem.saveModeData(stack, data);
            clearCeeWire(stack);
            return;
        }
        var connection = stack.get(CEEDataComponents.NODE_CONNECTION);
        if (meter.getMode(stack) == 1 && !data.getBoolean(CEE_WIRE)) {
            // PG's client-side wire estimate cannot reconstruct a CEE source.
            // Use the native server wire's solved current for mixed networks.
            var wire = data.hasUUID("UUID") ? level.getEntity(data.getUUID("UUID")) : null;
            if (wire instanceof BaseWireEntity pg) {
                float current = finiteCurrent(pg);
                data.putFloat(CURRENT, current);
                if (!Float.isFinite(current)) data.putBoolean(UNAVAILABLE, true);
            }
            else { data.remove(CURRENT); if (data.hasUUID("UUID")) data.putBoolean(UNAVAILABLE, true); }
            MultimeterItem.saveModeData(stack,data);
            return;
        }
        if (meter.getMode(stack) != 1 || connection == null) {
            clearCeeWire(stack);
            return;
        }
        var infrastructure = InfrastructureSavedData.load(level);
        if (!level.hasChunkAt(connection.node1().sourcePos()) || !level.hasChunkAt(connection.node2().sourcePos())
                || infrastructure.getConnectionData(connection) == null) {
            clearCeeWire(stack);
            MultimeterItem.deleteModeData(stack);
            return;
        }
        var results = infrastructure.ticker.lastResults;
        boolean rejected = rejectedTerminal(level, connection.node1().sourcePos(), connection.node1().id())
                || rejectedTerminal(level, connection.node2().sourcePos(), connection.node2().id());
        double current = rejected || results == null ? Double.NaN : results.getCurrentThrough(connection.node1(), connection.node2());
        data.putFloat(CURRENT, Double.isFinite(current) ? (float) Math.abs(current) : Float.NaN);
        if (!Double.isFinite(current)) data.putBoolean(UNAVAILABLE, true);
        MultimeterItem.saveModeData(stack, data);
    }

    private static void cacheVoltage(Level level, CompoundTag data, String selection, String value) {
        data.remove(value);
        if (!data.contains(selection)) return;
        IWireEndpoint endpoint = WireEndpointType.deserialize(data.getCompound(selection));
        if (endpoint == null || !endpoint.isValid(level)) { data.putBoolean(UNAVAILABLE, true); return; }
        if (rejectedEndpoint(level, endpoint)) { data.putBoolean(UNAVAILABLE, true); return; }
        var node = endpoint instanceof CircuitBoardEndpoint board ? board.getGenericNode(level) : endpoint.getNode(level);
        double voltage = node == null ? Double.NaN : node.getVoltage();
        if (!Double.isFinite(voltage)) data.putBoolean(UNAVAILABLE, true);
        else data.putFloat(value, (float) voltage);
    }

    public static InteractionResult attachClamp(BaseWireEntity wire, Player player, InteractionHand hand) {
        boolean nativeStick = com.george_vi.electroenergetics.CEEItems.LINEMANS_STICK.isIn(player.getMainHandItem())
                && player.getOffhandItem().getItem() instanceof ClampMeterItem;
        if (hand != InteractionHand.MAIN_HAND || !(player.getMainHandItem().getItem() instanceof ClampMeterItem) && !nativeStick)
            return InteractionResult.PASS;
        var stack = player.getMainHandItem();
        player.releaseUsingItem();
        var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        data.putUUID(PG_WIRE, wire.getUUID());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    public static void clearPgWire(ItemStack stack) {
        var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!data.contains(PG_WIRE)) return;
        data.remove(PG_WIRE); data.remove(CURRENT); data.remove(UNAVAILABLE);
        if (data.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
        else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
    }

    public static void tickClamp(ItemStack stack, ServerLevel level) {
        var data=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        if(!data.hasUUID(PG_WIRE))return;
        var entity=level.getEntity(data.getUUID(PG_WIRE));
        float current=entity instanceof BaseWireEntity wire&&wire.isAlive()?finiteCurrent(wire):Float.NaN;
        data.putFloat(CURRENT,current);
        data.putBoolean(UNAVAILABLE,!Float.isFinite(current));
        stack.set(DataComponents.CUSTOM_DATA,CustomData.of(data));
    }

    private static float finiteCurrent(BaseWireEntity wire) {
        if(rejectedEndpoint(wire.level(),wire.getEndpoint1())||rejectedEndpoint(wire.level(),wire.getEndpoint2()))return Float.NaN;
        float current=wire.measuredCurrent();
        return Float.isFinite(current)?Math.abs(current):Float.NaN;
    }

    private static boolean rejectedEndpoint(Level level, IWireEndpoint endpoint) {
        if (!(level instanceof ServerLevel server) || !(endpoint instanceof BlockWireEndpoint block)) return false;
        return rejectedTerminal(server, block.getPos(), block.getTerminal());
    }

    private static boolean rejectedTerminal(ServerLevel server, net.minecraft.core.BlockPos position, int nativeId) {
        var status = org.cha0scollective.wwpg.api.neoforge.ElectricalMeasurements
                .readTerminal(server, position, nativeId).status();
        return status != org.cha0scollective.wwpg.api.MeasurementStatus.VALID;
    }

    /** Queries current acceptance without refreshing probes or creating graph objects. */
    public static boolean selectionUnavailable(Level level, ItemStack stack) {
        if (!(level instanceof ServerLevel server) || !(stack.getItem() instanceof MultimeterItem meter)) return false;
        var data = MultimeterItem.getModeData(stack);
        if (meter.getMode(stack) == 0) {
            for (var key : new String[]{"Pos", "Neg"}) if (data.contains(key)) {
                var endpoint = WireEndpointType.deserialize(data.getCompound(key));
                if (endpoint == null || !endpoint.isValid(server) || rejectedEndpoint(server, endpoint)) return true;
            }
        } else if (meter.getMode(stack) == 1) {
            var connection = stack.get(CEEDataComponents.NODE_CONNECTION);
            if (connection != null && data.getBoolean(CEE_WIRE))
                return InfrastructureSavedData.load(server).getConnectionData(connection) == null
                        || rejectedTerminal(server, connection.node1().sourcePos(), connection.node1().id())
                        || rejectedTerminal(server, connection.node2().sourcePos(), connection.node2().id());
            var entity = data.hasUUID("UUID") ? server.getEntity(data.getUUID("UUID")) : null;
            if (entity instanceof BaseWireEntity wire)
                return rejectedEndpoint(server, wire.getEndpoint1()) || rejectedEndpoint(server, wire.getEndpoint2());
            if (data.hasUUID("UUID")) return true;
        }
        return false;
    }

}
