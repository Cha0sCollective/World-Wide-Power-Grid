package org.cha0scollective.wwpg.acceptance;

import com.george_vi.electroenergetics.CEEItems;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlockEntity;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelSlot;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.cha0scollective.wwpg.WorldWidePowerGrid;
import org.cha0scollective.wwpg.gametest.ExtensionApiClientSession;
import org.patryk3211.powergrid.circuits.circuitboard.CircuitBoardBlockEntity;
import org.patryk3211.powergrid.circuits.editor.CircuitDesignTableBlockEntity;
import org.patryk3211.powergrid.circuits.editor.CircuitDesignTableEditScreen;
import org.patryk3211.powergrid.circuits.editor.CircuitDesignTableScreen;
import org.patryk3211.powergrid.collections.ModdedPackets;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.HangingWireEntity;
import org.patryk3211.powergrid.network.packets.ChangeScreenC2SPacket;
import org.patryk3211.powergrid.network.packets.SaveSchematicC2SPacket;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.ArrayDeque;

/** Disposable graphical driver: native input APIs and network packets, never server handlers. */
final class ApiWorkshopClientAcceptance {
    private static final int RECONNECT_OBSERVER_DEADLINE_TICKS = 600;
    private static final int REPAIR_OBSERVER_DEADLINE_TICKS = 600;
    private static final int BOARD_OBSERVER_DEADLINE_TICKS = 800;
    private final boolean multiplayer = "multiplayer".equals(System.getProperty("wwpg.test.apiMode"));
    private final long started = System.currentTimeMillis();
    private volatile String failure;
    private ExtensionApiClientSession integrated;
    private String phase = "";
    private int age, stopping;
    private boolean completed, displayConfigured;
    private final ArrayDeque<Vec3> movement = new ArrayDeque<>();
    private Vec3 movementTarget;
    private int movementSettling;

    ApiWorkshopClientAcceptance() {
        NeoForge.EVENT_BUS.addListener(ClientChatReceivedEvent.System.class, this::chat);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, this::tick);
        if (!multiplayer) NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, this::server);
        WorldWidePowerGrid.LOGGER.info("API_WORKSHOP_CLIENT_HELPER: mode={}; profile={}; backend={}", multiplayer ? "multiplayer" : "graphical", System.getProperty("wwpg.test.profile"), System.getProperty("wwpg.test.backend"));
    }
    private void chat(ClientChatReceivedEvent.System event) {
        var text = event.getMessage().getString();
        if (text.startsWith("API_WORKSHOP_TEST ")) {
            phase = text.substring("API_WORKSHOP_TEST ".length()); age = 0;
            movement.clear(); movementTarget = null; movementSettling = 0;
            WorldWidePowerGrid.LOGGER.info("API_WORKSHOP_CLIENT_PHASE: {}", phase);
        }
    }
    private void server(ServerTickEvent.Post event) {
        if (failure != null || completed || event.getServer().getPlayerList().getPlayers().isEmpty()) return;
        try {
            if (integrated == null) {
                var extraction = JsonParser.parseString(Files.readString(Path.of("extracted-yard.json"))).getAsJsonObject();
                var save = Path.of("saves", extraction.get("source_save_name").getAsString());
                integrated = new ExtensionApiClientSession(event.getServer().overworld(), event.getServer().getPlayerList().getPlayers(), false, save);
            }
            integrated.tick();
        } catch (Throwable error) { WorldWidePowerGrid.LOGGER.error("API_WORKSHOP_SERVER_FAILED", error); failure = error.toString(); }
    }
    private void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance(); mc.options.pauseOnLostFocus = false;
        if (completed || failure != null) { if (++stopping >= 100) mc.stop(); return; }
        try {
            require(System.currentTimeMillis() - started < 900000, "API client exceeded fifteen minutes");
            if (!displayConfigured) {
                mc.options.guiScale().set(2);
                mc.resizeDisplay();
                require(mc.getWindow().getGuiScaledHeight() >= 272, "Native board editor requires at least 272 visible logical pixels");
                displayConfigured = true;
                WorldWidePowerGrid.LOGGER.info("API_WORKSHOP_DISPLAY_READY: requestedGuiScale=2; actualGuiScale={}; logicalWidth={}; logicalHeight={}",
                        mc.getWindow().getGuiScale(), mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
            }
            if (mc.level == null && mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) {
                for (var child : mc.screen.children()) if (child instanceof net.minecraft.client.gui.components.Button button && button.getMessage().getString().equals("Continue")) { button.onPress(); break; }
            }
            if (mc.level == null || mc.player == null || mc.gameMode == null || phase.isEmpty() || phase.equals("waiting")) return;
            if (phase.equals("done")) {
                WorldWidePowerGrid.LOGGER.info(multiplayer ? "API_WORKSHOP_CLIENT_PASSED" : "API_WORKSHOP_GRAPHICAL_PASSED"); completed = true; return;
            }
            if (!phase.equals("board_edit") && mc.screen != null) mc.setScreen(null);
            if (advanceMovement(mc)) return;
            ++age;
            boolean a = !multiplayer || mc.player.getGameProfile().getName().endsWith("A");
            switch (phase) {
                case "view" -> views(mc);
                case "wire" -> wires(mc, a);
                case "configure", "configure_on" -> configure(mc, a);
                case "meter" -> meters(mc, a);
                case "panel_edit" -> panel(mc);
                case "board_edit" -> board(mc, a);
                case "repair" -> repair(mc, a);
                case "repair_wire" -> reconnect(mc, a);
                default -> throw new IllegalStateException("Unknown API client phase " + phase);
            }
        } catch (Throwable error) {
            WorldWidePowerGrid.LOGGER.error("API_WORKSHOP_CLIENT_FAILED", error);
            if (mc.player != null) mc.player.connection.sendCommand("wwpg-api-ack failed");
            failure = error.toString();
        }
    }
    private void views(Minecraft mc) {
        int index = (age - 1) / 35, local = (age - 1) % 35;
        if (index >= 11) { finish(mc); return; }
        var pos = ExtensionApiClientSession.view(index);
        if (local == 0) face(mc, pos, Vec3.atCenterOf(pos));
        if (local != 30) return;
        require(!mc.level.getBlockState(pos).isAir(), "Client did not load A" + (index + 1));
        if (index < 9) {
            require(booleanState(mc, pos, "lit"), "API client block does not show accepted power/storage");
            var model = mc.getBlockRenderer().getBlockModel(mc.level.getBlockState(pos));
            require(model != mc.getModelManager().getMissingModel(), "API block rendered a missing model");
        } else if (index == 9) {
            var panel = (ElectricalPanelBlockEntity) mc.level.getBlockEntity(pos);
            var attachment = panel.getAttachments()[ElectricalPanelSlot.THIRD_LEFT.ordinal()];
            require(attachment != null, "Client custom panel attachment missing");
            var tag = new CompoundTag(); attachment.write(tag, true, mc.level.registryAccess());
            require(tag.getFloat("Light") > .95, "Client custom panel indicator is dark");
        } else {
            var board = (CircuitBoardBlockEntity) mc.level.getBlockEntity(pos);
            require(board != null && board.getComponentsStream().count() == 3, "Client custom board components missing");
        }
        String code = String.format("A%02d", index + 1);
        shot(mc, "api-" + code + ".png"); WorldWidePowerGrid.LOGGER.info("API_WORKSHOP_VIEW_PASSED: {}", code);
    }
    private void wires(Minecraft mc, boolean a) {
        wirePair(mc, a, age);
        if (!multiplayer && age == 125) creativeHand(mc, CEEItems.WIRE_SPOOL.asStack());
        if (!multiplayer && age > 125) wirePair(mc, false, age - 125);
        if (age == (multiplayer ? 160 : 285)) finish(mc);
    }
    private void wirePair(Minecraft mc, boolean a, int step) {
        if (step == 25 || step == 75) terminalLook(mc, ExtensionApiClientSession.source(a), step == 25 ? (a ? 0 : 1) : (a ? 1 : 0));
        if (step == 35 || step == 85) terminalClick(mc, ExtensionApiClientSession.source(a));
        if (step == 50 || step == 100) terminalLook(mc, ExtensionApiClientSession.load(a), step == 50 ? 0 : 1);
        if (step == 60 || step == 110) terminalClick(mc, ExtensionApiClientSession.load(a));
    }
    private void configure(Minecraft mc, boolean a) {
        var target = a ? ExtensionApiClientSession.source(true) : ExtensionApiClientSession.load(false);
        if (age == 25) face(mc, target, target.getCenter());
        if (age == 35) sneakClick(mc, target);
        if (!multiplayer && age == 60) { var second = ExtensionApiClientSession.load(false); face(mc, second, second.getCenter()); }
        if (!multiplayer && age == 70) sneakClick(mc, ExtensionApiClientSession.load(false));
        if (age == 110) {
            boolean enabled = phase.equals("configure_on");
            require(booleanState(mc, target, "enabled") == enabled, "Actual API enable click did not synchronize");
            if (!multiplayer) shot(mc, enabled ? "api-control-on.png" : "api-control-off.png");
            finish(mc);
        }
    }
    private void meters(Minecraft mc, boolean a) throws ReflectiveOperationException {
        if (a) {
            if (age == 25) terminalLook(mc, ExtensionApiClientSession.source(true), 0);
            if (age == 35) terminalClick(mc, ExtensionApiClientSession.source(true));
            if (age == 50) terminalLook(mc, ExtensionApiClientSession.source(true), 1);
            if (age == 60) terminalClick(mc, ExtensionApiClientSession.source(true));
            if (age == 95) {
                var stack = mc.player.getMainHandItem();
                var meter = (org.patryk3211.powergrid.equipment.multimeter.MultimeterItem) stack.getItem();
                near(meter.getMeasurement(mc.level, stack), 20, .05, "Native multimeter measures API terminals");
                if (multiplayer) { finish(mc); return; }
                creativeHand(mc, CEEItems.CLAMP_METER.asStack());
            }
        }
        int step = multiplayer ? age : age - 100;
        if ((!a || !multiplayer) && step == 25) wireLook(mc);
        if ((!a || !multiplayer) && step == 35) {
            require(mc.hitResult instanceof EntityHitResult && ((EntityHitResult) mc.hitResult).getEntity() instanceof HangingWireEntity, "CEE clamp ray missed the API PG wire");
            var hit = (EntityHitResult) mc.hitResult;
            mc.options.keyUse.setDown(true);
            require(mc.gameMode.interact(mc.player, hit.getEntity(), InteractionHand.MAIN_HAND).consumesAction(), "Native CEE clamp rejected API mixed wire");
        }
        if ((!a || !multiplayer) && step == 95) {
            require(mc.player.isUsingItem(), "Native clamp is not active");
            var overlay = com.george_vi.electroenergetics.client.ElectricPropertiesOverlay.INSTANCE;
            var field = overlay.getClass().getDeclaredField("amperage"); field.setAccessible(true);
            near(field.getFloat(overlay), .4, .02, "Native CEE clamp measures API load current");
            mc.options.keyUse.setDown(false); mc.gameMode.releaseUsingItem(mc.player); finish(mc);
        }
    }
    private void panel(Minecraft mc) {
        var pos = ExtensionApiClientSession.PANEL;
        if (age == 25) {
            var panel = (ElectricalPanelBlockEntity) mc.level.getBlockEntity(pos);
            var attachment = panel.getAttachments()[ElectricalPanelSlot.THIRD_LEFT.ordinal()];
            face(mc, pos, attachment.getCenter());
        }
        if (age == 35) blockClick(mc, pos);
        if (age == 100) {
            var panel = (ElectricalPanelBlockEntity) mc.level.getBlockEntity(pos);
            var tag = new CompoundTag(); panel.getAttachments()[ElectricalPanelSlot.THIRD_LEFT.ordinal()].write(tag, true, mc.level.registryAccess());
            require(tag.getString("Color").equals("blue") && tag.getFloat("Light") > .95, "Native panel dye edit did not synchronize or lost its light");
            if (!multiplayer) shot(mc, "api-panel-edit.png"); finish(mc);
        }
    }
    private void board(Minecraft mc, boolean a) throws ReflectiveOperationException {
        if (a) {
            var table = (CircuitDesignTableBlockEntity) mc.level.getBlockEntity(ExtensionApiClientSession.TABLE);
            if (age == 25) {
                face(mc, ExtensionApiClientSession.TABLE, ExtensionApiClientSession.TABLE.getCenter().add(0, .35, 0));
            }
            if (age == 35) blockClick(mc, ExtensionApiClientSession.TABLE);
            if (age == 65) {
                require(mc.screen instanceof CircuitDesignTableScreen && table.getSchematic().components().size() == 3, "Native custom-shunt design menu did not open");
                ModdedPackets.sendToServer(new ChangeScreenC2SPacket(table, 1));
            }
            if (age == 105) {
                require(mc.screen instanceof CircuitDesignTableEditScreen, "Native board design editor did not open");
                var field = CircuitDesignTableEditScreen.class.getDeclaredField("nameField"); field.setAccessible(true);
                var nameField = (net.minecraft.client.gui.components.EditBox) field.get(mc.screen);
                require(nameField.getX() >= 0 && nameField.getY() >= 0
                                && nameField.getRight() <= mc.screen.width && nameField.getBottom() <= mc.screen.height,
                        "Native design name field is outside the visible viewport");
                require(mc.screen.mouseClicked(nameField.getX() + 2, nameField.getY() + nameField.getHeight() / 2.0, 0)
                                && nameField.isFocused(), "Native design name field did not accept a visible mouse click");
                mc.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_END, 0, 0);
                int previousLength = nameField.getValue().length();
                for (int i = 0; i < previousLength; i++) mc.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE, 0, 0);
                require(nameField.getValue().isEmpty(), "Native name-field keyboard deletion failed");
                for (char character : "WWPG API client edited".toCharArray())
                    require(mc.screen.charTyped(character, 0), "Native name-field character input was rejected");
                require(nameField.getValue().equals("WWPG API client edited"), "Native design name keyboard input differs");
                WorldWidePowerGrid.LOGGER.info("API_WORKSHOP_VISIBLE_NAME_INPUT_PASSED: x={}; y={}; width={}; height={}; viewportWidth={}; viewportHeight={}; input=screenMouseAndKeyboard; name={}",
                        nameField.getX(), nameField.getY(), nameField.getWidth(), nameField.getHeight(),
                        mc.screen.width, mc.screen.height, nameField.getValue());
            }
            // Let the typed name render before capturing and pressing the visible
            // native Save button. Later copy/synchronization assertions are unchanged.
            if (age == 109) shot(mc, multiplayer ? "api-board-editor-visible-A.png" : "api-board-edit.png");
            if (age == 110) {
                require(mc.screen instanceof CircuitDesignTableEditScreen, "Native editor closed before visible Save");
                var editor = mc.screen;
                var field = CircuitDesignTableEditScreen.class.getDeclaredField("acceptBtn"); field.setAccessible(true);
                var save = (net.minecraft.client.gui.components.AbstractWidget) field.get(editor);
                require(save.active && save.visible && save.getX() >= 0 && save.getY() >= 0
                                && save.getRight() <= editor.width && save.getBottom() <= editor.height,
                        "Native Save button is disabled or outside the visible viewport");
                double x = save.getX() + save.getWidth() / 2.0, y = save.getY() + save.getHeight() / 2.0;
                require(editor.mouseClicked(x, y, 0), "Visible native Save button rejected its mouse click");
                editor.mouseReleased(x, y, 0);
                WorldWidePowerGrid.LOGGER.info("API_WORKSHOP_VISIBLE_SAVE_CLICK_PASSED: x={}; y={}; width={}; height={}; viewportWidth={}; viewportHeight={}; input=screenMouse",
                        save.getX(), save.getY(), save.getWidth(), save.getHeight(), editor.width, editor.height);
            }
            if (age == 155) {
                require(mc.screen instanceof CircuitDesignTableScreen && table.getSchematicName().equals("WWPG API client edited"), "Native editor save did not return the renamed design");
                var board = (CircuitBoardBlockEntity) mc.level.getBlockEntity(ExtensionApiClientSession.BOARD);
                ModdedPackets.sendToServer(new SaveSchematicC2SPacket(board, "WWPG API client edited", table.getSchematic()));
                ModdedPackets.sendToServer(new SaveSchematicC2SPacket(table, false));
            }
            if (age == 195) {
                var saved = org.patryk3211.powergrid.circuits.schematic.CircuitSchematic.fromStack(mc.level.registryAccess(), mc.player.containerMenu.getSlot(2).getItem());
                require(saved.components().size() == 3 && saved.getName().equals("WWPG API client edited"), "Native schematic item did not retain the custom board design");
                mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, 2, 0, net.minecraft.world.inventory.ClickType.PICKUP, mc.player);
                mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, 0, 0, net.minecraft.world.inventory.ClickType.PICKUP, mc.player);
                ModdedPackets.sendToServer(new SaveSchematicC2SPacket(table, true));
            }
            if (age == 235) {
                require(table.getSchematic().components().size() == 3 && table.getSchematicName().equals("WWPG API client edited"), "Actual native item copy/load lost the edited design");
                mc.player.closeContainer();
            }
        }
        if (a ? age == 310 : age >= 310) {
            var board = (CircuitBoardBlockEntity) mc.level.getBlockEntity(ExtensionApiClientSession.BOARD);
            boolean synchronizedDesign = board != null && "WWPG API client edited".equals(board.getSchematic().getName())
                    && board.getComponentsStream().count() == 3;
            if (!a && !synchronizedDesign) {
                require(age < BOARD_OBSERVER_DEADLINE_TICKS, "Other client's native board edit did not synchronize within 800 observer ticks");
                return;
            }
            require(synchronizedDesign, "Other client's native board edit did not synchronize");
            WorldWidePowerGrid.LOGGER.info("API_WORKSHOP_BOARD_SYNC_OBSERVED: player={}; operator={}; actionAge={}; renamed=true; components=3; observerDeadlineTicks={}",
                    mc.player.getGameProfile().getName(), a, age, BOARD_OBSERVER_DEADLINE_TICKS);
            finish(mc);
        }
    }
    private void repair(Minecraft mc, boolean a) {
        if (a && age == 25) wireLook(mc);
        if (a && age == 35) {
            require(mc.hitResult instanceof EntityHitResult && ((EntityHitResult) mc.hitResult).getEntity() instanceof HangingWireEntity, "Native cutter ray missed API wire");
            require(mc.gameMode.interact(mc.player, ((EntityHitResult) mc.hitResult).getEntity(), InteractionHand.MAIN_HAND).consumesAction(), "Native cutter did not accept API wire");
        }
        if (a ? age == 100 : age >= 100) {
            var load = ExtensionApiClientSession.load(true);
            boolean dark = !booleanState(mc, load, "lit");
            if (!a && !dark) {
                require(age < REPAIR_OBSERVER_DEADLINE_TICKS, "Client API load remained lit after cutting its wire for 600 observer ticks");
                return;
            }
            require(dark, "Client API load remained lit after cutting its wire");
            WorldWidePowerGrid.LOGGER.info("API_WORKSHOP_CUT_STATE_OBSERVED: player={}; operator={}; actionAge={}; lit=false; observerDeadlineTicks={}",
                    mc.player.getGameProfile().getName(), a, age, REPAIR_OBSERVER_DEADLINE_TICKS);
            finish(mc);
        }
    }
    private void reconnect(Minecraft mc, boolean a) {
        if (a && age == 25) terminalLook(mc, ExtensionApiClientSession.source(true), 0);
        if (a && age == 35) terminalClick(mc, ExtensionApiClientSession.source(true));
        if (a && age == 50) terminalLook(mc, ExtensionApiClientSession.load(true), 0);
        if (a && age == 60) {
            terminalClick(mc, ExtensionApiClientSession.load(true));
            WorldWidePowerGrid.LOGGER.info("API_WORKSHOP_NATIVE_RECONNECT_TARGET_CLICK_SENT: player={}; actionAge={}; terminal={}:0",
                    mc.player.getGameProfile().getName(), age, ExtensionApiClientSession.load(true));
        }
        if (a ? age == 120 : age >= 120) {
            boolean recovered = booleanState(mc, ExtensionApiClientSession.load(true), "lit");
            // The operator pauses its action clock while moving. The observing client
            // waits for the synchronized output, rather than racing that independent clock.
            if (!a && !recovered) {
                require(age < RECONNECT_OBSERVER_DEADLINE_TICKS,
                        "API load did not synchronize native reconnection recovery within 600 observer ticks");
                return;
            }
            require(recovered, "API load did not recover after native reconnection");
            WorldWidePowerGrid.LOGGER.info("API_WORKSHOP_RECONNECT_RECOVERY_OBSERVED: player={}; operator={}; actionAge={}; lit=true; observerDeadlineTicks={}",
                    mc.player.getGameProfile().getName(), a, age, RECONNECT_OBSERVER_DEADLINE_TICKS);
            finish(mc);
        }
    }
    private void finish(Minecraft mc) {
        if (!phase.equals("view")) {
            WorldWidePowerGrid.LOGGER.info("API_WORKSHOP_PHASE_PASSED: {}", phase);
            if (multiplayer) shot(mc, "api-" + phase + "-" + (mc.player.getGameProfile().getName().endsWith("A") ? "A" : "B") + ".png");
        }
        mc.player.connection.sendCommand("wwpg-api-ack " + phase); phase = "waiting";
    }
    private static void creativeHand(Minecraft mc, ItemStack stack) {
        mc.player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        mc.gameMode.handleCreativeModeItemAdd(stack, 36 + mc.player.getInventory().selected);
    }
    private static void sneakClick(Minecraft mc, BlockPos pos) {
        mc.player.setShiftKeyDown(true);
        mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(mc.player, net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY));
        blockClick(mc, pos);
        mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(mc.player, net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));
        mc.player.setShiftKeyDown(false);
    }
    private void terminalLook(Minecraft mc, BlockPos pos, int terminal) {
        var point = new BlockWireEndpoint(pos, terminal).getExactPosition(mc.level);
        require(point != null, "API client terminal lacks native geometry: " + pos + ":" + terminal);
        var outward = point.subtract(Vec3.atCenterOf(pos)).multiply(1, 0, 1).normalize();
        if (outward.lengthSqr() < .01) outward = new Vec3(0, 0, -1);
        move(mc, Vec3.atBottomCenterOf(pos).add(outward.scale(2)), point);
    }
    private static void terminalClick(Minecraft mc, BlockPos expected) { blockClick(mc, expected); }
    private static void blockClick(Minecraft mc, BlockPos expected) {
        var picked = mc.player.pick(6, 0, false);
        require(picked instanceof BlockHitResult && ((BlockHitResult) picked).getBlockPos().equals(expected), "Actual API block ray missed " + expected + ": " + picked);
        require(mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, (BlockHitResult) picked).consumesAction(), "Native API block interaction rejected " + expected);
    }
    private void wireLook(Minecraft mc) {
        for (var entity : mc.level.entitiesForRendering()) if (entity instanceof HangingWireEntity wire
                && wire.isConnectedTo(ExtensionApiClientSession.source(true), 0) && wire.curveParams != null) {
            var points = new ArrayList<Vec3>(); wire.curveParams.runForPoints(20, (x, y, z) -> points.add(new Vec3(x, y, z).add(wire.position())));
            var point = points.get(10); move(mc, new Vec3(point.x, 64, point.z - 2), point); return;
        }
        throw new IllegalStateException("Client has no loaded PG wire curve at the API source");
    }
    private void face(Minecraft mc, BlockPos pos, Vec3 target) { move(mc, Vec3.atBottomCenterOf(pos).add(0, 0, -2.5), target); }
    private void move(Minecraft mc, Vec3 position, Vec3 target) {
        require(movement.isEmpty() && movementTarget == null, "Overlapping API client movement");
        require(mc.player.getAbilities().mayfly, "API client movement requires the coordinator's native creative flight permission");
        mc.player.getAbilities().flying = true;
        mc.player.onUpdateAbilities();
        var current = mc.player.position();
        // Fly above the station hardware, travel with ordinary bounded packets,
        // then descend into native interaction reach. Action ages pause in flight.
        double altitude = Math.max(67, Math.max(current.y, position.y) + 3);
        movement.add(new Vec3(current.x, altitude, current.z));
        movement.add(new Vec3(position.x, altitude, position.z));
        movement.add(position);
        movementTarget = target;
        movementSettling = 5;
    }
    private boolean advanceMovement(Minecraft mc) {
        if (movementTarget == null) return false;
        if (!movement.isEmpty()) {
            var next = movement.peek();
            var delta = next.subtract(mc.player.position());
            double distance = delta.length();
            var position = distance <= .35 ? movement.remove() : mc.player.position().add(delta.scale(.35 / distance));
            mc.player.setPos(position);
            mc.player.setDeltaMovement(Vec3.ZERO);
            mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos(position.x, position.y, position.z, false));
            look(mc, movementTarget);
            return true;
        }
        look(mc, movementTarget);
        if (movementSettling-- > 0) return true;
        WorldWidePowerGrid.LOGGER.info("API_WORKSHOP_MOVEMENT_SETTLED: position={}; target={}; maxStep=0.35", mc.player.position(), movementTarget);
        movementTarget = null;
        return false;
    }
    private static void look(Minecraft mc, Vec3 target) {
        var delta = target.subtract(mc.player.getEyePosition());
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)));
        mc.player.setYRot(yaw); mc.player.setXRot(pitch);
        mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot(yaw, pitch, false));
    }
    private static boolean booleanState(Minecraft mc, BlockPos position, String name) {
        var state = mc.level.getBlockState(position);
        var property = state.getBlock().getStateDefinition().getProperty(name);
        require(property instanceof BooleanProperty, "Expected declared boolean property '" + name + "' at " + position + ", observed " + state);
        return state.getValue((BooleanProperty) property);
    }
    private static void shot(Minecraft mc, String name) { Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), text -> WorldWidePowerGrid.LOGGER.info("API_WORKSHOP_SCREENSHOT: {}", text.getString())); }
    private static void near(double actual, double expected, double tolerance, String label) { require(Double.isFinite(actual) && Math.abs(actual - expected) <= tolerance, label + ": " + actual + " instead of " + expected); WorldWidePowerGrid.LOGGER.info("API_WORKSHOP_MEASUREMENT: {}={}", label, actual); }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
