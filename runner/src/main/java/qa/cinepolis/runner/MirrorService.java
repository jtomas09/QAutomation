package qa.cinepolis.runner;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MirrorService — tracks active MJPEG mirror sessions per device.
 *
 * Provides DeviceMirrorState consumed by the frontend via
 * GET /api/device/status?udid={udid}.
 *
 * Future: when scrcpy support is added, this class manages scrcpy
 * process lifecycle and H264 session state.
 */
public final class MirrorService {

    public record DeviceMirrorState(
        boolean connected,
        String  deviceId,
        boolean isStreaming,
        String  resolution,
        int     fps
    ) {}

    private static final ConcurrentHashMap<String, AtomicInteger> activeStreams =
            new ConcurrentHashMap<>();

    private MirrorService() {}

    public static void registerStream(String udid) {
        activeStreams.computeIfAbsent(udid, k -> new AtomicInteger(0)).incrementAndGet();
    }

    public static void deregisterStream(String udid) {
        AtomicInteger count = activeStreams.get(udid);
        if (count != null && count.decrementAndGet() <= 0) {
            activeStreams.remove(udid);
        }
    }

    public static boolean isStreaming(String udid) {
        AtomicInteger count = activeStreams.get(udid);
        return count != null && count.get() > 0;
    }

    public static DeviceMirrorState getState(String udid, boolean connected) {
        boolean streaming = isStreaming(udid);
        return new DeviceMirrorState(connected, udid, streaming, "auto", streaming ? 20 : 0);
    }

    /** Returns true if the scrcpy binary is accessible (future H264 mode). */
    public static boolean isScrcpyAvailable() {
        try {
            Process p = new ProcessBuilder("scrcpy", "--version")
                    .redirectErrorStream(true).start();
            boolean finished = p.waitFor(3, TimeUnit.SECONDS);
            return finished && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
