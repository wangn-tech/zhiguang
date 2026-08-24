package com.wangning.search.api;

import com.wangning.auth.config.AuthProperties;
import com.wangning.auth.config.SecurityConfig;
import com.wangning.auth.token.JwtService;
import com.wangning.knowpost.api.dto.FeedItemResponse;
import com.wangning.search.api.dto.SearchResponse;
import com.wangning.search.api.dto.SuggestResponse;
import com.wangning.search.service.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SearchController.class)
@Import({SecurityConfig.class, SearchControllerTest.TestConfig.class})
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SearchService searchService;

    @MockBean
    private JwtService jwtService;

    @MockBean(name = "accessJwtDecoder")
    private JwtDecoder accessJwtDecoder;

    @Test
    void shouldAllowAnonymousSearchAndSuggest() throws Exception {
        when(searchService.search("Java", 20, "Java", null, null)).thenReturn(searchResponse());
        when(searchService.suggest("Ja", 10)).thenReturn(new SuggestResponse(List.of("Java 并发")));

        mockMvc.perform(get("/api/v1/search?q=Java&tags=Java"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value("100"))
                .andExpect(jsonPath("$.hasMore").value(false));
        mockMvc.perform(get("/api/v1/search/suggest?prefix=Ja"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0]").value("Java 并发"));

        verify(searchService).search("Java", 20, "Java", null, null);
        verify(searchService).suggest("Ja", 10);
    }

    @Test
    void shouldRejectInvalidSearchParameters() throws Exception {
        mockMvc.perform(get("/api/v1/search?q=&size=51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    private SearchResponse searchResponse() {
        return new SearchResponse(List.of(new FeedItemResponse(
                "100", "Java 标题", "摘要", null, List.of("Java"), null, "作者", "[]",
                0L, 0L, false, false, false
        )), null, false);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestConfig {

        @Bean
        AuthProperties authProperties() {
            return new AuthProperties();
        }
    }
}
