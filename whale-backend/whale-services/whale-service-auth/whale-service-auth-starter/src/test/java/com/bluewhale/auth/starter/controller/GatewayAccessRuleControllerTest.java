package com.bluewhale.auth.starter.controller;

import com.bluewhale.auth.application.model.command.SaveGatewayAccessRuleCommand;
import com.bluewhale.auth.application.model.vo.GatewayAccessRuleVO;
import com.bluewhale.auth.application.service.GatewayBlacklistAppService;
import com.bluewhale.auth.application.service.GatewayWhitelistAppService;
import com.bluewhale.auth.starter.assemble.GatewayAccessRuleAssembler;
import com.bluewhale.common.core.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.accept.ApiVersionStrategy;
import org.springframework.web.accept.DefaultApiVersionStrategy;
import org.springframework.web.accept.PathApiVersionResolver;
import org.springframework.web.accept.SemanticApiVersionParser;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 网关访问名单管理控制器单元测试（standalone MockMvc）。
 *
 * <p>覆盖：列表返回结构、路径校验失败、黑白名单新增命令转换。</p>
 *
 * @author BlueWhale
 */
class GatewayAccessRuleControllerTest {

    private final GatewayWhitelistAppService gatewayWhitelistAppService =
            mock(GatewayWhitelistAppService.class);

    private final GatewayBlacklistAppService gatewayBlacklistAppService =
            mock(GatewayBlacklistAppService.class);

    private final GatewayAccessRuleAssembler assembler = Mappers.getMapper(GatewayAccessRuleAssembler.class);

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
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new GatewayWhitelistController(gatewayWhitelistAppService, assembler),
                        new GatewayBlacklistController(gatewayBlacklistAppService, assembler))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setApiVersionStrategy(versionStrategy)
                .build();
    }

    @Test
    @DisplayName("白名单列表：返回管理字段")
    void shouldReturnWhitelistRules() throws Exception {
        when(gatewayWhitelistAppService.list(null)).thenReturn(List.of(
                new GatewayAccessRuleVO(1L, "/auth/v1/login", "*", "PLATFORM", "MIGRATION", "登录放行",
                        1, 1, null, null)));

        mockMvc.perform(get("/auth/v1/gateway/whitelist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].pathPattern").value("/auth/v1/login"))
                .andExpect(jsonPath("$.data[0].source").value("MIGRATION"))
                .andExpect(jsonPath("$.data[0].status").value(1));
    }

    @Test
    @DisplayName("白名单新增：路径未以 / 开头时返回参数错误码")
    void shouldRejectInvalidPathPattern() throws Exception {
        mockMvc.perform(post("/auth/v1/gateway/whitelist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pathPattern\":\"auth/v1/login\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("路径必须以 / 开头"));
    }

    @Test
    @DisplayName("黑名单新增：请求参数转换为保存命令")
    void shouldConvertBlacklistRequestToCommand() throws Exception {
        mockMvc.perform(post("/auth/v1/gateway/blacklist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pathPattern\":\"/pay/**\",\"httpMethod\":\"post\",\"status\":1,"
                                + "\"sortNo\":2,\"remark\":\"异常流量\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        ArgumentCaptor<SaveGatewayAccessRuleCommand> captor =
                ArgumentCaptor.forClass(SaveGatewayAccessRuleCommand.class);
        verify(gatewayBlacklistAppService).create(captor.capture());
        assertEquals("/pay/**", captor.getValue().pathPattern());
        assertEquals("post", captor.getValue().httpMethod());
        assertEquals(1, captor.getValue().status());
        assertEquals("异常流量", captor.getValue().remark());
    }
}
