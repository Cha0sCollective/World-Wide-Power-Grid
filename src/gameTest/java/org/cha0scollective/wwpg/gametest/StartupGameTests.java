package org.cha0scollective.wwpg.gametest;

import com.google.common.collect.ForwardingTable;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;
import com.tterrag.registrate.AbstractRegistrate;
import com.tterrag.registrate.util.entry.RegistryEntry;
import com.tterrag.registrate.util.nullness.NonNullConsumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Exercise the pinned Registrate implementation on an isolated, unpublished instance. */
@GameTestHolder("wwpg")
@PrefixGameTestTemplate(false)
public final class StartupGameTests {
    private static final class TestRegistrate extends AbstractRegistrate<TestRegistrate> {
        TestRegistrate() { super("wwpg_startup_check"); }
        void publish(String name) {
            accept(name, Registries.ITEM, null, () -> Items.AIR, holder -> new RegistryEntry<>(this, holder));
        }
    }

    @SuppressWarnings("unchecked")
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void registrationKeepsConcurrentCallbacks(GameTestHelper h) throws Exception {
        var registrate = new TestRegistrate();
        var registrationsField = AbstractRegistrate.class.getDeclaredField("registrations");
        registrationsField.setAccessible(true);
        var entries = (Table<ResourceKey<?>, String, Object>) registrationsField.get(registrate);
        var publishing = new CountDownLatch(1);
        var commit = new CountDownLatch(1);
        // Pause after pending callbacks have been drained but before the entry
        // becomes visible. A simultaneous callback must wait for publication.
        registrationsField.set(registrate, new ForwardingTable<ResourceKey<?>, String, Object>() {
            @Override protected Table<ResourceKey<?>, String, Object> delegate() { return entries; }
            @Override public Object put(ResourceKey<?> key, String name, Object value) {
                if (name.equals("gate")) {
                    publishing.countDown();
                    try {
                        if (!commit.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Registration gate timed out");
                    } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                }
                return super.put(key, name, value);
            }
        });
        NonNullConsumer<Item> callback = item -> {};
        var failures = new AtomicReference<Throwable>();
        var callbackStarted = new CountDownLatch(1);
        var publisher = Thread.ofPlatform().daemon().name("wwpg-registration-fixture").unstarted(() -> {
            try { registrate.publish("gate"); } catch (Throwable e) { failures.compareAndSet(null, e); }
        });
        var subscriber = Thread.ofPlatform().daemon().name("wwpg-callback-fixture").unstarted(() -> {
            callbackStarted.countDown();
            try { registrate.addRegisterCallback("gate", Registries.ITEM, callback); }
            catch (Throwable e) { failures.compareAndSet(null, e); }
        });
        publisher.start();
        try {
            h.assertTrue(publishing.await(5, TimeUnit.SECONDS), "Registration reached publication gate");
            subscriber.start();
            h.assertTrue(callbackStarted.await(5, TimeUnit.SECONDS), "Concurrent callback started");
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (subscriber.isAlive() && System.nanoTime() < deadline) {
                if (subscriber.getState() == Thread.State.BLOCKED) break;
                java.util.concurrent.locks.LockSupport.parkNanos(1_000_000L);
            }
        } finally {
            commit.countDown();
            publisher.join(5000); subscriber.join(5000);
        }
        h.assertTrue(!publisher.isAlive() && !subscriber.isAlive(), "Registration workers finished");
        h.assertTrue(failures.get() == null, "Registration worker failure: " + failures.get());
        var pendingField = AbstractRegistrate.class.getDeclaredField("registerCallbacks");
        pendingField.setAccessible(true);
        var pending = (Multimap<?, ?>) pendingField.get(registrate);
        h.assertTrue(pending.isEmpty() && pending.size() == 0, "No stranded or negative callback count");
        var registration = entries.get(Registries.ITEM, "gate");
        var callbacksField = registration.getClass().getDeclaredField("callbacks");
        callbacksField.setAccessible(true);
        h.assertTrue(((List<?>) callbacksField.get(registration)).contains(callback), "The actual callback was retained");
        h.succeed();
    }
}
