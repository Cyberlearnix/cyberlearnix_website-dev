package com.cyberlearnix.lab.websocket;

import com.cyberlearnix.lab.entity.AssignmentStatus;
import com.cyberlearnix.lab.entity.LabAssignment;
import com.cyberlearnix.lab.repository.LabAssignmentRepository;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    /** Per-session stdin pipe so browser input reaches the container. */
    private final Map<String, PipedOutputStream> sessionStdinPipes = new ConcurrentHashMap<>();

    // ─── lifecycle ──────────────────────────────────────────────────────────────

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String assignmentIdStr = extractAssignmentId(session);
        if (assignmentIdStr == null) {
            session.close(CloseStatus.BAD_DATA.withReason("Missing assignment ID in path"));
            return;
        }

        long assignmentId;
        try {
            assignmentId = Long.parseLong(assignmentIdStr);
        } catch (NumberFormatException e) {
            session.close(CloseStatus.BAD_DATA.withReason("Invalid assignment ID"));
            return;
        }

        LabAssignment assignment = assignmentRepository.findById(assignmentId).orElse(null);
        if (assignment == null
                || assignment.getContainerId() == null
                || assignment.getStatus() != AssignmentStatus.RUNNING) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Lab is not currently running"));
            return;
        }

        String containerId = assignment.getContainerId();

        // Create exec — prefer bash, fall back to sh
        ExecCreateCmdResponse execResponse = dockerClient.execCreateCmd(containerId)
                .withCmd("/bin/sh", "-c", "command -v bash > /dev/null 2>&1 && exec bash || exec sh")
                .withAttachStdin(true)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withTty(true)
                .exec();

        String execId = execResponse.getId();

        // Piped streams: browser → pipedOut → pipedIn → container stdin
        PipedOutputStream pipedOut = new PipedOutputStream();
        PipedInputStream pipedIn = new PipedInputStream(pipedOut);
        sessionStdinPipes.put(session.getId(), pipedOut);

        // Run exec in a daemon thread so Spring threads are not blocked
        Thread execThread = new Thread(() -> {
            try {
                dockerClient.execStartCmd(execId)
                        .withDetach(false)
                        .withTty(true)
                        .withStdIn(pipedIn)
                        .exec(new ExecStartResultCallback() {
                            @Override
                            public void onNext(Frame frame) {
                                if (frame == null || frame.getPayload() == null) return;
                                try {
                                    if (session.isOpen()) {
                                        session.sendMessage(new TextMessage(new String(frame.getPayload())));
                                    }
                                } catch (IOException e) {
                                    log.warn("Error sending terminal output to session {}: {}", session.getId(), e.getMessage());
                                }
                            }
                        }).awaitCompletion();
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

        log.info("Terminal session opened: session={} assignment={} container={}", session.getId(), assignmentId, containerId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        forwardToContainer(session, message.getPayload().getBytes());
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        byte[] payload = new byte[message.getPayload().remaining()];
        message.getPayload().get(payload);
        forwardToContainer(session, payload);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        PipedOutputStream pipe = sessionStdinPipes.remove(session.getId());
        if (pipe != null) {
            try {
                pipe.close();
            } catch (IOException ignore) {
            }
        }
        log.info("Terminal session closed: session={} status={}", session.getId(), status);
    }

    // ─── helpers ────────────────────────────────────────────────────────────────

    private void forwardToContainer(WebSocketSession session, byte[] data) throws IOException {
        PipedOutputStream pipe = sessionStdinPipes.get(session.getId());
        if (pipe != null) {
            pipe.write(data);
            pipe.flush();
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
