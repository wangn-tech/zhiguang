package com.wangning.relation.api;

import com.wangning.auth.config.AuthProperties;
import com.wangning.auth.config.SecurityConfig;
import com.wangning.auth.token.JwtService;
import com.wangning.relation.api.dto.PublicProfileResponse;
import com.wangning.relation.api.dto.RelationCountersResponse;
import com.wangning.relation.service.RelationService;
import com.wangning.relation.service.RelationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RelationController.class)
@Import({SecurityConfig.class, RelationControllerTest.TestConfig.class})
class RelationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RelationService relationService;

    @MockBean
    private JwtService jwtService;

    @MockBean(name = "accessJwtDecoder")
    private JwtDecoder accessJwtDecoder;

    @Test
    void shouldAllowAnonymousPublicRelationQueriesWithoutSensitiveFields() throws Exception {
        when(relationService.listFollowings(1L, 20, 0, null)).thenReturn(List.of(publicProfile()));
        when(relationService.getCounters(1L)).thenReturn(new RelationCountersResponse(2L, 3L, 0L, 0L, 0L));

        mockMvc.perform(get("/api/v1/relation/following").param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[0].nickname").value("公开用户"))
                .andExpect(jsonPath("$[0].phone").doesNotExist())
                .andExpect(jsonPath("$[0].email").doesNotExist());

        mockMvc.perform(get("/api/v1/relation/counter").param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followings").value(2))
                .andExpect(jsonPath("$.followers").value(3));
    }

    @Test
    void shouldRequireAccessTokenForRelationWrites() throws Exception {
        mockMvc.perform(post("/api/v1/relation/follow").param("toUserId", "2"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldDelegateAuthenticatedFollowAndStatusQueries() throws Exception {
        when(jwtService.extractUserId(any(Jwt.class))).thenReturn(1L);
        when(relationService.follow(1L, 2L)).thenReturn(true);
        when(relationService.getStatus(1L, 2L)).thenReturn(new RelationStatus(true, true, true));

        mockMvc.perform(post("/api/v1/relation/follow").param("toUserId", "2").with(accessJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(true));

        mockMvc.perform(get("/api/v1/relation/status").param("toUserId", "2").with(accessJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.following").value(true))
                .andExpect(jsonPath("$.followedBy").value(true))
                .andExpect(jsonPath("$.mutual").value(true));

        verify(relationService).follow(1L, 2L);
        verify(relationService).getStatus(1L, 2L);
    }

    @Test
    void shouldRejectInvalidBearerTokenForPublicEndpoint() throws Exception {
        when(accessJwtDecoder.decode("broken-token")).thenThrow(new BadJwtException("invalid token"));

        mockMvc.perform(get("/api/v1/relation/followers")
                        .param("userId", "1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer broken-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor accessJwt() {
        return jwt().jwt(jwt -> jwt.subject("1").claim("uid", 1L).claim("token_type", "access"));
    }

    private PublicProfileResponse publicProfile() {
        return new PublicProfileResponse(
                2L,
                "公开用户",
                "https://static.example.com/avatars/2.png",
                "个人简介",
                "zg_2",
                "UNKNOWN",
                LocalDate.of(2000, 1, 1),
                "同济大学",
                "[\"Java\"]"
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestConfig {

        @Bean
        AuthProperties authProperties() {
            return new AuthProperties();
        }
    }
}
