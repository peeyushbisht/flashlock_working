package com.flashlock.ipc;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;

@Component
public class EngineIpcClient {

    @Value("${flashlock.engine.socket-path:/tmp/flashlock.sock}")
    private String socketPath;

    public boolean sendRequestToEngine(Long requestId, Long productId, int quantity, int priority, String source) {
        Path path = Path.of(socketPath);
        if (!path.toFile().exists()) {
            // Fallback gracefully if C Engine process is not running during local dev/tests
            return false;
        }

        try (SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
            channel.connect(UnixDomainSocketAddress.of(path));
            
            PrintWriter writer = new PrintWriter(Channels.newWriter(channel, "UTF-8"), true);
            String jsonPayload = String.format(
                "{\"type\":\"SUBMIT\",\"requestId\":%d,\"productId\":%d,\"quantity\":%d,\"priority\":%d,\"source\":\"%s\"}",
                requestId, productId, quantity, priority, source
            );
            writer.println(jsonPayload);
            return true;
        } catch (Exception e) {
            System.err.println("[EngineIpcClient] Warning: Could not communicate with C Engine socket: " + e.getMessage());
            return false;
        }
    }
}
