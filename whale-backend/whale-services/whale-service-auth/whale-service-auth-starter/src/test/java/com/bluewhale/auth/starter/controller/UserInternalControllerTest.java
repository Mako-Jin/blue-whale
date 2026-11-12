package com.bluewhale.auth.starter.controller;

import com.bluewhale.auth.application.service.UserDirectoryAppService;
import com.bluewhale.auth.starter.assemble.UserAssembler;
import com.bluewhale.common.core.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.accept.ApiVersionStrategy;
import org.springframework.web.accept.DefaultApiVersionStrategy;
import org.springframework.web.accept.PathApiVersionResolver;
import org.springframework.web.accept.SemanticApiVersionParser;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link UserInternalController} 单元测试（standalone MockMvc）。
 *
 * <p>覆盖：命中返回启用用户 ID、空列表请求体校验失败（{@code code=400}）。</p>
 *
 * @author BlueWhale
 */
class UserInternalControllerTest {

    private static final int PARAM_ERROR_CODE = 400;

    private final UserDirectoryAppService userDirectoryAppService = mock(UserDirectoryAppService.class);

    private final UserInternalController controller =
            new UserInternalController(userDirectoryAppService, Mappers.getMapper(UserAssembler.class));

    private MockMvc mockMvc;

    /**
     * 构建 standalone MockMvc：注册全局异常处理器与校验器。
     */
    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        ApiVersionStrategy versionStrategy = new DefaultApiVersionStrategy(
                List.of(new PathApiVersionResolver(1, requestPath -> true)),
                new SemanticApiVersionParser(), false, null, true, null, null);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setApiVersionStrategy(versionStrategy)
                .build();
    }

    @Test
    @DisplayName("exists：返回存在且启用的用户 ID")
    void existsShouldReturnEnabledIds() throws Exception {
        when(userDirectoryAppService.findEnabledIds(any())).thenReturn(List.of(1L, 3L));

        mockMvc.perform(post("/user/v1/internal/exists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[1,2,3]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0]").value(1))
                .andExpect(jsonPath("$.data[1]").value(3));

        verify(userDirectoryAppService).findEnabledIds(List.of(1L, 2L, 3L));
    }

    @Test
    @DisplayName("exists：空用户列表返回参数错误")
    void existsShouldRejectEmptyIds() throws Exception {
        mockMvc.perform(post("/user/v1/internal/exists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[]}"))
                .andExpect(jsonPath("$.code").value(PARAM_ERROR_CODE));
    }
}
