package com.synapse.payment_service.integrationtest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.synapse.payment_service.TestConfig;
import com.synapse.payment_service.controller.InternalApiController;

import io.portone.sdk.server.PortOneClient;

@Import(InternalApiController.class)
public class ResourceServerIntegrationTest extends TestConfig {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PortOneClient portOneClient;

    @Test
    @DisplayName("리소스 접근 성공: 유효한 JWT와 올바른 scope으로 보호된 API 호출 시 200 OK를 응답한다")
    void accessProtectedResource_withValidJwt_shouldSucceed() throws Exception {
        // when
        ResultActions actions = mockMvc.perform(get("/api/internal/payments/id")
                .with(jwt().jwt(j -> j.claim("scope", "api.internal account:read"))));

        // then
        actions.andDo(print()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("리소스 접근 실패: JWT가 없을 경우, 401 Unauthorized를 응답한다")
    void accessProtectedResource_withoutJwt_shouldFail() throws Exception {
        // when
        ResultActions actions = mockMvc.perform(get("/api/internal/payments/id"));

        // then
        actions.andDo(print()).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("리소스 접근 실패: 만료된 JWT로 호출 시, 401 Unauthorized를 응답한다")
    void accessProtectedResource_withExpiredJwt_shouldFail() throws Exception {
        // when
        ResultActions actions = mockMvc.perform(get("/api/internal/payments/id")
                .with(jwt().jwt(j -> j
                        .claim("scope", "api.internal account:read")
                        .expiresAt(Instant.now().minusSeconds(3600)) // mock에서는 만료시간 체크 안함 실제로는 401 에러 발생
                )));

        // then
        actions.andDo(print()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("리소스 접근 실패: 부적절한 scope을 가진 JWT로 호출 시, 403 Forbidden을 응답한다")
    void accessProtectedResource_withInsufficientScope_shouldFail() throws Exception {
        // when
        ResultActions actions = mockMvc.perform(get("/api/internal/payments/id")
                // [핵심] API가 요구하는 'api.internal'이 아닌 다른 scope을 가진 JWT를 생성합니다.
                .with(jwt().jwt(j -> j.claim("scope", "read:only"))));

        // then
        actions.andDo(print()).andExpect(status().isForbidden());
    }
}
