package com.bluewhale.auth.starter.controller;

import com.bluewhale.auth.application.model.vo.GatewayWhitelistVO;
import com.bluewhale.auth.application.service.GatewayWhitelistAppService;
import com.bluewhale.auth.starter.assemble.GatewayWhitelistAssembler;
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
 * {@link GatewayWhitelistInternalController} 单元测试（standalone MockMvc）。
 *
 * <p>覆盖：返回结构与字段、空列表。</p>
 *
 * @author BlueWhale
 */
class GatewayWhitelistInternalControllerTest {

    private final GatewayWhitelistAppService gatewayWhitelistAppService = mock(GatewayWhitelistAppService.class);

    private final GatewayWhitelistInternalController controller = new GatewayWhitelistInternalController(
            gatewayWhitelistAppService, Mappers.getMapper(GatewayWhitelistAssembler.class));

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
    @DisplayName("listEnabled：返回白名单路径与方法")
    void shouldReturnWhitelistItems() throws Exception {
        when(gatewayWhitelistAppService.listEnabled()).thenReturn(List.of(
                new GatewayWhitelistVO("/auth/v1/login", "*", "PLATFORM", 0L),
                new GatewayWhitelistVO("/music/stream/**", "GET", "TENANT", 5L)));

        mockMvc.perform(get("/auth/v1/internal/gateway/whitelist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].pathPattern").value("/auth/v1/login"))
                .andExpect(jsonPath("$.data[0].httpMethod").value("*"))
                .andExpect(jsonPath("$.data[0].scope").value("PLATFORM"))
                .andExpect(jsonPath("$.data[0].tenantId").value(0))
                .andExpect(jsonPath("$.data[1].pathPattern").value("/music/stream/**"))
                .andExpect(jsonPath("$.data[1].httpMethod").value("GET"))
                .andExpect(jsonPath("$.data[1].scope").value("TENANT"))
                .andExpect(jsonPath("$.data[1].tenantId").value(5));
    }

    @Test
    @DisplayName("listEnabled：无数据时返回空数组")
    void shouldReturnEmptyList() throws Exception {
        when(gatewayWhitelistAppService.listEnabled()).thenReturn(List.of());

        mockMvc.perform(get("/auth/v1/internal/gateway/whitelist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
