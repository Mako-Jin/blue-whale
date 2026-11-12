package com.bluewhale.auth.starter.controller;

import com.bluewhale.auth.application.model.vo.GatewayBlacklistVO;
import com.bluewhale.auth.application.service.GatewayBlacklistAppService;
import com.bluewhale.auth.starter.assemble.GatewayBlacklistAssembler;
import com.bluewhale.common.core.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.accept.ApiVersionStrategy;
import org.springframework.web.accept.DefaultApiVersionStrategy;
import org.springframework.web.accept.PathApiVersionResolver;
import org.springframework.web.accept.SemanticApiVersionParser;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link GatewayBlacklistInternalController} 单元测试（standalone MockMvc）。
 *
 * <p>覆盖：返回结构与字段、无封禁时返回空数组。</p>
 *
 * @author BlueWhale
 */
class GatewayBlacklistInternalControllerTest {

    private final GatewayBlacklistAppService gatewayBlacklistAppService = mock(GatewayBlacklistAppService.class);

    private final GatewayBlacklistInternalController controller = new GatewayBlacklistInternalController(
            gatewayBlacklistAppService, Mappers.getMapper(GatewayBlacklistAssembler.class));

    private MockMvc mockMvc;

    /**
     * 构建 standalone MockMvc：注册全局异常处理器与校验器
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
    @DisplayName("listEnabled：返回封禁路径与方法")
    void shouldReturnBlacklistItems() throws Exception {
        when(gatewayBlacklistAppService.listEnabled()).thenReturn(List.of(
                new GatewayBlacklistVO("/pay/**", "*"),
                new GatewayBlacklistVO("/demo/abuse/**", "POST")));

        mockMvc.perform(get("/auth/v1/internal/gateway/blacklist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].pathPattern").value("/pay/**"))
                .andExpect(jsonPath("$.data[0].httpMethod").value("*"))
                .andExpect(jsonPath("$.data[1].httpMethod").value("POST"));
    }

    @Test
    @DisplayName("listEnabled：无封禁时返回空数组")
    void shouldReturnEmptyList() throws Exception {
        when(gatewayBlacklistAppService.listEnabled()).thenReturn(List.of());

        mockMvc.perform(get("/auth/v1/internal/gateway/blacklist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
