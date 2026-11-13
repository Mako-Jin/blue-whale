package com.bluewhale.auth.application.service.impl;

import com.bluewhale.auth.application.model.command.SaveGatewayRouteOverrideCommand;
import com.bluewhale.auth.application.model.vo.GatewayRouteOverrideVO;
import com.bluewhale.auth.domain.aggregate.GatewayRouteOverrideAggregate;
import com.bluewhale.auth.domain.repository.GatewayRouteOverrideRepository;
import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link GatewayRouteOverrideAppServiceImpl} 单元测试：路由覆盖 CRUD、
 * 重复/不存在守卫与版本自增。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class GatewayRouteOverrideAppServiceImplTest {

    @Mock
    private GatewayRouteOverrideRepository routeOverrideRepository;

    private GatewayRouteOverrideAppServiceImpl appService;

    @BeforeEach
    void setUp() {
        appService = new GatewayRouteOverrideAppServiceImpl(routeOverrideRepository);
    }

    @Test
    @DisplayName("listAll：返回全部覆盖条目（含停用）")
    void shouldListAllOverrides() {
        GatewayRouteOverrideAggregate enabled = GatewayRouteOverrideAggregate.create(
                "whale-a", 1, null, null, "维护期停用恢复");
        GatewayRouteOverrideAggregate disabled = GatewayRouteOverrideAggregate.create(
                "whale-b", 0, null, null, "灰度下线");
        when(routeOverrideRepository.findAll()).thenReturn(List.of(enabled, disabled));

        List<GatewayRouteOverrideVO> vos = appService.listAll();

        assertEquals(2, vos.size());
        assertTrue(vos.get(0).enabled());
        assertFalse(vos.get(1).enabled());
    }

    @Test
    @DisplayName("create：新增路由覆盖成功")
    void shouldCreateOverride() {
        when(routeOverrideRepository.findById("whale-demo")).thenReturn(Optional.empty());

        appService.create("whale-demo", new SaveGatewayRouteOverrideCommand(
                false, LocalDateTime.of(2026, 11, 13, 0, 0), null, "发布冻结窗口"));

        ArgumentCaptor<GatewayRouteOverrideAggregate> captor =
                ArgumentCaptor.forClass(GatewayRouteOverrideAggregate.class);
        verify(routeOverrideRepository).save(captor.capture());
        assertEquals("whale-demo", captor.getValue().getRouteId());
        assertFalse(captor.getValue().isEnabled());
        assertEquals(1L, captor.getValue().getVersion());
    }

    @Test
    @DisplayName("create：条目已存在时拒绝")
    void shouldRejectDuplicateCreate() {
        when(routeOverrideRepository.findById("whale-demo")).thenReturn(Optional.of(
                GatewayRouteOverrideAggregate.create("whale-demo", 1, null, null, null)));

        assertThrows(BizException.class, () -> appService.create("whale-demo",
                new SaveGatewayRouteOverrideCommand(true, null, null, null)));
        verify(routeOverrideRepository, never()).save(any());
    }

    @Test
    @DisplayName("update：条目存在时更新并自增版本号")
    void shouldUpdateAndIncrementVersion() {
        GatewayRouteOverrideAggregate existing = GatewayRouteOverrideAggregate.create(
                "whale-demo", 1, null, null, null);
        when(routeOverrideRepository.findById("whale-demo")).thenReturn(Optional.of(existing));

        appService.update("whale-demo", new SaveGatewayRouteOverrideCommand(
                false, null, null, "临时下线"));

        ArgumentCaptor<GatewayRouteOverrideAggregate> captor =
                ArgumentCaptor.forClass(GatewayRouteOverrideAggregate.class);
        verify(routeOverrideRepository).save(captor.capture());
        assertFalse(captor.getValue().isEnabled());
        assertEquals(2L, captor.getValue().getVersion());
        assertEquals("临时下线", captor.getValue().getRemark());
    }

    @Test
    @DisplayName("update：条目不存在时拒绝")
    void shouldRejectUpdateWhenMissing() {
        when(routeOverrideRepository.findById("whale-demo")).thenReturn(Optional.empty());

        assertThrows(BizException.class, () -> appService.update("whale-demo",
                new SaveGatewayRouteOverrideCommand(false, null, null, null)));
        verify(routeOverrideRepository, never()).save(any());
    }

    @Test
    @DisplayName("delete：条目存在时物理删除")
    void shouldDeleteExistingOverride() {
        when(routeOverrideRepository.findById("whale-demo")).thenReturn(Optional.of(
                GatewayRouteOverrideAggregate.create("whale-demo", 0, null, null, null)));

        appService.delete("whale-demo");

        verify(routeOverrideRepository).deleteById("whale-demo");
    }

    @Test
    @DisplayName("delete：条目不存在时拒绝")
    void shouldRejectDeleteWhenMissing() {
        when(routeOverrideRepository.findById("whale-demo")).thenReturn(Optional.empty());

        assertThrows(BizException.class, () -> appService.delete("whale-demo"));
        verify(routeOverrideRepository, never()).deleteById(any());
    }
}
