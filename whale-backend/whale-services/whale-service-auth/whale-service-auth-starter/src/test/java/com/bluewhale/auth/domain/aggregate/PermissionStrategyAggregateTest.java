package com.bluewhale.auth.domain.aggregate;

import com.bluewhale.auth.domain.model.vo.StrategyCondition;
import com.bluewhale.common.domain.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link PermissionStrategyAggregate} 核心不变量单元测试（权限中心扩展 M3）。
 *
 * @author BlueWhale
 */
class PermissionStrategyAggregateTest {

    @Test
    @DisplayName("新建策略：默认非默认、启用、缺省优先级 100")
    void shouldCreateStrategyWithDefaults() {
        PermissionStrategyAggregate strategy = PermissionStrategyAggregate.create(
                1L, "S001", "全员基线", PermissionStrategyAggregate.TYPE_GRANT,
                List.of(1L), List.of(10L, 11L), null, false, null);

        assertThat(strategy.getPriority()).isEqualTo(100);
        assertThat(strategy.isEnabled()).isTrue();
        assertThat(strategy.isDefaultStrategy()).isFalse();
        assertThat(strategy.isDeny()).isFalse();
        assertThat(strategy.getConditions()).isEmpty();
    }

    @Test
    @DisplayName("生效角色 / 资源集合为空时拒绝创建")
    void shouldRejectEmptyTargets() {
        assertThrows(DomainException.class, () -> PermissionStrategyAggregate.create(
                1L, "S001", "策略", PermissionStrategyAggregate.TYPE_GRANT,
                List.of(), List.of(10L), null, false, null));
        assertThrows(DomainException.class, () -> PermissionStrategyAggregate.create(
                1L, "S001", "策略", PermissionStrategyAggregate.TYPE_GRANT,
                List.of(1L), List.of(), null, false, null));
        assertThrows(DomainException.class, () -> PermissionStrategyAggregate.create(
                1L, "S001", "策略", PermissionStrategyAggregate.TYPE_GRANT,
                java.util.Arrays.asList((Long) null), List.of(10L), null, false, null));
    }

    @Test
    @DisplayName("策略类型非法或优先级为负时拒绝")
    void shouldRejectInvalidTypeOrPriority() {
        assertThrows(DomainException.class, () -> PermissionStrategyAggregate.create(
                1L, "S001", "策略", "AUDIT", List.of(1L), List.of(10L), null, false, null));
        assertThrows(DomainException.class, () -> PermissionStrategyAggregate.create(
                1L, "S001", "策略", PermissionStrategyAggregate.TYPE_GRANT,
                List.of(1L), List.of(10L), null, false, -1));
    }

    @Test
    @DisplayName("生效目标整体替换并去重，超过上限拒绝")
    void shouldChangeTargetsAndDeduplicate() {
        PermissionStrategyAggregate strategy = PermissionStrategyAggregate.create(
                1L, "S001", "策略", PermissionStrategyAggregate.TYPE_GRANT,
                List.of(1L, 2L), List.of(10L), null, false, null);

        strategy.changeTargets(java.util.Arrays.asList(2L, 2L, 3L, null), List.of(10L));

        assertThat(strategy.getRoleIds()).containsExactly(2L, 3L);

        List<Long> oversized = new java.util.ArrayList<>();
        for (long i = 0; i <= PermissionStrategyAggregate.MAX_TARGETS; i++) {
            oversized.add(i);
        }
        assertThrows(DomainException.class, () -> strategy.changeTargets(oversized, List.of(10L)));
    }

    @Test
    @DisplayName("默认策略标记与取消")
    void shouldMarkAndUnmarkDefault() {
        PermissionStrategyAggregate strategy = PermissionStrategyAggregate.create(
                1L, "S001", "策略", PermissionStrategyAggregate.TYPE_DENY,
                List.of(1L), List.of(10L), null, true, 5);

        assertThat(strategy.isDefaultStrategy()).isTrue();
        assertThat(strategy.isDeny()).isTrue();

        strategy.unmarkDefault();
        assertThat(strategy.isDefaultStrategy()).isFalse();
        strategy.markDefault();
        assertThat(strategy.isDefaultStrategy()).isTrue();
    }

    @Test
    @DisplayName("附加条件操作符非法时拒绝；变更条件整体替换")
    void shouldValidateAndChangeConditions() {
        assertThrows(DomainException.class,
                () -> new StrategyCondition("attr.deptId", "regex", ".*"));

        PermissionStrategyAggregate strategy = PermissionStrategyAggregate.create(
                1L, "S001", "策略", PermissionStrategyAggregate.TYPE_GRANT,
                List.of(1L), List.of(10L), null, false, null);

        strategy.changeConditions(List.of(new StrategyCondition("attr.deptId", " EQ ", 10)));
        assertThat(strategy.getConditions()).hasSize(1);
        assertThat(strategy.getConditions().get(0).op()).isEqualTo("eq");

        strategy.changeConditions(null);
        assertThat(strategy.getConditions()).isEmpty();
    }

    @Test
    @DisplayName("变更优先级与状态")
    void shouldChangePriorityAndStatus() {
        PermissionStrategyAggregate strategy = PermissionStrategyAggregate.create(
                1L, "S001", "策略", PermissionStrategyAggregate.TYPE_GRANT,
                List.of(1L), List.of(10L), null, false, 100);

        strategy.changePriority(1);
        strategy.changeStatus(PermissionStrategyAggregate.STATUS_DISABLED);

        assertThat(strategy.getPriority()).isEqualTo(1);
        assertThat(strategy.isEnabled()).isFalse();
        assertThrows(DomainException.class, () -> strategy.changePriority(-1));
        assertThrows(DomainException.class, () -> strategy.changeStatus(2));
    }
}
