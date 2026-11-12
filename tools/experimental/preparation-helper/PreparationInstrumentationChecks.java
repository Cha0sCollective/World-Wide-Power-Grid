package org.cha0scollective.wwpg.benchmark;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

public final class PreparationInstrumentationChecks {
    private static ClassNode read(byte[] bytes) {
        var node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }
    private static void rejected(Runnable operation) {
        try { operation.run(); }
        catch (IllegalStateException expected) { return; }
        throw new AssertionError("Expected a rejected instrumentation target");
    }
    public static void main(String[] args) throws Exception {
        var proofs = new ArrayList<PreparationInstrumenter.Proof>();
        try (var jar = new ZipFile(args[0])) {
            for (boolean stable : new boolean[]{false, true}) {
                String name = stable ? PreparationInstrumenter.STABLE : PreparationInstrumenter.BRIDGE;
                byte[] original = jar.getInputStream(jar.getEntry(name + ".class")).readAllBytes();
                var node = read(original);
                proofs.add(PreparationInstrumenter.instrument(node, stable));
                // Verify operand-stack consistency after serialization as it will be loaded.
                var serialized = read(PreparationInstrumenter.bytes(node));
                var method = serialized.methods.stream().filter(m -> m.name.equals(stable ? "update" : "prepare")).findFirst().orElseThrow();
                new Analyzer<>(new BasicVerifier()).analyze(name, method);
                rejected(() -> PreparationInstrumenter.instrument(node, stable));
                if (stable) {
                    var drifted = read(original);
                    for (var m : drifted.methods) if (m.name.equals("update"))
                        for (var instruction : m.instructions)
                            if (instruction instanceof FieldInsnNode field && field.name.equals("bindings")) field.name = "changedBindings";
                    rejected(() -> PreparationInstrumenter.instrument(drifted, true));
                }
            }
        }
        String json = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(java.util.Map.of(
                "passed", true, "transformed_methods", 2, "stack_verifications", 2,
                "duplicate_rejections", 2, "drift_rejections", 1, "proofs", proofs));
        Files.writeString(Path.of(args[1]), json + "\n", StandardOpenOption.CREATE_NEW);
        System.out.println(json);
    }
}
