package org.cha0scollective.wwpg.clientdelivery;

import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.cha0scollective.wwpg.clientdelivery.mixin.PlayerChunkSenderAccess;
import org.cha0scollective.wwpg.experimental.WireTreeBatch;

/** Read-only diagnostic; does not change chunk tickets, quotas, ACKs or processing. */
@Mod(value = "wwpg_benchmark_delivery", dist = Dist.CLIENT)
public final class ClientDeliveryDiagnostic {
    private static final Path OUT = Path.of("client-benchmark");
    private static final Gson JSON = new Gson();
    private static final ConcurrentLinkedQueue<Batch> BATCHES = new ConcurrentLinkedQueue<>();
    private int clientTicks;
    private ChunkPos viewCenter;
    private final List<ChunkPos> viewChunks = new ArrayList<>();
    private record Batch(long wall_unix_ms, String thread, int chunks, long elapsed_nanos,
                         double aggregated_nanos_per_chunk, int old_samples_weight,
                         float desired_chunks_per_tick) { }

    public ClientDeliveryDiagnostic() {
        if (!Boolean.getBoolean("wwpg.clientBenchmark.inspectDelivery"))
            throw new IllegalStateException("Delivery inspector requires its explicit diagnostic runner");
        try { Files.createDirectories(OUT); }
        catch (Exception error) { throw new IllegalStateException(error); }
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, this::serverTick);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, ignored -> {
            if (++clientTicks % 20 == 0 || Minecraft.getInstance().level == null) flushBatches();
        });
    }

    public static void batch(int chunks, long elapsed, double aggregate, int weight) {
        BATCHES.add(new Batch(System.currentTimeMillis(), Thread.currentThread().getName(), chunks,
                elapsed, aggregate, weight, (float) (7_000_000.0 / aggregate)));
    }

    private void serverTick(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (server.getTickCount() % 20 != 0) return;
        for (var player : server.getPlayerList().getPlayers()) {
            long started = System.nanoTime();
            var sender = (PlayerChunkSenderAccess) player.connection.chunkSender;
            var chunkSource = player.serverLevel().getChunkSource();
            var chunkMap = chunkSource.chunkMap;
            int missing = 0, notTicking = 0, waitingSend = 0, sendable = 0;
            for (long key : sender.delivery$pending()) {
                var holder = chunkMap.getVisibleChunkIfPresent(key);
                if (holder == null) { missing++; continue; }
                boolean ticking = holder.getTickingChunk() != null;
                boolean synced = holder.getSendSyncFuture().isDone();
                if (!ticking) notTicking++;
                if (!synced) waitingSend++;
                if (ticking && synced) sendable++;
            }
            if (!player.chunkPosition().equals(viewCenter)) {
                viewCenter = player.chunkPosition();
                viewChunks.clear();
                ChunkTrackingView.of(viewCenter, 12).forEach(viewChunks::add);
            }
            int viewMissing = 0, viewFull = 0, viewTicking = 0, viewWaitingSend = 0, viewSendable = 0;
            for (var pos : viewChunks) {
                if (chunkSource.getChunkNow(pos.x, pos.z) != null) viewFull++;
                var holder = chunkMap.getVisibleChunkIfPresent(pos.toLong());
                if (holder == null) { viewMissing++; continue; }
                boolean ticking = holder.getTickingChunk() != null;
                boolean synced = holder.getSendSyncFuture().isDone();
                if (ticking) viewTicking++;
                if (!synced) viewWaitingSend++;
                if (ticking && synced) viewSendable++;
            }
            var row = new LinkedHashMap<String, Object>();
            row.put("wall_unix_ms", System.currentTimeMillis());
            row.put("server_tick", server.getTickCount());
            row.put("memory_connection", sender.delivery$memory());
            row.put("pending_chunks", sender.delivery$pending().size());
            row.put("missing_holders", missing);
            row.put("not_ticking", notTicking);
            row.put("waiting_send_dependencies", waitingSend);
            row.put("sendable", sendable);
            row.put("desired_chunks_per_tick", sender.delivery$desired());
            row.put("batch_quota", sender.delivery$quota());
            row.put("unacknowledged_batches", sender.delivery$unacknowledged());
            row.put("maximum_unacknowledged_batches", sender.delivery$maximumUnacknowledged());
            row.put("pending_wire_roots", WireTreeBatch.pending(player.serverLevel()));
            row.put("view_center", List.of(viewCenter.x, viewCenter.z));
            row.put("view_chunks", viewChunks.size());
            row.put("view_missing_holders", viewMissing);
            row.put("view_full_chunks", viewFull);
            row.put("view_ticking_chunks", viewTicking);
            row.put("view_waiting_send_dependencies", viewWaitingSend);
            row.put("view_sendable", viewSendable);
            row.put("probe_nanos", System.nanoTime() - started);
            append("delivery-server.jsonl", JSON.toJson(row) + "\n");
        }
    }

    private static void flushBatches() {
        var text = new StringBuilder();
        Batch next;
        while ((next = BATCHES.poll()) != null) text.append(JSON.toJson(next)).append('\n');
        if (!text.isEmpty()) append("delivery-batches.jsonl", text.toString());
    }

    private static void append(String file, String text) {
        try { Files.writeString(OUT.resolve(file), text, StandardOpenOption.CREATE, StandardOpenOption.APPEND); }
        catch (Exception error) { throw new IllegalStateException("Cannot write delivery diagnostic", error); }
    }
}
