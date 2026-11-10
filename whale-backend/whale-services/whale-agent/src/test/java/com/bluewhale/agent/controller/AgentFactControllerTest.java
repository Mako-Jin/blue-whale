package com.bluewhale.agent.controller;

import com.bluewhale.agent.dto.FactVO;
import com.bluewhale.agent.service.FactStoreService;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 事实查询接口测试：响应装配委托服务层 + 运行时事实权限控制（T-18）。
 *
 * @author BlueWhale
 */
class AgentFactControllerTest {

    private static final String PERMISSION_HEADER = "X-Permission-Codes";

    private final FactStoreService factStoreService = mock(FactStoreService.class);

    private final AgentFactController controller = new AgentFactController(factStoreService);

    @Test
    @DisplayName("持有运行时权限：透传参数并返回服务层装配结果")
    void shouldDelegateToServiceWithRuntimePermission() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(PERMISSION_HEADER))
                .thenReturn("sys:agent:search, sys:agent:runtime:read");
        List<FactVO> expected = List.of(FactVO.builder().id("1").factType("PORT").build());
        when(factStoreService.queryVo("PORT", null, 10, true)).thenReturn(expected);

        Result<List<FactVO>> result = controller.facts("PORT", null, 10, request);

        assertSame(expected, result.getData());
        verify(factStoreService).queryVo(eq("PORT"), isNull(), eq(10), eq(true));
    }

    @Test
    @DisplayName("缺少运行时权限：未指定类型时剔除运行时事实")
    void shouldExcludeRuntimeWhenPermissionMissing() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(PERMISSION_HEADER)).thenReturn("sys:agent:search");
        List<FactVO> expected = List.of(FactVO.builder().id("2").factType("SERVICE").build());
        when(factStoreService.queryVo(null, "whale-auth", 5, false)).thenReturn(expected);

        Result<List<FactVO>> result = controller.facts(null, "whale-auth", 5, request);

        assertEquals(expected, result.getData());
        verify(factStoreService).queryVo(isNull(), eq("whale-auth"), eq(5), eq(false));
    }

    @Test
    @DisplayName("缺少运行时权限：显式请求运行时类型返回 403 且不触达服务层")
    void shouldRejectRuntimeTypeWithoutPermission() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(PERMISSION_HEADER)).thenReturn(null);

        assertThrows(BizException.class, () -> controller.facts("runtime_service", null, 10, request));

        verify(factStoreService, never()).queryVo(any(), any(), any(), anyBoolean());
    }
}
