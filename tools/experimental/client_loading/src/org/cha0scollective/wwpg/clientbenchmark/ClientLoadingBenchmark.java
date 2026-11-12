package org.cha0scollective.wwpg.clientbenchmark;

import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.cha0scollective.wwpg.benchmark.Session;
import org.cha0scollective.wwpg.clientbenchmark.mixin.OcclusionAccess;
import org.cha0scollective.wwpg.clientbenchmark.mixin.RendererAccess;

/** One copied world and one stationary view. This does not run graphical acceptance. */
@Mod(value = "wwpg_benchmark_client", dist = Dist.CLIENT)
public final class ClientLoadingBenchmark {
    private static final Path OUT = Path.of("client-benchmark");
    private static final com.google.gson.Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final double X = 1038.5, Y = 74, Z = 1028.5;
    private static final float YAW = -36, PITCH = 14;
    private final List<Map<String, Object>> observations = new ArrayList<>();
    private final List<Long> frameTimes = new ArrayList<>();
    private final Map<String, Object> report = new LinkedHashMap<>();
    private final List<ChunkPos> expectedChunks = new ArrayList<>();
    private volatile boolean cameraSent, electricalReady;
    private volatile String serverFailure;
    private volatile long electricalReadyNs;
    private long selectedNs, firstFrameNs, previousFrameNs, lastObservationNs, readySinceNs;
    private long screenshotRequestedNs, lastProgressNs;
    private int initialBuffers, titleTicks, quitTicks, stableFrames;
    private long visibleSignature;
    private boolean selected, capturing, quitting, observedServerCompletion;
    private volatile boolean screenshotWritten;

    public ClientLoadingBenchmark() {
        if (!Boolean.getBoolean("wwpg.clientBenchmark.enabled"))
            throw new IllegalStateException("Client helper requires its explicit runner");
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, ignored -> clientTick());
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, this::playerJoined);
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, ignored -> serverTick());
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.class, this::rendered);
        NeoForge.EVENT_BUS.addListener(RenderFrameEvent.Post.class, ignored -> completedFrame());
    }

    private void playerJoined(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) return;
        player.setGameMode(GameType.CREATIVE);
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.teleportTo(X, Y, Z);
        player.connection.teleport(X, Y, Z, YAW, PITCH);
        cameraSent = true;
    }

    private void serverTick() {
        if (observedServerCompletion) return;
        Boolean completion = Session.loadCompletion();
        if (completion == null) return;
        observedServerCompletion = true;
        if (!completion) serverFailure = "Electrical restoration correctness gate failed";
        else { electricalReadyNs = System.nanoTime(); electricalReady = true; }
    }

    private void clientTick() {
        var mc = Minecraft.getInstance();
        if (quitting) { quit(mc); return; }
        if (serverFailure != null) { fail(serverFailure); return; }
        if (selected && System.nanoTime() - lastProgressNs > 5_000_000_000L) {
            lastProgressNs = System.nanoTime();
            var progress = new LinkedHashMap<String, Object>();
            progress.put("elapsed_seconds", (lastProgressNs - selectedNs) / 1e9);
            progress.put("screen", mc.screen == null ? "world" : mc.screen.getClass().getName());
            progress.put("electrical_ready", electricalReady);
            if (!observations.isEmpty()) progress.put("render", observations.getLast());
            write("status.json", progress);
            System.out.println("WWPG_CLIENT_LOADING " + progress);
        }
        if (mc.screen instanceof AccessibilityOnboardingScreen) {
            click(mc, Component.translatable("gui.continue").getString());
            return;
        }
        if (!selected && mc.screen instanceof TitleScreen && mc.getOverlay() == null && ++titleTicks >= 40) {
            try {
                Files.createDirectories(OUT);
                if (Files.exists(OUT.resolve("result.json"))) throw new IllegalStateException("Use a fresh attempt");
                recordLoadedArtifacts();
                if (mc.getWindow().getWidth() != 1920 || mc.getWindow().getHeight() != 1080
                        || mc.options.renderDistance().get() != 12)
                    throw new IllegalStateException("Requested 1920x1080 / 12 chunks not active");
                mc.options.pauseOnLostFocus = false;
                initialBuffers = mc.renderBuffers().sectionBufferPool().getFreeBufferCount();
                ChunkTrackingView.of(new ChunkPos(BlockPos.containing(X, Y, Z)), 12).forEach(expectedChunks::add);
                report.put("schema", 1);
                report.put("scope", "One copied benchmark save; stationary visible scene; not full graphical acceptance");
                report.put("start_event", "Normal WorldOpenFlows.openWorld invocation from the title screen");
                report.put("selected_unix_ms", System.currentTimeMillis());
                report.put("resolution", List.of(1920, 1080));
                report.put("render_distance_chunks", 12);
                report.put("simulation_distance_chunks", mc.options.simulationDistance().get());
                report.put("camera", Map.of("x", X, "y", Y, "z", Z, "yaw", YAW, "pitch", PITCH));
                report.put("expected_client_chunks", expectedChunks.size());
                report.put("compile_buffers", initialBuffers);
                report.put("settings", Map.of("graphics", mc.options.graphicsMode().get().toString(),
                        "vsync", mc.options.enableVsync().get(), "max_fps", mc.options.framerateLimit().get(),
                        "fov", mc.options.fov().get(), "fullscreen", mc.getWindow().isFullscreen()));
                write("start.json", report);
                selected = true;
                selectedNs = System.nanoTime();
                mc.createWorldOpenFlows().openWorld(System.getProperty("wwpg.clientBenchmark.save"),
                        () -> fail("World opening returned without loading"));
            } catch (Exception failure) { fail(failure.toString()); }
        }
        if (selected && !capturing && System.nanoTime() - selectedNs > 360_000_000_000L)
            fail("World selection to rendered readiness exceeded six-minute diagnostic limit");
        if (capturing && System.nanoTime() - screenshotRequestedNs > 30_000_000_000L && !screenshotWritten)
            fail("Screenshot write did not complete");
        if (capturing && screenshotWritten) {
            try {
                var path = OUT.resolve("screenshots/rendered.png");
                if (!Files.isRegularFile(path) || Files.size(path) == 0) throw new IllegalStateException("Screenshot missing");
                report.put("screenshot_sha256", sha(path));
                report.put("passed", true);
                write("result.json", report);
                write("render-observations.json", observations);
                write("frame-times-ns.json", frameTimes);
                quitting = true;
            } catch (Exception failure) { fail(failure.toString()); }
        }
    }

    private void rendered(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || !selected || capturing || quitting) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.screen != null) return;
        long now = System.nanoTime();
        if (firstFrameNs == 0) firstFrameNs = now;
        if (previousFrameNs != 0) frameTimes.add(now - previousFrameNs);
        previousFrameNs = now;
        if (now - lastObservationNs < 100_000_000L) return;
        lastObservationNs = now;
        boolean camera = cameraSent && mc.player.position().distanceToSqr(X, Y, Z) < .01
                && Math.abs(mc.player.getYRot() - YAW) < .1 && Math.abs(mc.player.getXRot() - PITCH) < .1;
        int loaded = 0;
        for (var pos : expectedChunks) if (mc.level.getChunkSource().hasChunk(pos.x, pos.z)) loaded++;
        var access = (RendererAccess) mc.levelRenderer;
        int dirty = 0, uncompiled = 0;
        long signature = 1;
        for (var section : access.benchmark$visible()) {
            if (section.isDirty()) dirty++;
            if (section.getCompiled() == SectionRenderDispatcher.CompiledSection.UNCOMPILED) uncompiled++;
            signature += section.getOrigin().asLong();
        }
        var graph = (OcclusionAccess) access.benchmark$occlusion();
        var update = graph.benchmark$fullUpdateTask();
        boolean occlusionReady = !graph.benchmark$needsFullUpdate() && (update == null || update.isDone())
                && !graph.benchmark$needsFrustumUpdate().get();
        int freeBuffers = mc.renderBuffers().sectionBufferPool().getFreeBufferCount();
        boolean queueEmpty = mc.levelRenderer.hasRenderedAllSections();
        int rendered = mc.levelRenderer.countRenderedSections();
        boolean ready = electricalReady && camera && loaded == expectedChunks.size() && rendered > 0
                && dirty == 0 && uncompiled == 0 && queueEmpty && freeBuffers == initialBuffers && occlusionReady;
        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("elapsed_seconds", (now - selectedNs) / 1e9);
        sample.put("electrical_ready", electricalReady);
        sample.put("camera_ready", camera);
        sample.put("loaded_client_chunks", loaded);
        sample.put("visible_sections", access.benchmark$visible().size());
        sample.put("rendered_sections", rendered);
        sample.put("dirty_sections", dirty);
        sample.put("uncompiled_sections", uncompiled);
        sample.put("queue_empty", queueEmpty);
        sample.put("free_compile_buffers", freeBuffers);
        sample.put("occlusion_ready", occlusionReady);
        sample.put("window_active", mc.isWindowActive());
        observations.add(sample);
        if (!ready || readySinceNs == 0 || signature != visibleSignature) { readySinceNs = ready ? now : 0; stableFrames = 0; }
        else stableFrames++;
        visibleSignature = signature;
        // Two seconds with the full requested view, no compile/upload work, and accepted electricity.
        if (readySinceNs != 0 && now - readySinceNs >= 2_000_000_000L && stableFrames >= 15) {
            report.put("first_world_frame_seconds", (firstFrameNs - selectedNs) / 1e9);
            report.put("electrical_ready_seconds", (electricalReadyNs - selectedNs) / 1e9);
            report.put("restored_rendered_seconds", (now - selectedNs) / 1e9);
            report.put("under_one_minute", now - selectedNs < 60_000_000_000L);
            report.put("readiness_observation", sample);
            report.put("ready_confirmation_seconds", (now - readySinceNs) / 1e9);
            capturing = true;
        }
    }

    private void completedFrame() {
        if (!capturing || screenshotRequestedNs != 0 || quitting) return;
        screenshotRequestedNs = System.nanoTime();
        Screenshot.grab(OUT.toFile(), "rendered.png", Minecraft.getInstance().getMainRenderTarget(), message -> {
            if (Files.isRegularFile(OUT.resolve("screenshots/rendered.png"))) screenshotWritten = true;
            else serverFailure = "Screenshot capture failed: " + message.getString();
        });
    }

    private void quit(Minecraft mc) {
        if (++quitTicks > 1200) throw new IllegalStateException("Save and Quit did not complete");
        if (quitTicks == 1 && mc.level != null) {
            mc.setScreen(new PauseScreen(true));
            if (!click(mc, Component.translatable("menu.returnToMenu").getString()))
                throw new IllegalStateException("Save and Quit button unavailable");
        }
        if (mc.level == null && mc.getSingleplayerServer() == null && mc.screen instanceof TitleScreen) {
            write("shutdown.json", Map.of("save_and_quit_reached_title", true));
            mc.stop();
        }
    }

    private static boolean click(Minecraft mc, String text) {
        if (mc.screen == null) return false;
        for (var widget : mc.screen.children()) if (widget instanceof Button button && button.active
                && button.getMessage().getString().equals(text)) { button.onPress(); return true; }
        return false;
    }

    private void fail(String reason) {
        if (quitting) return;
        report.put("passed", false); report.put("failure", reason);
        write("result.json", report); write("render-observations.json", observations);
        write("frame-times-ns.json", frameTimes);
        quitting = true;
    }

    private static void recordLoadedArtifacts() throws Exception {
        var artifacts = new ArrayList<Map<String, Object>>();
        for (String id : List.of("wwpg", "powergrid", "electroenergetics", "create", "neoforge", "wwpg_benchmark")) {
            var mod = ModList.get().getModContainerById(id).orElseThrow();
            var path = ModList.get().getModFileById(id).getFile().getFilePath();
            artifacts.add(Map.of("id", id, "version", mod.getModInfo().getVersion().toString(),
                    "path", path.toString(), "sha256", sha(path)));
        }
        write("loaded-artifacts.json", artifacts);
    }

    private static String sha(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private static void write(String name, Object value) {
        try { Files.createDirectories(OUT); Files.writeString(OUT.resolve(name), JSON.toJson(value) + "\n"); }
        catch (Exception failure) { throw new IllegalStateException("Cannot preserve client measurement", failure); }
    }
}
