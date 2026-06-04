package com.cyberlearnix.lab.websocket;

import com.cyberlearnix.lab.entity.AssignmentStatus;
import com.cyberlearnix.lab.entity.LabAssignment;
import com.cyberlearnix.lab.repository.LabAssignmentRepository;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket handler that tunnels a browser terminal to a Docker container shell.
 *
 * Connection URL: /labs/terminal/{assignmentId}
 *
 * On connect:  creates a Docker exec with bash/sh, wires up a piped stream for stdin,
 *              and streams stdout/stderr back to the WebSocket.
 * On message:  forwards browser keystrokes to the container's stdin.
 * On close:    tears down the piped stream so the exec process exits cleanly.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class LabTerminalWebSocketHandler extends AbstractWebSocketHandler {

    private final DockerClient dockerClient;
    private final LabAssignmentRepository assignmentRepository;

    @Value("${jwt.secret}")
    private String jwtSecret;

    /** Per-session stdin stream so browser input reaches the container. */
    private final Map<String, ContainerStdin> sessionStdinStreams = new ConcurrentHashMap<>();
    /** Per-session exec ID for PTY resizing. */
    private final Map<String, String> sessionExecIds = new ConcurrentHashMap<>();

    /**
     * Thread-safe stdin InputStream backed by a LinkedBlockingQueue.
     *
     * Java's PipedInputStream/PipedOutputStream track the "writer thread" and throw
     * "Write end dead" when that thread is recycled by Spring's WebSocket thread pool.
     * This class has no thread-ownership concept, so it works reliably across thread pools.
     */
    private static class ContainerStdin extends InputStream {
        private final LinkedBlockingQueue<byte[]> queue = new LinkedBlockingQueue<>();
        private byte[] current = new byte[0];
        private int pos = 0;
        private volatile boolean eof = false;

        /** Called from the WebSocket handler thread to deliver a keystroke/paste. */
        void push(byte[] data) {
            if (!eof && data.length > 0) {
                queue.offer(data);
                log.info("🔵 Pushed {} byte(s) to stdin queue, queue size: {}", data.length, queue.size());
            }
        }

        /** Called on session close to unblock the docker-java reader thread. */
        void signalEof() {
            eof = true;
            queue.offer(new byte[0]); // wake up any blocked read()
        }

        @Override
        public int read() throws IOException {
            while (pos >= current.length) {
                if (eof && queue.isEmpty()) {
                    log.debug("read() returning EOF");
                    return -1;
                }
                try {
                    // Poll with short timeout instead of blocking indefinitely
                    // This keeps docker-java's stdin reader thread responsive and allows
                    // the PTY to initialize properly even when no initial input is available
                    byte[] chunk = queue.poll(100, TimeUnit.MILLISECONDS);
                    if (chunk == null) {
                        // No data yet - check EOF and loop to keep waiting
                        if (eof) return -1;
                        continue;
                    }
                    if (chunk.length == 0) return -1; // EOF sentinel
                    log.info("🟢 read() got chunk with {} bytes", chunk.length);
                    current = chunk;
                    pos = 0;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while reading stdin", e);
                }
            }
            int b = current[pos++] & 0xFF;
            log.trace("read() returning byte: {} ('{}')", b, (char) b);
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (b == null) throw new NullPointerException("buffer");
            if (off < 0 || len < 0 || len > b.length - off) throw new IndexOutOfBoundsException();
            if (len == 0) return 0;

            int firstByte = read();
            if (firstByte < 0) return -1;

            b[off] = (byte) firstByte;
            int count = 1;
            while (count < len && pos < current.length) {
                b[off + count] = current[pos++];
                count++;
            }
            return count;
        }

        @Override
        public int available() {
            return Math.max(0, current.length - pos) + queue.size();
        }

        @Override
        public void close() {
            signalEof();
        }
    }

    // ─── lifecycle ──────────────────────────────────────────────────────────────

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String assignmentIdStr = extractAssignmentId(session);
        if (assignmentIdStr == null) {
            session.close(CloseStatus.BAD_DATA.withReason("Missing assignment ID in path"));
            return;
        }

        // Verify the requesting user owns this assignment.
        // Primary: gateway injects X-User-Id after validating the JWT.
        // Fallback: Spring Cloud Gateway's WebSocket proxy may not forward filter-injected headers;
        //           parse the JWT from the ?token= query parameter directly in that case.
        String callerUserId = session.getHandshakeHeaders().getFirst("X-User-Id");
        String callerRole   = session.getHandshakeHeaders().getFirst("X-User-Role");

        if (callerUserId == null && session.getUri() != null) {
            String query = session.getUri().getQuery();
            if (query != null) {
                for (String param : query.split("&")) {
                    if (param.startsWith("token=")) {
                        String token = URLDecoder.decode(param.substring(6), StandardCharsets.UTF_8);
                        try {
                            Claims claims = Jwts.parser()
                                    .verifyWith(Keys.hmacShaKeyFor(jwtSecret.trim().getBytes(StandardCharsets.UTF_8)))
                                    .build()
                                    .parseSignedClaims(token)
                                    .getPayload();
                            callerUserId = claims.getSubject();
                            callerRole = (String) claims.get("role");
                            log.debug("WebSocket terminal: authenticated userId={} via ?token= query param", callerUserId);
                        } catch (Exception e) {
                            log.warn("WebSocket terminal: JWT parse from query param failed: {}", e.getMessage());
                        }
                        break;
                    }
                }
            }
        }

        if (callerUserId == null) {
            log.warn("WebSocket terminal rejected — no X-User-Id header and no valid ?token= param");
            try { session.sendMessage(new TextMessage("[ERROR] Unauthorized: missing or invalid token")); } catch (Exception ignored) {}
            session.close(CloseStatus.NORMAL.withReason("Unauthorized"));
            return;
        }

        long assignmentId;
        LabAssignment assignment;
        try {
            assignmentId = Long.parseLong(assignmentIdStr);
            assignment = assignmentRepository.findById(assignmentId).orElse(null);
        } catch (NumberFormatException e) {
            // The admin UI passes the container name (e.g. "cyberlearnix-lab-{uuid}-{id}")
            // instead of the numeric assignment ID — look it up by container name.
            log.debug("WebSocket terminal: path param '{}' is not numeric, trying container name lookup", assignmentIdStr);
            assignment = assignmentRepository.findByContainerName(assignmentIdStr).orElse(null);

            // Fallback: if containerName is null in DB (assignments created before containerName
            // column was populated), parse the numeric ID from the end of the container name.
            // Format: "cyberlearnix-lab-{studentUUID}-{assignmentId}"
            if (assignment == null && assignmentIdStr.contains("-")) {
                String lastSegment = assignmentIdStr.substring(assignmentIdStr.lastIndexOf('-') + 1);
                try {
                    long idFromName = Long.parseLong(lastSegment);
                    assignment = assignmentRepository.findById(idFromName).orElse(null);
                    if (assignment != null) {
                        log.info("WebSocket terminal: resolved assignment {} via ID suffix of container name '{}'",
                                idFromName, assignmentIdStr);
                    }
                } catch (NumberFormatException ignored) {
                    log.warn("WebSocket terminal: cannot parse assignment ID from container name '{}'", assignmentIdStr);
                }
            }
        }

        if (assignment == null
                || assignment.getContainerId() == null
                || assignment.getStatus() != AssignmentStatus.RUNNING) {
            String reason = assignment == null
                    ? "Lab assignment not found for: " + assignmentIdStr
                    : (assignment.getContainerId() == null
                            ? "Lab container ID missing (assignment " + assignment.getId() + ")"
                            : "Lab not running — status: " + assignment.getStatus()
                              + " (assignment " + assignment.getId() + ")");
            log.warn("WebSocket terminal rejected — {}", reason);
            try { session.sendMessage(new TextMessage("[ERROR] " + reason)); } catch (Exception ignored) {}
            session.close(CloseStatus.NORMAL.withReason(reason));
            return;
        }

        // Admins/instructors may connect to any assignment; students only their own.
        boolean isPrivileged = "admin".equals(callerRole) || "instructor".equals(callerRole) || "dual".equals(callerRole);
        if (!isPrivileged && !callerUserId.equals(assignment.getStudentId())) {
            log.warn("WebSocket terminal rejected — user {} is not the owner of assignment {}", callerUserId, assignment.getId());
            try { session.sendMessage(new TextMessage("[ERROR] Forbidden: you do not own this lab assignment")); } catch (Exception ignored) {}
            session.close(CloseStatus.NORMAL.withReason("Forbidden"));
            return;
        }

        String containerId = assignment.getContainerId();

        // Create exec with bash
        // We'll force interactive terminal behavior with explicit stty commands
        ExecCreateCmdResponse execResponse = dockerClient.execCreateCmd(containerId)
                .withCmd("bash")
                .withAttachStdin(true)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withTty(true)
                .withEnv(java.util.List.of("TERM=xterm", "COLUMNS=80", "LINES=24"))
                .exec();

        String execId = execResponse.getId();

        // stdin stream: browser → ContainerStdin.push() → docker exec stdin
        ContainerStdin containerStdin = new ContainerStdin();
        sessionStdinStreams.put(session.getId(), containerStdin);
        sessionExecIds.put(session.getId(), execId);

        // Run exec in a daemon thread so Spring threads are not blocked
        Thread execThread = new Thread(() -> {
            try {
                // Start the exec (non-blocking)
                ExecStartResultCallback callback = dockerClient.execStartCmd(execId)
                        .withDetach(false)
                        .withTty(true)
                        .withStdIn(containerStdin)
                        .exec(new ExecStartResultCallback() {
                            @Override
                            public void onNext(Frame frame) {
                                log.info("📤 Got frame from container: type={} payload={} bytes", 
                                        frame != null ? frame.getStreamType() : "null", 
                                        frame != null && frame.getPayload() != null ? frame.getPayload().length : 0);
                                if (frame == null || frame.getPayload() == null) return;
                                try {
                                    synchronized (session) {
                                        if (session.isOpen()) {
                                            session.sendMessage(new BinaryMessage(
                                                    ByteBuffer.wrap(frame.getPayload())));
                                            log.info("📨 Sent {} bytes to browser", frame.getPayload().length);
                                        }
                                    }
                                } catch (IOException e) {
                                    log.warn("Error sending terminal output to session {}: {}", session.getId(), e.getMessage());
                                }
                            }
                        });

                // Now that exec is started, initialize PTY properly
                // Critical: bash needs dimensions BEFORE it can echo properly
                try {
                    // Step 1: Resize PTY to actual dimensions
                    dockerClient.resizeExecCmd(execId)
                            .withSize(24, 80)  // height=24 rows, width=80 cols
                            .exec();
                    
                    // Step 2: Wait for bash to notice the resize
                    Thread.sleep(200);
                    
                    // Step 3: Force terminal into sane state with echo enabled
                    // Send as single command with Enter to execute immediately
                    String initCmd = "stty sane echo echoe echok -echonl icanon icrnl\r";
                    containerStdin.push(initCmd.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    
                    // Step 4: Wait for command to execute
                    Thread.sleep(100);
                    
                    // Step 5: Clear the screen and show fresh prompt
                    containerStdin.push("clear\r".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    
                    log.info("✅ PTY resized to 80x24, terminal initialized with echo enabled");
                } catch (Exception e) {
                    log.warn("Failed to initialize PTY for session {}: {}", session.getId(), e.getMessage());
                }

                // Wait for exec to complete
                callback.awaitCompletion();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.debug("Exec thread interrupted for session {}", session.getId());
            } catch (Exception e) {
                log.error("Exec error for session {}: {}", session.getId(), e.getMessage(), e);
            } finally {
                closeQuietly(session);
            }
        }, "lab-terminal-" + session.getId());
        execThread.setDaemon(true);
        execThread.start();

        log.info("Terminal session opened: session={} assignment={} container={}", session.getId(), assignment.getId(), containerId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        forwardToContainer(session, message.getPayload().getBytes());
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        byte[] payload = new byte[message.getPayload().remaining()];
        message.getPayload().get(payload);
        forwardToContainer(session, payload);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        ContainerStdin stdin = sessionStdinStreams.remove(session.getId());
        sessionExecIds.remove(session.getId());
        if (stdin != null) {
            stdin.signalEof();
        }
        log.info("Terminal session closed: session={} status={}", session.getId(), status);
    }

    // ─── helpers ────────────────────────────────────────────────────────────────

    private void forwardToContainer(WebSocketSession session, byte[] data) {
        ContainerStdin stdin = sessionStdinStreams.get(session.getId());
        if (stdin != null) {
            log.info("stdin → container: {} byte(s)", data.length);
            stdin.push(data);
        } else {
            log.warn("forwardToContainer: no stdin stream for session {}", session.getId());
        }
    }

    /** Extracts the last path segment, which is the assignmentId. */
    private String extractAssignmentId(WebSocketSession session) {
        if (session.getUri() == null) return null;
        String path = session.getUri().getPath();
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 && lastSlash < path.length() - 1 ? path.substring(lastSlash + 1) : null;
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            if (session.isOpen()) {
                session.close(CloseStatus.NORMAL);
            }
        } catch (IOException ignore) {
        }
    }
}
