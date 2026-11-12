package com.flashlock.ipc;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Persistent, bidirectional connection to the C worker engine (Unix domain socket,
 * newline-delimited JSON).
 *
 * Purchase flow: SUBMIT (queued by the engine's scheduler) -> EXECUTE (engine picked this
 * request) -> backend runs the database strategy -> DONE (frees the engine worker).
 */
@Component
public class EngineIpcClient {

    public static final Set<String> POLICIES = Set.of("FIFO", "PRIORITY", "PRIORITY_AGING");
    private static final String DEFAULT_POLICY = "PRIORITY_AGING";
    private static final int DEFAULT_AGING_MS = 5000;

    /** What the engine reports when a queued request reaches a worker. */
    public static final class Execution {
        private final boolean rejected;
        private final long waitMs;
        private final int workerId;
        private final String policy;

        Execution(boolean rejected, long waitMs, int workerId, String policy) {
            this.rejected = rejected;
            this.waitMs = waitMs;
            this.workerId = workerId;
            this.policy = policy;
        }

        /** True when the engine queue was full and the request was not accepted. */
        public boolean isRejected() { return rejected; }
        public long getWaitMs() { return waitMs; }
        public int getWorkerId() { return workerId; }
        public String getPolicy() { return policy; }
    }

    @Value("${flashlock.engine.socket-path:/tmp/flashlock.sock}")
    private String socketPath;

    private final Object connLock = new Object();
    private final Object writeLock = new Object();
    private final Map<Long, CompletableFuture<Execution>> pending = new ConcurrentHashMap<>();

    private SocketChannel channel;
    private volatile String policy = null;          // null until configured; engine default applies
    private volatile int agingIntervalMs = DEFAULT_AGING_MS;

    // ------------------------------------------------------------------
    // Scheduling-policy configuration
    // ------------------------------------------------------------------
    public String getPolicy() {
        return policy != null ? policy : DEFAULT_POLICY;
    }

    public int getAgingIntervalMs() {
        return agingIntervalMs;
    }

    public boolean isEngineOnline() {
        return ensureConnected();
    }

    /** Sets the engine's scheduling policy. Returns false when the engine is not reachable. */
    public boolean configurePolicy(String newPolicy, int newAgingIntervalMs) {
        if (newPolicy == null || !POLICIES.contains(newPolicy)) {
            throw new IllegalArgumentException("Unknown scheduling policy: " + newPolicy);
        }
        this.policy = newPolicy;
        this.agingIntervalMs = Math.max(1, newAgingIntervalMs);
        if (!ensureConnected()) {
            return false;
        }
        return sendConfig();
    }

    private boolean sendConfig() {
        return sendLine(String.format(
                "{\"type\":\"CONFIG\",\"policy\":\"%s\",\"agingIntervalMs\":%d}",
                getPolicy(), agingIntervalMs));
    }

    // ------------------------------------------------------------------
    // Purchase scheduling
    // ------------------------------------------------------------------
    /**
     * Queues the request in the engine and blocks until the engine's scheduler selects it.
     * Returns null when the engine is unavailable (the caller then runs the purchase directly).
     * The caller must call {@link #markDone(long)} after the database work, if non-null was returned.
     */
    public Execution submitAndAwait(long requestId, long productId, int quantity, int priority,
                                    String source, long timeoutMs) {
        if (!ensureConnected()) {
            return null;
        }

        CompletableFuture<Execution> future = new CompletableFuture<>();
        pending.put(requestId, future);

        String json = String.format(
                "{\"type\":\"SUBMIT\",\"requestId\":%d,\"productId\":%d,\"quantity\":%d,\"priority\":%d,\"source\":\"%s\"}",
                requestId, productId, quantity, priority, source);
        if (!sendLine(json)) {
            pending.remove(requestId);
            return null;
        }

        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            pending.remove(requestId); // a late EXECUTE for this id is acknowledged automatically
            return null;
        } catch (ExecutionException e) {
            return null;               // connection dropped while waiting
        } catch (InterruptedException e) {
            pending.remove(requestId);
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /** Tells the engine the database work for this request finished, freeing its worker. */
    public void markDone(long requestId) {
        sendLine(String.format("{\"type\":\"DONE\",\"requestId\":%d}", requestId));
    }

    /** Fire-and-forget telemetry submission (used by the simulation service). */
    public boolean sendRequestToEngine(Long requestId, Long productId, int quantity, int priority, String source) {
        if (!ensureConnected()) {
            // Fallback gracefully if C Engine process is not running during local dev/tests
            return false;
        }
        return sendLine(String.format(
                "{\"type\":\"SUBMIT\",\"requestId\":%d,\"productId\":%d,\"quantity\":%d,\"priority\":%d,\"source\":\"%s\"}",
                requestId, productId, quantity, priority, source));
    }

    // ------------------------------------------------------------------
    // Connection handling
    // ------------------------------------------------------------------
    private boolean ensureConnected() {
        synchronized (connLock) {
            if (channel != null && channel.isOpen()) {
                return true;
            }
            Path path = Path.of(socketPath);
            if (!path.toFile().exists()) {
                return false;
            }
            try {
                SocketChannel ch = SocketChannel.open(StandardProtocolFamily.UNIX);
                ch.connect(UnixDomainSocketAddress.of(path));
                channel = ch;
                Thread reader = new Thread(() -> readLoop(ch), "engine-ipc-reader");
                reader.setDaemon(true);
                reader.start();
            } catch (IOException e) {
                channel = null;
                System.err.println("[EngineIpcClient] Warning: Could not communicate with C Engine socket: " + e.getMessage());
                return false;
            }
        }
        // Re-apply a previously chosen policy after (re)connecting; the engine restarts with its default.
        if (policy != null) {
            sendConfig();
        }
        return true;
    }

    private boolean sendLine(String json) {
        SocketChannel ch;
        synchronized (connLock) {
            ch = channel;
        }
        if (ch == null || !ch.isOpen()) {
            return false;
        }
        try {
            ByteBuffer buf = ByteBuffer.wrap((json + "\n").getBytes(StandardCharsets.UTF_8));
            synchronized (writeLock) {
                while (buf.hasRemaining()) {
                    ch.write(buf);
                }
            }
            return true;
        } catch (IOException e) {
            disconnect(ch);
            return false;
        }
    }

    private void disconnect(SocketChannel ch) {
        synchronized (connLock) {
            if (channel == ch) {
                channel = null;
            }
        }
        try {
            ch.close();
        } catch (IOException ignored) {
        }
        // Release everyone waiting on this connection; they fall back to direct execution.
        for (Map.Entry<Long, CompletableFuture<Execution>> e : pending.entrySet()) {
            if (pending.remove(e.getKey(), e.getValue())) {
                e.getValue().completeExceptionally(new IOException("Engine connection closed"));
            }
        }
    }

    private void readLoop(SocketChannel ch) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(Channels.newInputStream(ch), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                handleMessage(line);
            }
        } catch (IOException ignored) {
            // connection closed
        } finally {
            disconnect(ch);
        }
    }

    private static final Pattern TYPE = Pattern.compile("\"type\"\\s*:\\s*\"([A-Z_]+)\"");

    private void handleMessage(String line) {
        Matcher m = TYPE.matcher(line);
        if (!m.find()) {
            return;
        }
        String type = m.group(1);
        if ("EXECUTE".equals(type)) {
            long requestId = longField(line, "requestId", -1);
            CompletableFuture<Execution> future = pending.remove(requestId);
            if (future != null) {
                future.complete(new Execution(false,
                        longField(line, "waitMs", 0),
                        (int) longField(line, "workerId", 0),
                        stringField(line, "policy", getPolicy())));
            } else {
                markDone(requestId); // nobody is waiting (telemetry or timed-out request): free the worker
            }
        } else if ("REJECT".equals(type)) {
            long requestId = longField(line, "requestId", -1);
            CompletableFuture<Execution> future = pending.remove(requestId);
            if (future != null) {
                future.complete(new Execution(true, 0, 0, getPolicy()));
            }
        }
    }

    private static long longField(String json, String key, long fallback) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*(-?\\d+)").matcher(json);
        return m.find() ? Long.parseLong(m.group(1)) : fallback;
    }

    private static String stringField(String json, String key, String fallback) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : fallback;
    }
}
