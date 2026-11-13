package com.bluewhale.auth.domain.aggregate;

import com.bluewhale.auth.domain.model.vo.GroupFilters;
import com.bluewhale.common.domain.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link UserGroupAggregate} 核心不变量单元测试（权限中心扩展 M3）。
 *
 * @author BlueWhale
 */
class UserGroupAggregateTest {

    @Test
    @DisplayName("新建静态组：默认启用、父组缺省顶级")
    void shouldCreateStaticGroup() {
        UserGroupAggregate group = UserGroupAggregate.createStatic(1L, "G001", "研发组", null, "描述", null);

        assertThat(group.getCategory()).isEqualTo(UserGroupAggregate.CATEGORY_STATIC);
        assertThat(group.getParentId()).isZero();
        assertThat(group.isEnabled()).isTrue();
        assertThat(group.getFilters()).isNull();
    }

    @Test
    @DisplayName("新建动态组必须提供过滤规则，静态组拒绝承载过滤规则")
    void shouldValidateFiltersByCategory() {
        GroupFilters filters = GroupFilters.of(List.of(
                new GroupFilters.Condition("deptId", "eq", 10)));

        assertThrows(DomainException.class,
                () -> UserGroupAggregate.createDynamic(1L, "G002", "动态组", null, null, null, null));
        UserGroupAggregate dynamic = UserGroupAggregate.createDynamic(
                1L, "G002", "动态组", null, filters, null, null);
        assertThat(dynamic.isDynamic()).isTrue();
        assertThat(dynamic.getFilters()).isNotNull();

        assertThrows(DomainException.class,
                () -> UserGroupAggregate.createStatic(1L, "G003", "静态组", null, null, null).changeFilters(filters));
    }

    @Test
    @DisplayName("过滤规则操作符限定 eq/ne/in/like，非法操作符拒绝")
    void shouldRejectInvalidFilterOp() {
        assertThrows(DomainException.class, () -> GroupFilters.of(List.of(
                new GroupFilters.Condition("deptId", "regex", ".*"))));
        assertThrows(DomainException.class, () -> GroupFilters.of(List.of(
                new GroupFilters.Condition(" ", "eq", 1))));
    }

    @Test
    @DisplayName("挂载父组拒绝自指（组树不得成环）")
    void shouldRejectSelfParent() {
        UserGroupAggregate group = UserGroupAggregate.reconstitute(
                5L, 1L, "G001", "研发组", 0L, UserGroupAggregate.CATEGORY_STATIC,
                null, null, 0, 1,
                java.time.LocalDateTime.now(), java.time.LocalDateTime.now());

        assertThrows(DomainException.class, () -> group.changeParent(5L));
        group.changeParent(9L);
        assertThat(group.getParentId()).isEqualTo(9L);
    }

    @Test
    @DisplayName("静态组成员归一化：去重去空，动态组拒绝手工维护成员")
    void shouldNormalizeMembersOnlyForStaticGroup() {
        UserGroupAggregate staticGroup = UserGroupAggregate.createStatic(1L, "G001", "研发组", null, null, null);

        List<Long> normalized = staticGroup.normalizeMembers(Arrays.asList(1L, null, 2L, 1L, 3L));

        assertThat(normalized).containsExactly(1L, 2L, 3L);

        GroupFilters filters = GroupFilters.of(List.of(
                new GroupFilters.Condition("deptId", "eq", 10)));
        UserGroupAggregate dynamicGroup = UserGroupAggregate.createDynamic(
                1L, "G002", "动态组", null, filters, null, null);
        assertThrows(DomainException.class, () -> dynamicGroup.normalizeMembers(List.of(1L, 2L)));
    }

    @Test
    @DisplayName("组成员超过单组上限时拒绝")
    void shouldRejectMembersBeyondLimit() {
        UserGroupAggregate group = UserGroupAggregate.createStatic(1L, "G001", "研发组", null, null, null);
        List<Long> oversized = new java.util.ArrayList<>();
        for (long i = 0; i <= UserGroupAggregate.MAX_MEMBERS_PER_GROUP; i++) {
            oversized.add(i);
        }

        assertThrows(DomainException.class, () -> group.normalizeMembers(oversized));
    }

    @Test
    @DisplayName("编码/名称为空或类别非法时拒绝创建")
    void shouldRejectInvalidBasicFields() {
        assertThrows(DomainException.class,
                () -> UserGroupAggregate.createStatic(1L, " ", "研发组", null, null, null));
        assertThrows(DomainException.class,
                () -> UserGroupAggregate.createStatic(1L, "G001", " ", null, null, null));
        assertThrows(DomainException.class, () -> UserGroupAggregate.reconstitute(
                1L, 1L, "G001", "研发组", 0L, "OTHER", null, null, 0, 1,
                java.time.LocalDateTime.now(), java.time.LocalDateTime.now()));
    }

    @Test
    @DisplayName("重命名、状态与描述变更")
    void shouldRenameAndChangeStatus() {
        UserGroupAggregate group = UserGroupAggregate.createStatic(1L, "G001", "研发组", null, null, null);

        group.rename(" 平台研发组 ");
        group.changeStatus(UserGroupAggregate.STATUS_DISABLED);
        group.changeDescription("新描述");

        assertThat(group.getGroupName()).isEqualTo("平台研发组");
        assertThat(group.isEnabled()).isFalse();
        assertThat(group.getDescription()).isEqualTo("新描述");
        assertThrows(DomainException.class, () -> group.rename(" "));
        assertThrows(DomainException.class, () -> group.changeStatus(2));
    }
}
