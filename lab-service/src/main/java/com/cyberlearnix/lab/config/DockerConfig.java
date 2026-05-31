package com.cyberlearnix.lab.config;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.okhttp.OkDockerHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DockerConfig {

    @Bean
    public DockerClient dockerClient() {
        // Prefer DOCKER_HOST env var; fall back to unix socket
        String dockerHost = System.getenv("DOCKER_HOST");
        if (dockerHost == null || dockerHost.isBlank()) {
            dockerHost = "unix:///var/run/docker.sock";
        }

        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(dockerHost)
                .build();

        OkDockerHttpClient httpClient = new OkDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .connectTimeout(30_000)
                .readTimeout(300_000)  // 5 minutes - interactive shells can be idle
                .build();

        return DockerClientImpl.getInstance(config, httpClient);
    }
}
