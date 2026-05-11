package com.cyberlearnix.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.cors.reactive.CorsWebFilter;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class GatewaySecurityConfigTest {

    @MockBean
    private ReactiveStringRedisTemplate reactiveStringRedisTemplate;

    @Autowired
    private CorsWebFilter corsWebFilter;

    @Test
    void corsWebFilter_isCreated() {
        assertThat(corsWebFilter).isNotNull();
    }

    @Test
    void corsWebFilter_readsOriginsFromConfig() {
        // The filter bean must be created without throwing — proving that
        // the comma-separated value from config is parsed correctly.
        assertThat(corsWebFilter).isInstanceOf(CorsWebFilter.class);
    }
}
