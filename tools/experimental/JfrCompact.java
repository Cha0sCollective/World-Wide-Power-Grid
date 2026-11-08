import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordingFile;

/** Stream selected JFR fields without recursively expanding class-loader metadata. */
public final class JfrCompact {
    private static final Set<String> EVENTS = Set.of("jdk.ExecutionSample", "jdk.NativeMethodSample",
            "jdk.ObjectAllocationSample", "jdk.GarbageCollection", "jdk.GCPhasePause", "jdk.ThreadPark",
            "jdk.JavaMonitorEnter", "jdk.Compilation", "jdk.Deoptimization");

    private static String quote(String value) {
        if (value == null) return "null";
        var result = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            if (c == '"' || c == '\\') result.append('\\').append(c);
            else if (c < 32) result.append(String.format("\\u%04x", (int)c));
            else result.append(c);
        }
        return result.append('"').toString();
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("recording.jfr output.json");
        try (var recording = new RecordingFile(Path.of(args[0]));
             BufferedWriter output = Files.newBufferedWriter(Path.of(args[1]), StandardOpenOption.CREATE_NEW)) {
            output.write("{\"schema\":1,\"events\":[\n");
            boolean first = true;
            while (recording.hasMoreEvents()) {
                RecordedEvent event = recording.readEvent();
                String type = event.getEventType().getName();
                if (!EVENTS.contains(type)) continue;
                if (!first) output.write(",\n");
                first = false;
                RecordedThread thread = event.hasField("sampledThread") ? event.getThread("sampledThread") : event.getThread();
                var stack = event.getStackTrace();
                output.write("{\"type\":" + quote(type) + ",\"thread\":" + quote(thread == null ? null : thread.getJavaName())
                        + ",\"start\":" + quote(event.getStartTime().toString()) + ",\"duration_ns\":" + event.getDuration().toNanos()
                        + ",\"truncated\":" + (stack != null && stack.isTruncated()) + ",\"frames\":[");
                if (stack != null) {
                    boolean firstFrame = true;
                    for (var frame : stack.getFrames()) {
                        if (!firstFrame) output.write(',');
                        firstFrame = false;
                        var method = frame.getMethod();
                        output.write(quote(method.getType().getName() + "." + method.getName()));
                    }
                }
                output.write(']');
                if (type.equals("jdk.ObjectAllocationSample")) {
                    RecordedClass klass = event.getValue("objectClass");
                    output.write(",\"object_class\":" + quote(klass.getName()) + ",\"weight\":" + event.getLong("weight"));
                }
                output.write('}');
            }
            output.write("\n]}\n");
        }
    }
}
