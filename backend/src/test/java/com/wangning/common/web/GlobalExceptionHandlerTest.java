package com.wangning.common.web;

import com.wangning.common.exception.BusinessException;
import com.wangning.common.exception.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldReturnConfiguredStatusForBusinessException() throws Exception {
        mockMvc.perform(get("/test/business-error"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("用户不存在"))
                .andExpect(jsonPath("$.path").value("/test/business-error"));
    }

    @Test
    void shouldReturnFirstValidationMessageForInvalidRequestBody() throws Exception {
        mockMvc.perform(post("/test/validation-error")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("名称不能为空"));
    }

    @Test
    void shouldHideUnexpectedExceptionDetails() throws Exception {
        mockMvc.perform(get("/test/unexpected-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("服务器内部错误"));
    }

    @RestController
    @RequestMapping("/test")
    private static class TestController {

        @GetMapping("/business-error")
        void businessError() {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
        }

        @PostMapping("/validation-error")
        void validationError(@Valid @RequestBody TestRequest request) {
        }

        @GetMapping("/unexpected-error")
        void unexpectedError() {
            throw new IllegalStateException("不应返回给客户端的内部信息");
        }
    }

    private record TestRequest(@NotBlank(message = "名称不能为空") String name) {
    }
}
