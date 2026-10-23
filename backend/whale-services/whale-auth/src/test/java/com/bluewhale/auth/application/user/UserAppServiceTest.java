package com.bluewhale.auth.application.user;

import com.bluewhale.auth.application.user.command.AssignUserRolesCommand;
import com.bluewhale.auth.application.user.command.ChangeUserPasswordCommand;
import com.bluewhale.auth.application.user.command.ChangeUserStatusCommand;
import com.bluewhale.auth.application.user.command.CreateUserCommand;
import com.bluewhale.auth.application.user.command.UpdateUserCommand;
import com.bluewhale.auth.application.user.query.UserPageQuery;
import com.bluewhale.auth.domain.model.vo.EncryptedPassword;
import com.bluewhale.auth.domain.model.vo.Username;
import com.bluewhale.auth.domain.role.repository.RoleRepository;
import com.bluewhale.auth.domain.user.aggregate.User;
import com.bluewhale.auth.domain.user.repository.UserRepository;
import com.bluewhale.auth.security.PermissionCache;
import com.bluewhale.auth.vo.UserVO;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link UserAppService} 单元测试：用户分页/详情/增删改、启停与角色分配（AUTH-17 / T-DDD-9）。
 *
 * <p>纯 Mockito 单测，不启动 Spring 上下文、不连库；校验文案与旧 {@code SysUserServiceImpl} 一致。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class UserAppServiceTest {

    private static final long USER_ID = 2L;
    private static final String CIPHER = "$2a$10$abcdefghijklmnopqrstuv";
    private static final String NEW_CIPHER = "$2a$10$newencodedciphervalue";
    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 1, 1, 9, 0, 0);
    private static final LocalDateTime UPDATED = LocalDateTime.of(2026, 1, 2, 9, 0, 0);

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PermissionCache permissionCache;

    @InjectMocks
    private UserAppService userAppService;

    private static User user(String nickname) {
        return User.reconstitute(USER_ID, Username.of("alice_01"), EncryptedPassword.of(CIPHER), nickname,
                "13800000000", "a@b.com", "avatar.png", 3L, User.STATUS_ENABLED,
                LocalDateTime.of(2026, 1, 1, 8, 0, 0), CREATED, UPDATED);
    }

    @Test
    @DisplayName("page：透传分页条件并逐条映射为 UserVO")
    void shouldPageUsers() {
        when(userRepository.page(1L, 10L, "ali"))
                .thenReturn(new UserRepository.UserPage(List.of(user("Alice")), 1L));

        PageResult<UserVO> result = userAppService.page(new UserPageQuery(1L, 10L, "ali"));

        assertEquals(1, result.getRecords().size());
        assertEquals(1L, result.getTotal());
        assertEquals(1L, result.getCurrent());
        assertEquals(10L, result.getSize());
        assertEquals("alice_01", result.getRecords().get(0).getUsername());
    }

    @Test
    @DisplayName("getDetail：命中时返回用户视图对象")
    void shouldGetDetailWhenExists() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user("Alice")));

        UserVO vo = userAppService.getDetail(USER_ID);

        assertEquals(USER_ID, vo.getId());
        assertEquals("Alice", vo.getNickname());
    }

    @Test
    @DisplayName("getDetail：不存在时抛 BizException(NOT_FOUND)")
    void shouldRejectGetDetailWhenMissing() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class, () -> userAppService.getDetail(USER_ID));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
        assertEquals(ErrorCode.NOT_FOUND.getMessage(), ex.getMessage());
    }

    @Test
    @DisplayName("create：用户名已存在时抛 BizException 且不落库、不加密")
    void shouldRejectCreateWhenUsernameExists() {
        when(userRepository.existsByUsername("alice_01")).thenReturn(true);

        BizException ex = assertThrows(BizException.class, () -> userAppService.create(
                new CreateUserCommand("alice_01", "pwd123", "Alice", null, null, null, null, null)));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
        assertEquals("用户名已存在", ex.getMessage());
        verify(userRepository, never()).save(any(User.class));
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("create：用户名可用时加密口令并落库，默认启用")
    void shouldCreateUserWhenUsernameAvailable() {
        when(userRepository.existsByUsername("alice_01")).thenReturn(false);
        when(passwordEncoder.encode("pwd123")).thenReturn(NEW_CIPHER);

        UserVO vo = userAppService.create(
                new CreateUserCommand("alice_01", "pwd123", "Alice", "138", "a@b.com", "avatar.png", 3L, null));

        assertEquals("alice_01", vo.getUsername());
        assertEquals("Alice", vo.getNickname());
        assertEquals("avatar.png", vo.getAvatar());
        assertEquals(User.STATUS_ENABLED, vo.getStatus());
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("update：口令非空时重新加密，资料字段全量覆盖")
    void shouldReencodePasswordAndApplyProfileOnUpdate() {
        User stored = user("旧昵称");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(stored));
        when(passwordEncoder.encode("newpwd123")).thenReturn(NEW_CIPHER);

        UserVO vo = userAppService.update(USER_ID, new UpdateUserCommand("newpwd123", "新昵称",
                "13900000000", "n@b.com", "new-avatar.png", 4L, User.STATUS_DISABLED));

        assertEquals("新昵称", vo.getNickname());
        assertEquals("13900000000", vo.getPhone());
        assertEquals("new-avatar.png", vo.getAvatar());
        assertEquals(4L, vo.getDeptId());
        assertEquals(User.STATUS_DISABLED, vo.getStatus());
        assertEquals(NEW_CIPHER, stored.getPassword().value());
        verify(userRepository).save(stored);
    }

    @Test
    @DisplayName("changePassword：明文经 BCrypt 加密后以密文落库")
    void shouldChangePasswordWithEncodedCipher() {
        User stored = user("Alice");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(stored));
        when(passwordEncoder.encode("resetpwd")).thenReturn(NEW_CIPHER);

        userAppService.changePassword(USER_ID, new ChangeUserPasswordCommand("resetpwd"));

        assertEquals(NEW_CIPHER, stored.getPassword().value());
        verify(userRepository).save(stored);
    }

    @Test
    @DisplayName("changeStatus：非法状态抛 BizException 且不落库、不失效缓存")
    void shouldRejectIllegalStatus() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user("Alice")));

        BizException ex = assertThrows(BizException.class,
                () -> userAppService.changeStatus(USER_ID, new ChangeUserStatusCommand(9)));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
        assertEquals("状态值非法，仅支持 1（启用）/ 0（停用）", ex.getMessage());
        verify(userRepository, never()).save(any(User.class));
        verify(permissionCache, never()).evictUser(any());
    }

    @Test
    @DisplayName("changeStatus：合法状态落库并失效用户权限缓存")
    void shouldChangeStatusAndEvictUserCache() {
        User stored = user("Alice");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(stored));

        userAppService.changeStatus(USER_ID, new ChangeUserStatusCommand(User.STATUS_DISABLED));

        assertEquals(User.STATUS_DISABLED, stored.getStatus());
        verify(userRepository).save(stored);
        verify(permissionCache).evictUser(USER_ID);
    }

    @Test
    @DisplayName("delete：删除用户并失效其权限缓存")
    void shouldDeleteUserAndEvictUserCache() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user("Alice")));

        userAppService.delete(USER_ID);

        verify(userRepository).deleteById(USER_ID);
        verify(permissionCache).evictUser(USER_ID);
    }

    @Test
    @DisplayName("assignRoles：存在无效角色 ID 时抛 BizException 且不写关联、不失效缓存")
    void shouldRejectAssignRolesWhenRoleInvalid() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user("Alice")));
        List<Long> roleIds = List.of(1L, 2L);
        when(roleRepository.countByIds(anyList())).thenReturn(1L);

        BizException ex = assertThrows(BizException.class,
                () -> userAppService.assignRoles(USER_ID, new AssignUserRolesCommand(roleIds)));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
        assertEquals("存在无效的角色ID", ex.getMessage());
        verify(userRepository, never()).replaceRoles(any(), anyList());
        verify(permissionCache, never()).evictUser(any());
    }

    @Test
    @DisplayName("assignRoles：角色全部有效时重建关联并失效用户权限缓存")
    void shouldAssignRolesAndEvictUserCache() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user("Alice")));
        List<Long> roleIds = List.of(1L, 2L);
        when(roleRepository.countByIds(anyList())).thenReturn(2L);

        userAppService.assignRoles(USER_ID, new AssignUserRolesCommand(roleIds));

        verify(userRepository).replaceRoles(USER_ID, roleIds);
        verify(permissionCache).evictUser(USER_ID);
    }

    @Test
    @DisplayName("listRoleIds：委托仓储返回角色 ID 列表")
    void shouldListRoleIds() {
        when(userRepository.listRoleIds(USER_ID)).thenReturn(List.of(1L, 2L));

        List<Long> roleIds = userAppService.listRoleIds(USER_ID);

        assertEquals(List.of(1L, 2L), roleIds);
    }
}
