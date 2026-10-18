package com.bluewhale.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.bluewhale.auth.dto.DeptRequest;
import com.bluewhale.auth.entity.SysDept;
import com.bluewhale.auth.mapper.SysDeptMapper;
import com.bluewhale.auth.vo.DeptVO;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SysDeptServiceImpl} 单元测试：部门树组装、详情、增删改与子节点校验（AUTH-23）。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class SysDeptServiceImplTest {

    private static final long ROOT_ID = 1L;
    private static final long CHILD_ID = 2L;
    private static final long TOP_PARENT_ID = 0L;

    @Mock
    private SysDeptMapper deptMapper;

    private SysDeptServiceImpl deptService;

    @BeforeEach
    void setUp() {
        deptService = new SysDeptServiceImpl();
        ReflectionTestUtils.setField(deptService, "baseMapper", deptMapper);
    }

    private static SysDept dept(Long id, Long parentId, String name) {
        SysDept dept = new SysDept();
        dept.setId(id);
        dept.setParentId(parentId);
        dept.setDeptName(name);
        dept.setSort(1);
        return dept;
    }

    private static DeptRequest request(String name) {
        DeptRequest request = new DeptRequest();
        request.setDeptName(name);
        return request;
    }

    @Test
    @DisplayName("tree：按父子关系组装两级部门树")
    void shouldBuildTree() {
        when(deptMapper.selectList(ArgumentMatchers.<Wrapper<SysDept>>any()))
                .thenReturn(List.of(dept(ROOT_ID, TOP_PARENT_ID, "总部"), dept(CHILD_ID, ROOT_ID, "研发部")));

        List<DeptVO> tree = deptService.tree();

        assertEquals(1, tree.size());
        assertEquals("总部", tree.get(0).getDeptName());
        assertEquals(1, tree.get(0).getChildren().size());
        assertEquals("研发部", tree.get(0).getChildren().get(0).getDeptName());
    }

    @Test
    @DisplayName("getDetail：命中时返回部门视图对象")
    void shouldGetDetailWhenExists() {
        when(deptMapper.selectById(ROOT_ID)).thenReturn(dept(ROOT_ID, TOP_PARENT_ID, "总部"));

        DeptVO vo = deptService.getDetail(ROOT_ID);

        assertEquals(ROOT_ID, vo.getId());
        assertEquals("总部", vo.getDeptName());
    }

    @Test
    @DisplayName("getDetail：不存在时抛 BizException(NOT_FOUND)")
    void shouldRejectGetDetailWhenMissing() {
        when(deptMapper.selectById(ROOT_ID)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> deptService.getDetail(ROOT_ID));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("create：落库并取默认父节点/排序")
    void shouldCreateWithDefaults() {
        ArgumentCaptor<SysDept> captor = ArgumentCaptor.forClass(SysDept.class);

        deptService.create(request("财务部"));

        verify(deptMapper).insert(captor.capture());
        assertEquals(TOP_PARENT_ID, captor.getValue().getParentId());
        assertEquals(0, captor.getValue().getSort());
        assertEquals("财务部", captor.getValue().getDeptName());
    }

    @Test
    @DisplayName("update：命中时覆盖字段并落库")
    void shouldUpdateWhenExists() {
        when(deptMapper.selectById(ROOT_ID)).thenReturn(dept(ROOT_ID, TOP_PARENT_ID, "旧名"));
        DeptRequest request = request("新名");
        request.setSort(5);
        request.setParentId(TOP_PARENT_ID);

        deptService.update(ROOT_ID, request);

        verify(deptMapper).updateById(any(SysDept.class));
    }

    @Test
    @DisplayName("update：不存在时抛 BizException(NOT_FOUND)")
    void shouldRejectUpdateWhenMissing() {
        when(deptMapper.selectById(ROOT_ID)).thenReturn(null);

        assertThrows(BizException.class, () -> deptService.update(ROOT_ID, request("新名")));
        verify(deptMapper, never()).updateById(any(SysDept.class));
    }

    @Test
    @DisplayName("delete：无子部门时逻辑删除")
    void shouldDeleteWhenNoChildren() {
        when(deptMapper.selectCount(ArgumentMatchers.<Wrapper<SysDept>>any())).thenReturn(0L);

        deptService.delete(ROOT_ID);

        verify(deptMapper).deleteById(ROOT_ID);
    }

    @Test
    @DisplayName("delete：存在子部门时抛 BizException 且不删除")
    void shouldRejectDeleteWhenHasChildren() {
        when(deptMapper.selectCount(ArgumentMatchers.<Wrapper<SysDept>>any())).thenReturn(1L);

        assertThrows(BizException.class, () -> deptService.delete(ROOT_ID));
        verify(deptMapper, never()).deleteById(anyLong());
    }
}
