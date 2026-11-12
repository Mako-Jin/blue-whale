package com.bluewhale.auth.application.dept;

import com.bluewhale.auth.application.model.command.SaveDeptCommand;
import com.bluewhale.auth.application.event.DomainEventPublisher;
import com.bluewhale.auth.domain.aggregate.Dept;
import com.bluewhale.auth.domain.repository.DeptRepository;
import com.bluewhale.auth.domain.event.AuthDomainEvent.ChangeType;
import com.bluewhale.auth.domain.event.DeptDomainEvent;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.auth.application.model.vo.DeptVO;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.bluewhale.auth.application.service.DeptAppService;
import com.bluewhale.auth.application.service.impl.DeptAppServiceImpl;

/**
 * {@link DeptAppService} 单元测试：部门树/详情/增删改与子部门校验（AUTH-17 / T-DDD-9）。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class DeptAppServiceTest {

    private static final long ROOT_ID = 1L;
    private static final long CHILD_ID = 2L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 1, 9, 0, 0);

    @Mock
    private DeptRepository deptRepository;

    @Mock
    private DomainEventPublisher eventPublisher;

    @InjectMocks
    private DeptAppServiceImpl deptAppService;

    @BeforeEach
    void bindTenant() {
        TenantContext.set(1L);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private static Dept dept(Long id, Long parentId, String name, int sort) {
        return Dept.reconstitute(id, 1L, parentId, name, sort, "负责人", NOW, NOW);
    }

    @Test
    @DisplayName("tree：按父子关系组装两级部门树")
    void shouldBuildTree() {
        when(deptRepository.findAll()).thenReturn(List.of(
                dept(ROOT_ID, Dept.ROOT_PARENT_ID, "总部", 1),
                dept(CHILD_ID, ROOT_ID, "研发部", 2)));

        List<DeptVO> tree = deptAppService.tree();

        assertEquals(1, tree.size());
        assertEquals("总部", tree.get(0).getDeptName());
        assertEquals(1, tree.get(0).getChildren().size());
        assertEquals("研发部", tree.get(0).getChildren().get(0).getDeptName());
    }

    @Test
    @DisplayName("getDetail：不存在时抛 BizException(NOT_FOUND)")
    void shouldRejectGetDetailWhenMissing() {
        when(deptRepository.findById(ROOT_ID)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class, () -> deptAppService.getDetail(ROOT_ID));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("create：落库并取默认父节点/排序")
    void shouldCreateWithDefaults() {
        when(deptRepository.save(any(Dept.class))).thenAnswer(inv -> inv.getArgument(0));

        deptAppService.create(new SaveDeptCommand(null, "财务部", null, null));

        ArgumentCaptor<Dept> captor = ArgumentCaptor.forClass(Dept.class);
        verify(deptRepository).save(captor.capture());
        Dept saved = captor.getValue();
        assertEquals(Dept.ROOT_PARENT_ID, saved.getParentId());
        assertEquals(0, saved.getSort());
        assertEquals("财务部", saved.getDeptName());
        verify(eventPublisher).publish(new DeptDomainEvent(null, ChangeType.CREATED));
    }

    @Test
    @DisplayName("update：命中时覆盖字段并落库")
    void shouldUpdateWhenExists() {
        Dept stored = dept(ROOT_ID, Dept.ROOT_PARENT_ID, "旧名", 1);
        when(deptRepository.findById(ROOT_ID)).thenReturn(Optional.of(stored));

        deptAppService.update(ROOT_ID, new SaveDeptCommand(ROOT_ID, "新名", 5, "新负责人"));

        ArgumentCaptor<Dept> captor = ArgumentCaptor.forClass(Dept.class);
        verify(deptRepository).save(captor.capture());
        Dept saved = captor.getValue();
        assertEquals("新名", saved.getDeptName());
        assertEquals(5, saved.getSort());
        assertEquals("新负责人", saved.getLeader());
        verify(eventPublisher).publish(new DeptDomainEvent(ROOT_ID, ChangeType.UPDATED));
    }

    @Test
    @DisplayName("update：不存在时抛 BizException(NOT_FOUND) 且不落库")
    void shouldRejectUpdateWhenMissing() {
        when(deptRepository.findById(ROOT_ID)).thenReturn(Optional.empty());

        assertThrows(BizException.class,
                () -> deptAppService.update(ROOT_ID, new SaveDeptCommand(null, "新名", null, null)));
        verify(deptRepository, never()).save(any(Dept.class));
    }

    @Test
    @DisplayName("delete：存在子部门时抛 BizException 且不删除")
    void shouldRejectDeleteWhenHasChildren() {
        when(deptRepository.countChildren(ROOT_ID)).thenReturn(1L);

        BizException ex = assertThrows(BizException.class, () -> deptAppService.delete(ROOT_ID));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
        assertEquals("存在子部门，不允许删除", ex.getMessage());
        verify(deptRepository, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("delete：无子部门时逻辑删除")
    void shouldDeleteWhenNoChildren() {
        when(deptRepository.countChildren(ROOT_ID)).thenReturn(0L);

        deptAppService.delete(ROOT_ID);

        verify(deptRepository).deleteById(ROOT_ID);
        verify(eventPublisher).publish(new DeptDomainEvent(ROOT_ID, ChangeType.DELETED));
    }
}
