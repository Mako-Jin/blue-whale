package com.bluewhale.auth.application.role;

import com.bluewhale.auth.application.port.RoleConstraintChecker;
import com.bluewhale.auth.application.service.impl.RoleConstraintCheckerImpl;
import com.bluewhale.auth.domain.aggregate.RoleConstraintAggregate;
import com.bluewhale.auth.domain.repository.RoleConstraintRepository;
import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * {@link RoleConstraintCheckerImpl} 互斥 / 基数约束校验单元测试（权限中心扩展 M3）。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class RoleConstraintCheckerImplTest {

    private static final long TENANT_ID = 1L;
    private static final long USER_ID = 100L;

    @Mock
    private RoleConstraintRepository constraintRepository;

    private RoleConstraintChecker newChecker() {
        return new RoleConstraintCheckerImpl(constraintRepository);
    }

    @Test
    @DisplayName("互斥约束：目标集合命中同一互斥集 ≥2 个角色时拒绝，异常携带约束名")
    void shouldRejectMutexOverlap() {
        RoleConstraintAggregate mutex = RoleConstraintAggregate.create(
                TENANT_ID, "C001", "财务互斥", "MUTEX", List.of(1L, 2L, 3L), null);
        when(constraintRepository.listEnabledByTenant(TENANT_ID)).thenReturn(List.of(mutex));

        assertThatThrownBy(() -> newChecker().checkAssignRoles(TENANT_ID, USER_ID, List.of(1L, 2L)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("财务互斥");
    }

    @Test
    @DisplayName("互斥约束：仅命中 1 个角色时放行")
    void shouldAllowSingleMutexHit() {
        RoleConstraintAggregate mutex = RoleConstraintAggregate.create(
                TENANT_ID, "C001", "财务互斥", "MUTEX", List.of(1L, 2L, 3L), null);
        when(constraintRepository.listEnabledByTenant(TENANT_ID)).thenReturn(List.of(mutex));

        assertThatCode(() -> newChecker().checkAssignRoles(TENANT_ID, USER_ID, List.of(1L, 9L)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("基数约束：命中数超过 max_assign 拒绝，等于 max_assign 放行")
    void shouldValidateCardinalityBoundary() {
        RoleConstraintAggregate cardinality = RoleConstraintAggregate.create(
                TENANT_ID, "C002", "基数约束", "CARDINALITY", List.of(1L, 2L, 3L), 2);
        when(constraintRepository.listEnabledByTenant(TENANT_ID)).thenReturn(List.of(cardinality));

        assertThatThrownBy(() -> newChecker().checkAssignRoles(TENANT_ID, USER_ID, List.of(1L, 2L, 3L)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("最多同时持有");

        assertThatCode(() -> newChecker().checkAssignRoles(TENANT_ID, USER_ID, List.of(1L, 2L)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("目标集合为空或租户为空时直接放行，不触发仓储查询")
    void shouldSkipEmptyTargets() {
        assertThatCode(() -> newChecker().checkAssignRoles(TENANT_ID, USER_ID, List.of()))
                .doesNotThrowAnyException();
        assertThatCode(() -> newChecker().checkAssignRoles(null, USER_ID, List.of(1L)))
                .doesNotThrowAnyException();
    }
}
