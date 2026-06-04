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
        String[] callerIdentity = resolveCallerIdentity(session);
        String callerUserId = callerIdentity[0];
        String callerRole   = callerIdentity[1];

        if (callerUserId == null) {
            log.warn("WebSocket terminal rejected — no X-User-Id header and no valid ?token= param");
            sendErrorText(session, "[ERROR] Unauthorized: missing or invalid token");
            session.close(CloseStatus.NORMAL.withReason("Unauthorized"));
            return;
        }

        LabAssignment assignment = resolveAssignment(assignmentIdStr);

        if (assignment == null
                || assignment.getContainerId() == null
                || assignment.getStatus() != AssignmentStatus.RUNNING) {
            String reason = buildRejectionReason(assignment, assignmentIdStr);
            log.warn("WebSocket terminal rejected — {}", reason);
            sendErrorText(session, "[ERROR] " + reason);
            session.close(CloseStatus.NORMAL.withReason(reason));
            return;
        }

        // Admins/instructors may connect to any assignment; students only their own.
        boolean isPrivileged = "admin".equals(callerRole) || "instructor".equals(callerRole) || "dual".equals(callerRole);
        if (!isPrivileged && !callerUserId.equals(assignment.getStudentId())) {
            log.warn("WebSocket terminal rejected — user {} is not the owner of assignment {}", callerUserId, assignment.getId());
            sendErrorText(session, "[ERROR] Forbidden: you do not own this lab assignment");
            session.close(CloseStatus.NORMAL.withReason("Forbidden"));
            return;
        }

        try {
            startDockerExec(session, assignment);
        } catch (Exception e) {
            log.error("Failed to start Docker exec for session {}: {}", session.getId(), e.getMessage(), e);
            sendErrorText(session, "[ERROR] Could not attach to container: " + e.getMessage());
            session.close(CloseStatus.SERVER_ERROR.withReason("Docker exec failed"));
        }
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

    /**
     * Returns caller userId and role from gateway-injected headers, falling back to a
     * {@code ?token=} JWT query parameter when the gateway WebSocket proxy strips injected headers.
     * Returns {@code String[2]} where {@code [0]} is userId and {@code [1]} is role; both may be null.
     */
    private String[] resolveCallerIdentity(WebSocketSession session) {
        String userId = session.getHandshakeHeaders().getFirst("X-User-Id");
        String role   = session.getHandshakeHeaders().getFirst("X-User-Role");
        if (userId == null && session.getUri() != null) {
            String[] fromToken = parseCallerFromToken(session.getUri().getQuery());
            userId = fromToken[0];
            role   = fromToken[1];
        }
        return new String[]{userId, role};
    }

    /**
     * Parses userId and role from a JWT {@code ?token=} query parameter.
     * Returns {@code String[2]}, both null on parse failure.
     */
    private String[] parseCallerFromToken(String query) {
        if (query == null) return new String[]{null, null};
        for (String param : query.split("&")) {
            if (param.startsWith("token=")) {
                String token = URLDecoder.decode(param.substring(6), StandardCharsets.UTF_8);
                try {
                    Claims claims = Jwts.parser()
                            .verifyWith(Keys.hmacShaKeyFor(jwtSecret.trim().getBytes(StandardCharsets.UTF_8)))
                            .build()
                            .parseSignedClaims(token)
                            .getPayload();
                    String userId = claims.getSubject();
                    String role   = (String) claims.get("role");
                    log.debug("WebSocket terminal: authenticated userId={} via ?token= query param", userId);
                    return new String[]{userId, role};
                } catch (Exception e) {
                    log.warn("WebSocket terminal: JWT parse from query param failed: {}", e.getMessage());
                    return new String[]{null, null};
                }
            }
        }
        return new String[]{null, null};
    }

    /**
     * Resolves a {@link LabAssignment} by numeric ID or by container name.
     * Falls back to parsing the numeric ID from the end of the container name when the
     * {@code containerName} column is null for assignments created before the column was added.
     */
    private LabAssignment resolveAssignment(String assignmentIdStr) {
        try {
            long assignmentId = Long.parseLong(assignmentIdStr);
            return assignmentRepository.findById(assignmentId).orElse(null);
        } catch (NumberFormatException e) {
            // The admin UI passes the container name (e.g. "cyberlearnix-lab-{uuid}-{id}")
            // instead of the numeric assignment ID — look it up by container name.
            log.debug("WebSocket terminal: path param '{}' is not numeric, trying container name lookup", assignmentIdStr);
            LabAssignment assignment = assignmentRepository.findByContainerName(assignmentIdStr).orElse(null);
            return assignment != null ? assignment : resolveAssignmentByIdSuffix(assignmentIdStr);
        }
    }

    /**
     * Fallback: parses the numeric assignment ID from the end of a container name.
     * Container name format: {@code cyberlearnix-lab-{studentUUID}-{assignmentId}}.
     */
    private LabAssignment resolveAssignmentByIdSuffix(String containerName) {
        if (!containerName.contains("-")) return null;
        String lastSegment = containerName.substring(containerName.lastIndexOf('-') + 1);
        try {
            long idFromName = Long.parseLong(lastSegment);
            LabAssignment assignment = assignmentRepository.findById(idFromName).orElse(null);
            if (assignment != null) {
                log.info("WebSocket terminal: resolved assignment {} via ID suffix of container name '{}'",
                        idFromName, containerName);
            }
            return assignment;
        } catch (NumberFormatException e) {
            log.warn("WebSocket terminal: cannot parse assignment ID from container name '{}'", containerName);
            return null;
        }
    }

    /** Returns a human-readable rejection reason based on the assignment's null/state condition. */
    private String buildRejectionReason(LabAssignment assignment, String assignmentIdStr) {
        if (assignment == null) {
            return "Lab assignment not found for: " + assignmentIdStr;
        }
        if (assignment.getContainerId() == null) {
            return "Lab container ID missing (assignment " + assignment.getId() + ")";
        }
        return "Lab not running — status: " + assignment.getStatus() + " (assignment " + assignment.getId() + ")";
    }

    /** Sends a text error message to the browser, logging send failures at DEBUG level. */
    private void sendErrorText(WebSocketSession session, String message) {
        try {
            session.sendMessage(new TextMessage(message));
        } catch (Exception e) {
            log.debug("Could not send error message to session {}: {}", session.getId(), e.getMessage());
        }
    }

    /** Creates a Docker exec, wires stdin/stdout streams, and starts the background exec thread. */
    private void startDockerExec(WebSocketSession session, LabAssignment assignment) {
        String containerId = assignment.getContainerId();
        // Use /bin/sh as the exec entry-point and exec into bash if available;
        // this works on both Alpine (sh only) and Debian/Ubuntu (bash present) images.
        ExecCreateCmdResponse execResponse = dockerClient.execCreateCmd(containerId)
                .withCmd("/bin/sh", "-c", "[ -x /bin/bash ] && exec /bin/bash || exec /bin/sh")
                .withAttachStdin(true)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withTty(true)
                .withEnv(java.util.List.of("TERM=xterm", "COLUMNS=80", "LINES=24"))
                .exec();

        String execId = execResponse.getId();
        ContainerStdin containerStdin = new ContainerStdin();
        sessionStdinStreams.put(session.getId(), containerStdin);
        sessionExecIds.put(session.getId(), execId);

        Thread execThread = new Thread(
                () -> runExecLoop(session, execId, containerStdin),
                "lab-terminal-" + session.getId());
        execThread.setDaemon(true);
        execThread.start();

        log.info("Terminal session opened: session={} assignment={} container={}",
                session.getId(), assignment.getId(), containerId);
    }

    /** Runs the Docker exec loop: streams container stdout/stderr to the WebSocket. */
    private void runExecLoop(WebSocketSession session, String execId, ContainerStdin containerStdin) {
        try {
            ExecStartResultCallback callback = dockerClient.execStartCmd(execId)
                    .withDetach(false)
                    .withTty(true)
                    .withStdIn(containerStdin)
                    .exec(new ExecStartResultCallback() {
                        @Override
                        public void onNext(Frame frame) {
                            sendFrameToSession(session, frame);
                        }
                    });

            initializePty(execId, containerStdin, session);
            callback.awaitCompletion();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.debug("Exec thread interrupted for session {}", session.getId());
        } catch (Exception e) {
            log.error("Exec error for session {}: {}", session.getId(), e.getMessage(), e);
            sendErrorText(session, "[ERROR] Terminal exec failed: " + e.getMessage());
        } finally {
            closeQuietly(session);
        }
    }

    /** Forwards a Docker output frame to the WebSocket browser client. */
    private void sendFrameToSession(WebSocketSession session, Frame frame) {
        if (frame == null || frame.getPayload() == null) return;
        log.info("📤 Got frame from container: type={} payload={} bytes",
                frame.getStreamType(), frame.getPayload().length);
        try {
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new BinaryMessage(ByteBuffer.wrap(frame.getPayload())));
                    log.info("📨 Sent {} bytes to browser", frame.getPayload().length);
                }
            }
        } catch (IOException e) {
            log.warn("Error sending terminal output to session {}: {}", session.getId(), e.getMessage());
        }
    }

    /** Resizes the PTY to 80×24 and sends {@code stty}/clear commands to initialise the terminal. */
    private void initializePty(String execId, ContainerStdin containerStdin, WebSocketSession session) {
        try {
            // Step 1: Resize PTY to actual dimensions (height=24 rows, width=80 cols)
            dockerClient.resizeExecCmd(execId).withSize(24, 80).exec();

            // Step 2: Wait for the shell to notice the resize
            Thread.sleep(200);

            // Step 3: Force terminal into sane state with echo enabled
            containerStdin.push("stty sane echo echoe echok -echonl icanon icrnl\r"
                    .getBytes(StandardCharsets.UTF_8));

            // Step 4: Wait for command to execute, then clear screen
            Thread.sleep(100);
            containerStdin.push("clear\r".getBytes(StandardCharsets.UTF_8));

            log.info("✅ PTY resized to 80x24, terminal initialized with echo enabled");
        } catch (Exception e) {
            log.warn("Failed to initialize PTY for session {}: {}", session.getId(), e.getMessage());
        }
    }

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
            // intentionally silent — we are already in a teardown path
        }
    }
}
