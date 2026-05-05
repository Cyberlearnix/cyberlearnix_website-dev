package com.cyberlearnix.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class GatewayApplicationTests {

    /** Mock reactive Redis — ReactiveRedisAutoConfiguration is excluded in test profile */
    @MockBean
    private ReactiveStringRedisTemplate reactiveStringRedisTemplate;

    @Test
    void contextLoads() {
    }
}
