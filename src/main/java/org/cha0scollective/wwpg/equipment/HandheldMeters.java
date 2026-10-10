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
import org.patryk3211.powergrid.electricity.wire.CircuitBoardEndpoint;
import org.patryk3211.powergrid.electricity.wire.IWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.WireEndpointType;
import org.patryk3211.powergrid.equipment.multimeter.MultimeterItem;


/** Item selections retain native identities; measurements come from the existing solve. */
public final class HandheldMeters {
    public static final String CEE_WIRE = "WWPG_CeeWire";
    public static final String CURRENT = "WWPG_Current";
    public static final String PG_WIRE = "WWPG_PgWire";

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
            if (wire instanceof BaseWireEntity pg) data.putFloat(CURRENT, finiteCurrent(pg));
            else data.remove(CURRENT);
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
        double current = results == null ? 0 : results.getCurrentThrough(connection.node1(), connection.node2());
        data.putFloat(CURRENT, Double.isFinite(current) ? (float) Math.abs(current) : 0);
        MultimeterItem.saveModeData(stack, data);
    }

    private static void cacheVoltage(Level level, CompoundTag data, String selection, String value) {
        data.remove(value);
        if (!data.contains(selection)) return;
        IWireEndpoint endpoint = WireEndpointType.deserialize(data.getCompound(selection));
        if (endpoint == null || !endpoint.isValid(level)) return;
        var node = endpoint instanceof CircuitBoardEndpoint board ? board.getGenericNode(level) : endpoint.getNode(level);
        double voltage = node == null ? 0 : node.getVoltage();
        data.putFloat(value, Double.isFinite(voltage) ? (float) voltage : 0);
    }

    public static InteractionResult attachClamp(BaseWireEntity wire, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !(player.getMainHandItem().getItem() instanceof ClampMeterItem))
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
        data.remove(PG_WIRE); data.remove(CURRENT);
        if (data.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
        else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
    }

    public static void tickClamp(ItemStack stack, ServerLevel level) {
        var data=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        if(!data.hasUUID(PG_WIRE))return;
        var entity=level.getEntity(data.getUUID(PG_WIRE));
        data.putFloat(CURRENT,entity instanceof BaseWireEntity wire&&wire.isAlive()?finiteCurrent(wire):0);
        stack.set(DataComponents.CUSTOM_DATA,CustomData.of(data));
    }

    private static float finiteCurrent(BaseWireEntity wire) {
        float current=wire.measuredCurrent();
        return Float.isFinite(current)?Math.abs(current):0;
    }

}
