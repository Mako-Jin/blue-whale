package com.bluewhale.auth.domain.dept.aggregate;

import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link Dept} 聚合不变量与行为单元测试（REQ-ARCH-001 / T-DDD-7）。
 *
 * @author BlueWhale
 */
class DeptTest {

    private static final int PARAM_ERROR_CODE = 400;

    @Test
    @DisplayName("create：合法构造，默认父节点 0、默认排序 0 并回填各字段")
    void shouldCreateWithDefaults() {
        Dept dept = Dept.create(1L, null, "总部", null, "张三");

        assertNull(dept.getId());
        assertEquals(Dept.ROOT_PARENT_ID, dept.getParentId());
        assertEquals("总部", dept.getDeptName());
        assertEquals(0, dept.getSort());
        assertEquals("张三", dept.getLeader());
    }

    @Test
    @DisplayName("create：显式父节点与排序生效")
    void shouldCreateWithExplicitValues() {
        Dept dept = Dept.create(1L, 3L, "研发部", 5, null);

        assertEquals(3L, dept.getParentId());
        assertEquals(5, dept.getSort());
    }

    @Test
    @DisplayName("不变量①：部门名称为空抛参数错误")
    void shouldRejectBlankDeptName() {
        BizException ex = assertThrows(BizException.class, () -> Dept.create(1L, 0L, " ", null, null));
        assertEquals(PARAM_ERROR_CODE, ex.getCode());
    }

    @Test
    @DisplayName("不变量②：reconstitute 父部门为空时归一化为顶级")
    void shouldNormalizeNullParentToRoot() {
        LocalDateTime created = LocalDateTime.of(2026, 1, 1, 0, 0);

        Dept dept = Dept.reconstitute(7L, 1L, null, "总部", null, "李四", created, created);

        assertEquals(7L, dept.getId());
        assertEquals(Dept.ROOT_PARENT_ID, dept.getParentId());
        assertEquals(0, dept.getSort());
        assertEquals(created, dept.getCreateTime());
    }

    @Test
    @DisplayName("行为：moveTo/rename/changeSort/changeLeader 各自生效")
    void shouldApplyChanges() {
        Dept dept = Dept.create(1L, 0L, "旧名", 1, "旧负责人");

        dept.moveTo(3L);
        dept.rename("新名");
        dept.changeSort(5);
        dept.changeLeader("新负责人");

        assertEquals(3L, dept.getParentId());
        assertEquals("新名", dept.getDeptName());
        assertEquals(5, dept.getSort());
        assertEquals("新负责人", dept.getLeader());
    }

    @Test
    @DisplayName("行为：moveTo/rename 非法入参抛参数错误")
    void shouldRejectIllegalChanges() {
        Dept dept = Dept.create(1L, 0L, "总部", 1, null);

        assertThrows(BizException.class, () -> dept.moveTo(null));
        assertThrows(BizException.class, () -> dept.rename(""));
    }
}
