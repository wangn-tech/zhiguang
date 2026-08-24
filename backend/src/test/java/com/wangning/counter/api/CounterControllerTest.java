package com.wangning.counter.api;

import com.wangning.auth.config.AuthProperties;
import com.wangning.auth.config.SecurityConfig;
import com.wangning.auth.token.JwtService;
import com.wangning.counter.service.CounterActionResult;
import com.wangning.counter.service.CounterActionService;
import com.wangning.counter.service.CounterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({ActionController.class, CounterController.class})
@Import({SecurityConfig.class, CounterControllerTest.TestConfig.class})
class CounterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CounterActionService counterActionService;

    @MockBean
    private CounterService counterService;

    @MockBean
    private JwtService jwtService;

    @MockBean(name = "accessJwtDecoder")
    private JwtDecoder accessJwtDecoder;

    @Test
    void shouldRequireAccessTokenForInteractionWrite() throws Exception {
        mockMvc.perform(post("/api/v1/action/like")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entityType\":\"knowpost\",\"entityId\":\"100\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldKeepExistingActionAndCounterContracts() throws Exception {
        when(jwtService.extractUserId(any(Jwt.class))).thenReturn(1L);
        when(counterActionService.like("knowpost", "100", 1L))
                .thenReturn(new CounterActionResult(true, true));
        when(counterService.getCounts("knowpost", "100", List.of("like", "fav")))
                .thenReturn(Map.of("like", 3L, "fav", 2L));

        mockMvc.perform(post("/api/v1/action/like")
                        .with(accessJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entityType\":\"knowpost\",\"entityId\":\"100\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed").value(true))
                .andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.faved").doesNotExist());

        mockMvc.perform(get("/api/v1/counter/knowpost/100")
                        .param("metrics", "like,fav")
                        .with(accessJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entityType").value("knowpost"))
                .andExpect(jsonPath("$.entityId").value("100"))
                .andExpect(jsonPath("$.counts.like").value(3))
                .andExpect(jsonPath("$.counts.fav").value(2));

        verify(counterActionService).like("knowpost", "100", 1L);
        verify(counterService).getCounts("knowpost", "100", List.of("like", "fav"));
    }

    @Test
    void shouldRejectInvalidActionAndUnknownMetric() throws Exception {
        mockMvc.perform(post("/api/v1/action/fav")
                        .with(accessJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entityType\":\"knowpost\",\"entityId\":\"0\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("实体 ID 必须为正整数"));

        mockMvc.perform(get("/api/v1/counter/knowpost/100")
                        .param("metrics", "like,comment")
                        .with(accessJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("计数指标仅支持 like、fav"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor accessJwt() {
        return jwt().jwt(jwt -> jwt.subject("1").claim("uid", 1L).claim("token_type", "access"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestConfig {

        @Bean
        AuthProperties authProperties() {
            return new AuthProperties();
        }
    }
}
