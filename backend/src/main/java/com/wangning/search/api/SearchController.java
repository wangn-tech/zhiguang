package com.wangning.search.api;

import com.wangning.auth.token.JwtService;
import com.wangning.search.api.dto.SearchResponse;
import com.wangning.search.api.dto.SuggestResponse;
import com.wangning.search.service.SearchService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开知文搜索 HTTP 接口。
 */
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/search")
public class SearchController {

    private final SearchService searchService;
    private final JwtService jwtService;

    /**
     * 搜索已发布且公开的知文。
     *
     * @param keyword 搜索关键词
     * @param size 单页结果数量
     * @param tagsCsv 可选逗号分隔标签
     * @param after 可选 Base64URL 翻页游标
     * @param jwt 可选的已验证 Access Token
     * @return 搜索结果页
     */
    @GetMapping
    public SearchResponse search(
            @RequestParam("q") @NotBlank(message = "搜索关键词不能为空") @Size(max = 100, message = "搜索关键词不能超过 100 个字符")
            String keyword,
            @RequestParam(value = "size", defaultValue = "20") @Min(value = 1, message = "每页数量至少为 1")
            @Max(value = 50, message = "每页数量不能超过 50") int size,
            @RequestParam(value = "tags", required = false) String tagsCsv,
            @RequestParam(value = "after", required = false) String after,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long currentUserId = jwt == null ? null : jwtService.extractUserId(jwt);
        return searchService.search(keyword, size, tagsCsv, after, currentUserId);
    }

    /**
     * 查询标题前缀联想。
     *
     * @param prefix 标题前缀
     * @param size 最大候选数
     * @return 标题联想列表
     */
    @GetMapping("/suggest")
    public SuggestResponse suggest(
            @RequestParam("prefix") @NotBlank(message = "搜索前缀不能为空") @Size(max = 100, message = "搜索前缀不能超过 100 个字符")
            String prefix,
            @RequestParam(value = "size", defaultValue = "10") @Min(value = 1, message = "候选数量至少为 1")
            @Max(value = 20, message = "候选数量不能超过 20") int size
    ) {
        return searchService.suggest(prefix, size);
    }
}
