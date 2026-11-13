package com.bluewhale.auth.domain.aggregate;

import com.bluewhale.common.domain.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link RoleConstraintAggregate} 核心不变量单元测试（权限中心扩展 M3）。
 *
 * @author BlueWhale
 */
class RoleConstraintAggregateTest {

    @Test
    @DisplayName("新建互斥约束：至少 2 个角色，去重后仍需 ≥2")
    void shouldRejectMutexWithLessThanTwoRoles() {
        assertThrows(DomainException.class, () -> RoleConstraintAggregate.create(
                1L, "C001", "财务互斥", "MUTEX", List.of(1L), null));
        assertThrows(DomainException.class, () -> RoleConstraintAggregate.create(
                1L, "C001", "财务互斥", "MUTEX", List.of(1L, 1L, 1L), null));

        RoleConstraintAggregate constraint = RoleConstraintAggregate.create(
                1L, "C001", "财务互斥", "MUTEX", List.of(1L, 2L, 2L), null);
        assertThat(constraint.getConstraintType()).isEqualTo(com.bluewhale.auth.domain.model.vo.ConstraintType.MUTEX);
        assertThat(constraint.getRoleIds()).containsExactly(1L, 2L);
        assertThat(constraint.getMaxAssign()).isNull();
    }

    @Test
    @DisplayName("基数约束 max_assign 必须 > 0 且不超过覆盖角色数")
    void shouldValidateCardinalityMaxAssign() {
        assertThrows(DomainException.class, () -> RoleConstraintAggregate.create(
                1L, "C002", "基数约束", "CARDINALITY", List.of(1L, 2L, 3L), 0));
        assertThrows(DomainException.class, () -> RoleConstraintAggregate.create(
                1L, "C002", "基数约束", "CARDINALITY", List.of(1L, 2L, 3L), null));
        assertThrows(DomainException.class, () -> RoleConstraintAggregate.create(
                1L, "C002", "基数约束", "CARDINALITY", List.of(1L, 2L, 3L), 4));

        RoleConstraintAggregate constraint = RoleConstraintAggregate.create(
                1L, "C002", "基数约束", "CARDINALITY", List.of(1L, 2L, 3L), 2);
        assertThat(constraint.getMaxAssign()).isEqualTo(2);
    }

    @Test
    @DisplayName("约束类型非法或角色集合为空时拒绝")
    void shouldRejectInvalidTypeOrRoles() {
        assertThrows(DomainException.class, () -> RoleConstraintAggregate.create(
                1L, "C001", "约束", "SYMMETRIC", List.of(1L, 2L), null));
        assertThrows(DomainException.class, () -> RoleConstraintAggregate.create(
                1L, "C001", "约束", "MUTEX", List.of(), null));
    }

    @Test
    @DisplayName("互斥约束变更角色后仍需 ≥2；基数约束变更后重新校验上限")
    void shouldRevalidateOnRoleChange() {
        RoleConstraintAggregate mutex = RoleConstraintAggregate.create(
                1L, "C001", "财务互斥", "MUTEX", List.of(1L, 2L), null);
        assertThrows(DomainException.class, () -> mutex.changeRoles(List.of(1L)));
        mutex.changeRoles(List.of(1L, 3L));
        assertThat(mutex.getRoleIds()).containsExactly(1L, 3L);

        RoleConstraintAggregate cardinality = RoleConstraintAggregate.create(
                1L, "C002", "基数约束", "CARDINALITY", List.of(1L, 2L, 3L), 3);
        assertThrows(DomainException.class, () -> cardinality.changeRoles(List.of(1L, 2L)));
        cardinality.changeRoles(List.of(1L, 2L, 3L, 4L));
        cardinality.changeMaxAssign(4);
        assertThat(cardinality.getMaxAssign()).isEqualTo(4);
    }

    @Test
    @DisplayName("max_assign 变更仅限基数约束且取值合法")
    void shouldRestrictMaxAssignToCardinality() {
        RoleConstraintAggregate mutex = RoleConstraintAggregate.create(
                1L, "C001", "财务互斥", "MUTEX", List.of(1L, 2L), null);
        assertThrows(DomainException.class, () -> mutex.changeMaxAssign(1));

        RoleConstraintAggregate cardinality = RoleConstraintAggregate.create(
                1L, "C002", "基数约束", "CARDINALITY", List.of(1L, 2L, 3L), 2);
        assertThrows(DomainException.class, () -> cardinality.changeMaxAssign(0));
        assertThrows(DomainException.class, () -> cardinality.changeMaxAssign(4));
        cardinality.changeMaxAssign(1);
        assertThat(cardinality.getMaxAssign()).isEqualTo(1);
    }

    @Test
    @DisplayName("状态变更与启用判断")
    void shouldChangeStatus() {
        RoleConstraintAggregate constraint = RoleConstraintAggregate.create(
                1L, "C001", "财务互斥", "MUTEX", List.of(1L, 2L), null);

        assertThat(constraint.isEnabled()).isTrue();
        constraint.changeStatus(RoleConstraintAggregate.STATUS_DISABLED);
        assertThat(constraint.isEnabled()).isFalse();
        assertThrows(DomainException.class, () -> constraint.changeStatus(2));
    }
}
