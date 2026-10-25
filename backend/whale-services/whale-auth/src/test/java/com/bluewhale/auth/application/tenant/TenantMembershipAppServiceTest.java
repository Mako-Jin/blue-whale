package com.bluewhale.auth.application.tenant;

import com.bluewhale.auth.domain.tenant.aggregate.Tenant;
import com.bluewhale.auth.domain.tenant.repository.TenantRepository;
import com.bluewhale.auth.domain.user.repository.UserTenantRepository;
import com.bluewhale.auth.vo.MyTenantVO;
import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link TenantMembershipAppService} 单元测试（Wave 5.54 Phase 5.1：一人多租户）。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class TenantMembershipAppServiceTest {

    private static final Long USER_ID = 1251323419503984640L;

    @Mock
    private UserTenantRepository userTenantRepository;
    @Mock
    private TenantRepository tenantRepository;
    @InjectMocks
    private TenantMembershipAppService service;

    @Test
    @DisplayName("requireSwitchable：非成员切换返回 403，且不读取租户")
    void requireSwitchableShouldRejectNonMember() {
        when(userTenantRepository.existsEnabled(USER_ID, 2L)).thenReturn(false);

        BizException ex = assertThrows(BizException.class, () -> service.requireSwitchable(USER_ID, 2L));

        assertEquals(403, ex.getCode());
        verifyNoInteractions(tenantRepository);
    }

    @Test
    @DisplayName("requireSwitchable：目标租户停用或过期返回 403")
    void requireSwitchableShouldRejectUnavailableTenant() {
        Tenant tenant = tenant(2L, "t2", "租户2", false);
        when(userTenantRepository.existsEnabled(USER_ID, 2L)).thenReturn(true);
        when(tenantRepository.findById(2L)).thenReturn(Optional.of(tenant));

        BizException ex = assertThrows(BizException.class, () -> service.requireSwitchable(USER_ID, 2L));

        assertEquals(403, ex.getCode());
    }

    @Test
    @DisplayName("requireEnabledMembership：关系被撤销时 refresh 返回 401")
    void requireEnabledMembershipShouldRejectRevokedRelation() {
        when(userTenantRepository.existsEnabled(USER_ID, 1L)).thenReturn(false);

        BizException ex = assertThrows(BizException.class, () -> service.requireEnabledMembership(USER_ID, 1L));

        assertEquals(401, ex.getCode());
    }

    @Test
    @DisplayName("listMyTenants：过滤不可用租户并标记当前租户")
    void listMyTenantsShouldFilterUnavailableAndMarkCurrent() {
        Tenant available = tenant(1L, "t1", "租户1", true);
        Tenant expired = tenant(2L, "t2", "租户2", false);
        when(userTenantRepository.listEnabledTenantIds(USER_ID)).thenReturn(List.of(1L, 2L));
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(available));
        when(tenantRepository.findById(2L)).thenReturn(Optional.of(expired));

        List<MyTenantVO> tenants = service.listMyTenants(USER_ID, 1L);

        assertEquals(1, tenants.size());
        assertEquals(1L, tenants.get(0).getTenantId());
        assertTrue(tenants.get(0).isCurrent());
    }

    private static Tenant tenant(Long id, String code, String name, boolean available) {
        Tenant tenant = org.mockito.Mockito.mock(Tenant.class);
        org.mockito.Mockito.lenient().when(tenant.getId()).thenReturn(id);
        org.mockito.Mockito.lenient().when(tenant.getTenantCode()).thenReturn(code);
        org.mockito.Mockito.lenient().when(tenant.getTenantName()).thenReturn(name);
        org.mockito.Mockito.lenient().when(tenant.isAvailableAt(any(LocalDateTime.class))).thenReturn(available);
        return tenant;
    }
}
