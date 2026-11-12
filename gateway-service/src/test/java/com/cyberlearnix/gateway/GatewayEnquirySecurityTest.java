package com.cyberlearnix.gateway;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("unit")
class GatewayEnquirySecurityTest {

    @Test
    void postPublicEnquiries_isAllowedWithoutJwt() {
        ReactiveStringRedisTemplate redisTemplate = mock(ReactiveStringRedisTemplate.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(redisTemplate);
        ReflectionTestUtils.setField(filter, "jwtSecret", "test-secret-key-for-ci-only-do-not-use-in-production-xxxxxxxxxxxxxxxxxxx");

        MockServerHttpRequest request = MockServerHttpRequest.post("/api/public/enquiries").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain, times(1)).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void getAdminEnquiries_withoutJwt_returns401() {
        ReactiveStringRedisTemplate redisTemplate = mock(ReactiveStringRedisTemplate.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(redisTemplate);
        ReflectionTestUtils.setField(filter, "jwtSecret", "test-secret-key-for-ci-only-do-not-use-in-production-xxxxxxxxxxxxxxxxxxx");

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/admin/enquiries").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
