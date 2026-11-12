package org.cha0scollective.wwpg.benchmark;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Diagnostic insertion only: no original instruction, jump or exception handler is replaced. */
public final class PreparationInstrumenter implements Opcodes {
    static final String BRIDGE = "org/cha0scollective/wwpg/bridge/WorldBridge";
    static final String STABLE = BRIDGE + "$StableDescription";
    static final String PROBE = "org/cha0scollective/wwpg/benchmark/PreparationSections";
    private record Addition(AbstractInsnNode anchor, List<AbstractInsnNode> nodes) { }
    public record Proof(String target, String method, String beforeSha256, String afterSha256,
                        int sites, boolean originalInstructionsPreserved) { }

    static byte[] bytes(ClassNode node) {
        var writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }
    static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
    private static AbstractInsnNode executable(AbstractInsnNode node) {
        while (node != null && node.getOpcode() < 0) node = node.getNext();
        return node;
    }
    private static List<AbstractInsnNode> field(MethodNode method, String name) {
        var result = new ArrayList<AbstractInsnNode>();
        for (var node : method.instructions)
            if (node instanceof FieldInsnNode f && f.getOpcode() == GETFIELD && f.name.equals(name)) result.add(node);
        return result;
    }
    private static void stage(List<Addition> additions, AbstractInsnNode anchor, int stage) {
        additions.add(new Addition(anchor, List.of(new IntInsnNode(BIPUSH, stage),
                new MethodInsnNode(INVOKESTATIC, PROBE, "stage", "(I)V", false))));
    }
    private static void attach(MethodNode method, List<Addition> additions) {
        for (var addition : additions) {
            var list = new InsnList();
            for (var node : addition.nodes) list.add(node);
            method.instructions.insertBefore(addition.anchor, list);
        }
    }

    public static Proof instrument(ClassNode node, boolean stable) {
        String target = stable ? STABLE : BRIDGE, name = stable ? "update" : "prepare";
        String descriptor = stable ? "(Lorg/patryk3211/powergrid/electricity/WorldNetworks;)Z"
                : "(Lcom/george_vi/electroenergetics/simulation/simulator/SimulationTicker;)V";
        require(node.name.equals(target), "Unexpected target " + node.name);
        var methods = node.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(descriptor)).toList();
        require(methods.size() == 1, "Missing or ambiguous method " + name);
        var method = methods.getFirst();
        for (var instruction : method.instructions)
            require(!(instruction instanceof MethodInsnNode call && call.owner.equals(PROBE)), "Already instrumented");
        byte[] before = bytes(node);
        int oldMax = method.maxStack;
        var additions = new ArrayList<Addition>();
        var entry = new ArrayList<AbstractInsnNode>();
        entry.add(new VarInsnNode(ALOAD, 0));
        if (stable) entry.add(new FieldInsnNode(GETFIELD, STABLE, "this$0", "L" + BRIDGE + ";"));
        entry.add(new MethodInsnNode(INVOKESTATIC, PROBE, stable ? "stableBegin" : "prepareBegin", "(Ljava/lang/Object;)V", false));
        additions.add(new Addition(executable(method.instructions.getFirst()), entry));
        if (stable) {
            var anchors = field(method, "anchors");
            var bindings = field(method, "bindings");
            require(anchors.size() == 1 && bindings.size() == 2, "Preparation field anchors drifted");
            stage(additions, anchors.getFirst(), 1);
            AbstractInsnNode next = anchors.getFirst().getNext();
            while (next != null && !(next instanceof MethodInsnNode call && call.owner.equals("java/util/Iterator") && call.name.equals("hasNext")))
                next = next.getNext();
            require(next != null, "Owner-loop condition missing");
            var jump = executable(next.getNext());
            require(jump instanceof JumpInsnNode && jump.getOpcode() == IFEQ, "Owner-loop exit drifted");
            var descriptorStart = executable(((JumpInsnNode) jump).label);
            require(descriptorStart != null && descriptorStart.getOpcode() == ICONST_0
                    && executable(descriptorStart.getNext()) instanceof VarInsnNode variable && variable.getOpcode() == ISTORE,
                    "Descriptor-loop initializer drifted");
            stage(additions, descriptorStart, 2);
            stage(additions, bindings.getFirst(), 3);
            var clears = new ArrayList<AbstractInsnNode>();
            var indexStarts = new ArrayList<AbstractInsnNode>();
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && call.name.equals("clear") && call.desc.equals("()V")) clears.add(instruction);
                if (instruction instanceof TypeInsnNode type && type.getOpcode() == NEW && type.desc.equals("java/util/IdentityHashMap")) indexStarts.add(instruction);
            }
            require(clears.size() == 1 && indexStarts.size() == 1, "Rebinding/index anchors drifted");
            stage(additions, clears.getFirst(), 4);
            stage(additions, bindings.getLast(), 5);
            stage(additions, indexStarts.getFirst(), 6);
        }
        int returns = 0;
        for (var instruction : method.instructions) {
            if (instruction.getOpcode() != (stable ? IRETURN : RETURN)) continue;
            returns++;
            additions.add(new Addition(instruction, List.of(stable ? new InsnNode(DUP) : new VarInsnNode(ALOAD, 0),
                    new MethodInsnNode(INVOKESTATIC, PROBE, stable ? "stableEnd" : "prepareEnd",
                            stable ? "(Z)V" : "(Ljava/lang/Object;)V", false))));
        }
        require(returns > 0, "No return sites");
        attach(method, additions);
        method.maxStack = oldMax + 2;
        byte[] after = bytes(node);
        // Check the actual class being transformed, including any preceding mixins.
        for (var addition : additions) for (var instruction : addition.nodes) method.instructions.remove(instruction);
        method.maxStack = oldMax;
        require(Arrays.equals(before, bytes(node)), "Instrumentation changed original instructions or metadata");
        attach(method, additions);
        method.maxStack = oldMax + 2;
        require(Arrays.equals(after, bytes(node)), "Instrumentation reinsertion differs");
        return new Proof(target, name, hash(before), hash(after), additions.size(), true);
    }

    public static void install(ClassNode node, boolean stable) {
        var proof = instrument(node, stable);
        try {
            Path directory = Path.of("benchmark");
            Files.createDirectories(directory);
            Files.writeString(directory.resolve("preparation-install-" + (stable ? "stable" : "prepare") + ".json"),
                    new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(proof) + "\n", StandardOpenOption.CREATE_NEW);
        } catch (Exception error) { throw new IllegalStateException("Cannot preserve preparation instrumentation proof", error); }
    }
}
