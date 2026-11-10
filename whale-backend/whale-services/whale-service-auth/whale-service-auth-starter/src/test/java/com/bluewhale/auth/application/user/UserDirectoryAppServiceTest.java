package com.bluewhale.auth.application.user;

import com.bluewhale.auth.domain.dept.aggregate.Dept;
import com.bluewhale.auth.domain.dept.repository.DeptRepository;
import com.bluewhale.auth.domain.model.vo.EncryptedPassword;
import com.bluewhale.auth.domain.model.vo.Username;
import com.bluewhale.auth.domain.user.aggregate.User;
import com.bluewhale.auth.domain.user.repository.UserRepository;
import com.bluewhale.auth.vo.UserDirectoryVO;
import com.bluewhale.common.core.result.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link UserDirectoryAppService} 单元测试：通讯录分页脱敏、部门名称批量补全、按角色查询与空角色降级。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class UserDirectoryAppServiceTest {

    private static final String BCRYPT = "$2a$10$abcdefghijklmnopqrstuv";
    private static final long TENANT_ID = 1L;
    private static final long DEPT_ID = 10L;
    private static final int STATUS_ENABLED = 1;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 1, 9, 0, 0);

    @Mock
    private UserRepository userRepository;

    @Mock
    private DeptRepository deptRepository;

    @InjectMocks
    private UserDirectoryAppService userDirectoryAppService;

    /**
     * 构造启用用户聚合（用于通讯录断言）
     *
     * @param id 用户 ID
     * @param username 用户名
     * @param deptId 部门 ID（可空）
     * @param phone 手机号
     * @param email 邮箱
     * @return 用户聚合根
     */
    private static User enabledUser(long id, String username, Long deptId, String phone, String email) {
        return User.reconstitute(id, TENANT_ID, Username.of(username), EncryptedPassword.of(BCRYPT),
                "昵称" + id, phone, email, deptId, STATUS_ENABLED, NOW, NOW);
    }

    /**
     * 构造部门聚合（用于部门名称批量补全）
     *
     * @param id 部门 ID
     * @param deptName 部门名称
     * @return 部门聚合根
     */
    private static Dept dept(long id, String deptName) {
        return Dept.reconstitute(id, TENANT_ID, Dept.ROOT_PARENT_ID, deptName, 1, "张三", NOW, NOW);
    }

    @Test
    @DisplayName("directory：分页返回并正确脱敏手机号/邮箱，部门名称批量补全")
    void shouldMaskSensitiveFieldsInDirectory() {
        when(userRepository.directoryPage(1L, 20L, DEPT_ID, "ali"))
                .thenReturn(new UserRepository.UserPage(List.of(
                        enabledUser(1L, "alice", DEPT_ID, "13800000001", "alice@example.com"),
                        enabledUser(2L, "bob", null, "123", "no-at-sign")), 2L));
        when(deptRepository.findAll()).thenReturn(List.of(dept(DEPT_ID, "研发部")));

        PageResult<UserDirectoryVO> page = userDirectoryAppService.directory(DEPT_ID, "ali", 1L, 20L);

        assertEquals(2, page.getRecords().size());
        assertEquals(1L, page.getCurrent());
        assertEquals(20L, page.getSize());
        assertEquals(2L, page.getTotal());
        UserDirectoryVO first = page.getRecords().get(0);
        assertEquals("138****0001", first.getPhone());
        assertEquals("a***@example.com", first.getEmail());
        assertEquals("研发部", first.getDeptName());
        UserDirectoryVO second = page.getRecords().get(1);
        assertEquals("***", second.getPhone());
        assertEquals("***", second.getEmail());
        verify(deptRepository).findAll();
    }

    @Test
    @DisplayName("directory：每页条数超过 200 时按 200 截断")
    void shouldCapPageSizeAtTwoHundred() {
        when(userRepository.directoryPage(1L, 200L, null, null))
                .thenReturn(new UserRepository.UserPage(List.of(), 0L));

        userDirectoryAppService.directory(null, null, 1L, 500L);

        verify(userRepository).directoryPage(1L, 200L, null, null);
    }

    @Test
    @DisplayName("byRole：角色不存在时返回空列表")
    void shouldReturnEmptyWhenRoleMissing() {
        when(userRepository.findByRoleCode("missing_role", 200)).thenReturn(List.of());

        List<UserDirectoryVO> users = userDirectoryAppService.byRole("missing_role");

        assertTrue(users.isEmpty());
    }

    @Test
    @DisplayName("byRole：只返回启用用户且字段脱敏")
    void shouldReturnOnlyEnabledUsersByRole() {
        when(userRepository.findByRoleCode("tenant_admin", 200))
                .thenReturn(List.of(enabledUser(1L, "alice", DEPT_ID, "13800000001", "alice@example.com")));
        when(deptRepository.findAll()).thenReturn(List.of(dept(DEPT_ID, "研发部")));

        List<UserDirectoryVO> users = userDirectoryAppService.byRole("tenant_admin");

        assertEquals(1, users.size());
        assertEquals("138****0001", users.get(0).getPhone());
        assertEquals("a***@example.com", users.get(0).getEmail());
        assertEquals("研发部", users.get(0).getDeptName());
        verify(deptRepository).findAll();
    }
}
