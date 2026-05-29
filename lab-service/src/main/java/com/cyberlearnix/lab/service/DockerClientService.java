package com.cyberlearnix.lab.service;

import com.cyberlearnix.lab.entity.LabTemplate;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.RestartPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DockerClientService {

    private final DockerClient dockerClient;

    @Value("${lab.defaults.default-cpu:0.5}")
    private double defaultCpu;

    @Value("${lab.defaults.default-memory:536870912}")
    private long defaultMemory;

    /**
     * Creates (but does not start) a container for the given lab template.
     * Container name: cyberlearnix-lab-{studentId}-{assignmentId}
     *
     * @return the Docker container ID
     */
    public String createContainer(LabTemplate template, Long studentId, Long assignmentId) {
        String containerName = "cyberlearnix-lab-" + studentId + "-" + assignmentId;
        double cpu = template.getCpuLimit() != null ? template.getCpuLimit() : defaultCpu;
        long memory = template.getMemoryLimit() != null ? template.getMemoryLimit() : defaultMemory;

        // Docker CFS bandwidth: cpuPeriod=100000µs, cpuQuota = cpu * cpuPeriod
        long cpuPeriod = 100_000L;
        long cpuQuota = (long) (cpu * cpuPeriod);

        HostConfig hostConfig = HostConfig.newHostConfig()
                .withMemory(memory)
                .withCpuPeriod(cpuPeriod)
                .withCpuQuota(cpuQuota)
                .withNetworkMode("cyberlearnix-labs-network")
                .withRestartPolicy(RestartPolicy.noRestart());

        CreateContainerResponse response = dockerClient.createContainerCmd(template.getDockerImage())
                .withName(containerName)
                .withHostConfig(hostConfig)
                .withTty(true)
                .withAttachStdin(true)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withLabels(Map.of(
                        "managed-by", "cyberlearnix",
                        "student-id", String.valueOf(studentId),
                        "assignment-id", String.valueOf(assignmentId)
                ))
                .exec();

        log.info("Created container {} (name={}) for student={} assignment={}", response.getId(), containerName, studentId, assignmentId);
        return response.getId();
    }

    public void startContainer(String containerId) {
        dockerClient.startContainerCmd(containerId).exec();
        log.info("Started container {}", containerId);
    }

    public void stopContainer(String containerId) {
        dockerClient.stopContainerCmd(containerId).withTimeout(10).exec();
        log.info("Stopped container {}", containerId);
    }

    public void removeContainer(String containerId) {
        dockerClient.removeContainerCmd(containerId).withForce(true).exec();
        log.info("Removed container {}", containerId);
    }

    /**
     * Returns lightweight stats via container inspect (status, running flag, startedAt).
     * For full CPU/memory metrics use the Docker stats stream API.
     */
    public Map<String, Object> getContainerStats(String containerId) {
        try {
            InspectContainerResponse info = dockerClient.inspectContainerCmd(containerId).exec();
            InspectContainerResponse.ContainerState state = info.getState();
            return Map.of(
                    "id", containerId,
                    "status", state.getStatus() != null ? state.getStatus() : "unknown",
                    "running", Boolean.TRUE.equals(state.getRunning()),
                    "startedAt", state.getStartedAt() != null ? state.getStartedAt() : ""
            );
        } catch (Exception e) {
            log.warn("Failed to inspect container {}: {}", containerId, e.getMessage());
            return Map.of("id", containerId, "status", "unknown", "error", e.getMessage());
        }
    }
}
