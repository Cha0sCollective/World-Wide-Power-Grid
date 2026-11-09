import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Set;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordingFile;

/** Preserve exact recorded frame locations without interpreting them as exclusive costs. */
public final class JfrSites {
    private static final Set<String> EVENTS = Set.of("jdk.ExecutionSample", "jdk.NativeMethodSample");

    private static String quote(String value) {
        if (value == null) return "null";
        var result = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            if (c == '"' || c == '\\') result.append('\\').append(c);
            else if (c < 32) result.append(String.format("\\u%04x", (int) c));
            else result.append(c);
        }
        return result.append('"').toString();
    }

    private static long nanos(Instant time) {
        return Math.addExact(Math.multiplyExact(time.getEpochSecond(), 1_000_000_000L), time.getNano());
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("recording.jfr output.json start_ns end_ns");
        long start = Long.parseLong(args[2]), end = Long.parseLong(args[3]);
        if (start >= end) throw new IllegalArgumentException("Empty or reversed interval");
        try (var recording = new RecordingFile(Path.of(args[0]));
             BufferedWriter output = Files.newBufferedWriter(Path.of(args[1]), StandardOpenOption.CREATE_NEW)) {
            output.write("{\"schema\":1,\"start_ns\":" + start + ",\"end_ns\":" + end + ",\"events\":[\n");
            boolean first = true;
            while (recording.hasMoreEvents()) {
                var event = recording.readEvent();
                String type = event.getEventType().getName();
                if (!EVENTS.contains(type) || nanos(event.getStartTime()) < start
                        || nanos(event.getStartTime()) >= end || nanos(event.getEndTime()) > end) continue;
                RecordedThread thread = event.getThread("sampledThread");
                if (thread == null || !thread.getJavaName().equals("Server thread")) continue;
                if (!first) output.write(",\n");
                first = false;
                var stack = event.getStackTrace();
                output.write("{\"type\":" + quote(type) + ",\"start\":" + quote(event.getStartTime().toString())
                        + ",\"truncated\":" + (stack != null && stack.isTruncated()) + ",\"frames\":[");
                if (stack != null) {
                    boolean firstFrame = true;
                    for (var frame : stack.getFrames()) {
                        if (!firstFrame) output.write(',');
                        firstFrame = false;
                        var method = frame.getMethod();
                        output.write("{\"method\":" + quote(method.getType().getName() + "." + method.getName())
                                + ",\"descriptor\":" + quote(method.getDescriptor()) + ",\"line\":" + frame.getLineNumber()
                                + ",\"bci\":" + frame.getBytecodeIndex() + ",\"frame_type\":" + quote(frame.getType()) + '}');
                    }
                }
                output.write("]}");
            }
            output.write("\n]}\n");
        }
    }
}
