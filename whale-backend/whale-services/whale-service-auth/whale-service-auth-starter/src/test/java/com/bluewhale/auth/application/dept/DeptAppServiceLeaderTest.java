package com.bluewhale.auth.application.dept;

import com.bluewhale.auth.application.event.DomainEventPublisher;
import com.bluewhale.auth.domain.dept.aggregate.Dept;
import com.bluewhale.auth.domain.dept.repository.DeptRepository;
import com.bluewhale.auth.vo.DeptLeaderVO;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * {@link DeptAppService#leader(Long)} 单元测试：负责人命中、部门不存在/跨租户 404、负责人用户 ID 为空降级。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class DeptAppServiceLeaderTest {

    private static final long DEPT_ID = 3L;
    private static final long LEADER_USER_ID = 99L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 1, 9, 0, 0);

    @Mock
    private DeptRepository deptRepository;

    @Mock
    private DomainEventPublisher eventPublisher;

    @InjectMocks
    private DeptAppService deptAppService;

    @Test
    @DisplayName("leader：命中部门时返回负责人用户 ID 与部门名称")
    void shouldReturnLeaderUserIdWhenDeptExists() {
        Dept dept = Dept.reconstitute(DEPT_ID, 1L, Dept.ROOT_PARENT_ID, "研发部", 1, "张三",
                LEADER_USER_ID, NOW, NOW);
        when(deptRepository.findById(DEPT_ID)).thenReturn(Optional.of(dept));

        DeptLeaderVO vo = deptAppService.leader(DEPT_ID);

        assertEquals(DEPT_ID, vo.getDeptId());
        assertEquals("研发部", vo.getDeptName());
        assertEquals(LEADER_USER_ID, vo.getLeaderUserId());
    }

    @Test
    @DisplayName("leader：部门不存在或跨租户时抛 BizException(NOT_FOUND)")
    void shouldRejectWhenDeptMissing() {
        when(deptRepository.findById(DEPT_ID)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class, () -> deptAppService.leader(DEPT_ID));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("leader：负责人用户 ID 未维护时返回 null 且不抛错")
    void shouldReturnNullLeaderWithoutError() {
        Dept dept = Dept.reconstitute(DEPT_ID, 1L, Dept.ROOT_PARENT_ID, "研发部", 1, "张三",
                null, NOW, NOW);
        when(deptRepository.findById(DEPT_ID)).thenReturn(Optional.of(dept));

        DeptLeaderVO vo = deptAppService.leader(DEPT_ID);

        assertNull(vo.getLeaderUserId());
    }
}
