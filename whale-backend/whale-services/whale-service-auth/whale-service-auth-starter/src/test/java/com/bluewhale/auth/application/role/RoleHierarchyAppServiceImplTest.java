package com.bluewhale.auth.application.role;

import com.bluewhale.auth.application.event.DomainEventPublisher;
import com.bluewhale.auth.application.service.impl.RoleHierarchyAppServiceImpl;
import com.bluewhale.auth.domain.aggregate.RoleAggregate;
import com.bluewhale.auth.domain.model.vo.RoleCode;
import com.bluewhale.auth.domain.repository.RoleRepository;
import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * {@link RoleHierarchyAppServiceImpl} 角色继承用例单元测试（权限中心扩展 M3）。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class RoleHierarchyAppServiceImplTest {

    private static final long TENANT_ID = 1L;
    private static final long ROLE_ID = 10L;
    private static final long PARENT_ID = 20L;
    private static final long GRAND_ID = 30L;
    private static final LocalDateTime TIME = LocalDateTime.of(2026, 1, 1, 0, 0);

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private DomainEventPublisher domainEventPublisher;

    @InjectMocks
    private RoleHierarchyAppServiceImpl hierarchyAppService;

    private static RoleAggregate role(Long id, Long tenantId, Long parentId, int roleLevel) {
        return RoleAggregate.reconstituteWithHierarchy(id, tenantId, RoleCode.of("role_" + id),
                "角色" + id, null, 0, 1, parentId, roleLevel, "FULL", "GENERAL", TIME, TIME);
    }

    @Test
    @DisplayName("挂载父角色：层级由父角色 +1，保存并发布事件")
    void shouldAttachParentAndRaiseLevel() {
        RoleAggregate role = role(ROLE_ID, TENANT_ID, 0L, 1);
        RoleAggregate parent = role(PARENT_ID, TENANT_ID, 0L, 1);
        when(roleRepository.findById(anyLong())).thenAnswer(inv -> {
            long id = inv.getArgument(0);
            if (id == ROLE_ID) {
                return Optional.of(role);
            }
            if (id == PARENT_ID) {
                return Optional.of(parent);
            }
            return Optional.empty();
        });
        when(roleRepository.save(any(RoleAggregate.class))).thenAnswer(inv -> inv.getArgument(0));

        hierarchyAppService.attachParent(ROLE_ID, PARENT_ID);

        assertThat(role.getParentId()).isEqualTo(PARENT_ID);
        assertThat(role.getRoleLevel()).isEqualTo(2);
    }

    @Test
    @DisplayName("角色继承链成环（父链已包含自身）时拒绝")
    void shouldRejectCycle() {
        // parent 的父链：20 -> 30 -> 10（即自身），挂载 10 -> 20 成环
        RoleAggregate role = role(ROLE_ID, TENANT_ID, GRAND_ID, 3);
        RoleAggregate parent = role(PARENT_ID, TENANT_ID, ROLE_ID, 2);
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));
        when(roleRepository.findById(PARENT_ID)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> hierarchyAppService.attachParent(ROLE_ID, PARENT_ID))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("成环");
    }

    @Test
    @DisplayName("父角色跨租户时拒绝")
    void shouldRejectCrossTenantParent() {
        RoleAggregate role = role(ROLE_ID, TENANT_ID, 0L, 1);
        RoleAggregate parent = role(PARENT_ID, 2L, 0L, 1);
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));
        when(roleRepository.findById(PARENT_ID)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> hierarchyAppService.attachParent(ROLE_ID, PARENT_ID))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("同租户");
    }

    @Test
    @DisplayName("解除父角色：回到顶级且层级重置为 1")
    void shouldDetachParentAndResetLevel() {
        RoleAggregate role = role(ROLE_ID, TENANT_ID, PARENT_ID, 2);
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));
        when(roleRepository.save(any(RoleAggregate.class))).thenAnswer(inv -> inv.getArgument(0));

        hierarchyAppService.detachParent(ROLE_ID);

        assertThat(role.isTopLevel()).isTrue();
        assertThat(role.getRoleLevel()).isEqualTo(1);
    }
}
