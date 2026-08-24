package com.wangning.knowpost.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangning.auth.config.AuthProperties;
import com.wangning.auth.config.SecurityConfig;
import com.wangning.auth.token.JwtService;
import com.wangning.knowpost.api.dto.FeedItemResponse;
import com.wangning.knowpost.api.dto.FeedPageResponse;
import com.wangning.knowpost.api.dto.KnowPostDetailResponse;
import com.wangning.knowpost.api.dto.StoragePresignResponse;
import com.wangning.knowpost.service.KnowPostFeedService;
import com.wangning.knowpost.service.KnowPostService;
import com.wangning.knowpost.service.KnowPostUploadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({KnowPostController.class, KnowPostStorageController.class})
@Import({SecurityConfig.class, KnowPostControllerTest.TestConfig.class})
class KnowPostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KnowPostService knowPostService;

    @MockBean
    private KnowPostFeedService knowPostFeedService;

    @MockBean
    private KnowPostUploadService knowPostUploadService;

    @MockBean
    private JwtService jwtService;

    @MockBean(name = "accessJwtDecoder")
    private JwtDecoder accessJwtDecoder;

    @Test
    void shouldAllowAnonymousPublicFeedAndDetail() throws Exception {
        when(knowPostFeedService.getPublicFeed(1, 20, null)).thenReturn(feedPage());
        when(knowPostService.getDetail(100L, null)).thenReturn(detail());

        mockMvc.perform(get("/api/v1/knowposts/feed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value("100"))
                .andExpect(jsonPath("$.items[0].tagJson").value("[\"Java\"]"));

        mockMvc.perform(get("/api/v1/knowposts/detail/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentUrl").value("https://static.example.com/posts/100/content.md"));
        verify(knowPostFeedService).getPublicFeed(1, 20, null);
        verify(knowPostService).getDetail(100L, null);
    }

    @Test
    void shouldRequireAccessTokenForDraftCreationAndPresign() throws Exception {
        mockMvc.perform(post("/api/v1/knowposts/drafts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(post("/api/v1/storage/presign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scene":"knowpost_image","postId":"100","contentType":"image/png","ext":".png"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldDelegateAuthenticatedWriteOperations() throws Exception {
        when(jwtService.extractUserId(any(Jwt.class))).thenReturn(42L);
        when(knowPostService.createDraft(42L)).thenReturn(100L);
        when(knowPostUploadService.presignUpload(eq(42L), any())).thenReturn(new StoragePresignResponse(
                "posts/100/images/20260824/a.png",
                "https://oss.example.com/put",
                Map.of("Content-Type", "image/png"),
                600
        ));

        mockMvc.perform(post("/api/v1/knowposts/drafts").with(accessJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("100"));

        mockMvc.perform(post("/api/v1/storage/presign")
                        .with(accessJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scene":"knowpost_image","postId":"100","contentType":"image/png","ext":".png"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectKey").value("posts/100/images/20260824/a.png"))
                .andExpect(jsonPath("$.expiresIn").value(600));

        mockMvc.perform(patch("/api/v1/knowposts/100/visibility")
                        .with(accessJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + "\"visible\":\"private\"" + "}"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(knowPostService).createDraft(42L);
        verify(knowPostUploadService).presignUpload(eq(42L), any());
        verify(knowPostService).updateVisibility(42L, 100L, "private");
    }

    @Test
    void shouldReturnBadRequestForInvalidRequestBody() throws Exception {
        mockMvc.perform(post("/api/v1/storage/presign")
                        .with(accessJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + "\"scene\":\"\",\"postId\":\"100\",\"contentType\":\"image/png\"" + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("上传场景不能为空"));
    }

    @Test
    void shouldRejectInvalidBearerTokenForPublicEndpoint() throws Exception {
        when(accessJwtDecoder.decode("broken-token")).thenThrow(new BadJwtException("invalid token"));

        mockMvc.perform(get("/api/v1/knowposts/feed")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer broken-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor accessJwt() {
        return jwt().jwt(jwt -> jwt.subject("42").claim("uid", 42L).claim("token_type", "access"));
    }

    private FeedPageResponse feedPage() {
        return new FeedPageResponse(List.of(new FeedItemResponse(
                "100", "标题", "摘要", null, List.of("Java"), null, "作者", "[\"Java\"]",
                0L, 0L, false, false, false
        )), 1, 20, false);
    }

    private KnowPostDetailResponse detail() {
        return new KnowPostDetailResponse(
                "100", "标题", "摘要", "https://static.example.com/posts/100/content.md",
                List.of(), List.of("Java"), "42", null, "作者", "[\"Java\"]", 0L, 0L,
                false, false, false, "public", "image_text", null
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
