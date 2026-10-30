package com.bluewhale.auth.application.user;

import com.bluewhale.auth.application.event.DomainEventPublisher;
import com.bluewhale.auth.application.tenant.TenantMembershipAppService;
import com.bluewhale.auth.application.user.command.AssignUserRolesCommand;
import com.bluewhale.auth.application.user.command.ChangeUserPasswordCommand;
import com.bluewhale.auth.application.user.command.ChangeUserStatusCommand;
import com.bluewhale.auth.application.user.command.CreateUserCommand;
import com.bluewhale.auth.application.user.command.UpdateUserCommand;
import com.bluewhale.auth.application.user.query.UserPageQuery;
import com.bluewhale.auth.domain.event.AuthDomainEvent.ChangeType;
import com.bluewhale.auth.domain.event.UserDomainEvent;
import com.bluewhale.auth.domain.model.vo.EncryptedPassword;
import com.bluewhale.auth.domain.model.vo.Username;
import com.bluewhale.auth.domain.role.repository.RoleRepository;
import com.bluewhale.auth.domain.user.aggregate.User;
import com.bluewhale.auth.domain.user.repository.UserRepository;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.auth.vo.UserVO;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    private DomainEventPublisher eventPublisher;

    @Mock
    private TenantMembershipAppService tenantMembershipAppService;

    @InjectMocks
    private UserAppService userAppService;

    @BeforeEach
    void bindTenant() {
        TenantContext.set(1L);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private static User user(String nickname) {
        return User.reconstitute(USER_ID, 1L, Username.of("alice_01"), EncryptedPassword.of(CIPHER), nickname,
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
        when(userRepository.existsByUsername(1L, "alice_01")).thenReturn(true);

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
        when(userRepository.existsByUsername(1L, "alice_01")).thenReturn(false);
        when(passwordEncoder.encode("pwd123")).thenReturn(NEW_CIPHER);
        when(userRepository.save(any(User.class))).thenReturn(User.reconstitute(USER_ID, 1L,
                Username.of("alice_01"), EncryptedPassword.of(NEW_CIPHER), "Alice", "138", "a@b.com",
                "avatar.png", 3L, User.STATUS_ENABLED, null, CREATED, UPDATED));

        UserVO vo = userAppService.create(
                new CreateUserCommand("alice_01", "pwd123", "Alice", "138", "a@b.com", "avatar.png", 3L, null));

        assertEquals("alice_01", vo.getUsername());
        assertEquals("Alice", vo.getNickname());
        assertEquals("avatar.png", vo.getAvatar());
        assertEquals(User.STATUS_ENABLED, vo.getStatus());
        verify(userRepository).save(any(User.class));
        // 一人多租户：新建账号必须写入首页租户关系
        verify(tenantMembershipAppService).ensureHomeMembership(USER_ID, 1L);
        verify(eventPublisher).publish(new UserDomainEvent(USER_ID, ChangeType.CREATED));
    }

    @Test
    @DisplayName("update：停用优先发布 USER_DISABLED，口令非空时重新加密")
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
        verify(eventPublisher).publish(new UserDomainEvent(USER_ID, ChangeType.USER_DISABLED));
    }

    @Test
    @DisplayName("update：仅资料变更发布 UPDATED，不触发缓存/令牌动作")
    void shouldPublishUpdatedWhenOnlyProfileChanged() {
        User stored = user("旧昵称");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(stored));

        userAppService.update(USER_ID, new UpdateUserCommand(null, "新昵称",
                "13900000000", "n@b.com", "new-avatar.png", 4L, User.STATUS_ENABLED));

        verify(eventPublisher).publish(new UserDomainEvent(USER_ID, ChangeType.UPDATED));
    }

    @Test
    @DisplayName("update：仅改密发布 PASSWORD_CHANGED，撤销存量令牌")
    void shouldPublishPasswordChangedWhenOnlyPasswordChanged() {
        User stored = user("Alice");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(stored));
        when(passwordEncoder.encode("newpwd123")).thenReturn(NEW_CIPHER);

        userAppService.update(USER_ID, new UpdateUserCommand("newpwd123", "Alice",
                "13800000000", "a@b.com", "avatar.png", 3L, null));

        verify(eventPublisher).publish(new UserDomainEvent(USER_ID, ChangeType.PASSWORD_CHANGED));
    }

    @Test
    @DisplayName("update：启用状态变化发布 STATUS_CHANGED，失效权限缓存")
    void shouldPublishStatusChangedWhenEnabling() {
        User stored = User.reconstitute(USER_ID, 1L, Username.of("alice_01"),
                EncryptedPassword.of(CIPHER), "Alice", "13800000000", "a@b.com", "avatar.png", 3L,
                User.STATUS_DISABLED, LocalDateTime.of(2026, 1, 1, 8, 0, 0), CREATED, UPDATED);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(stored));

        userAppService.update(USER_ID, new UpdateUserCommand(null, "Alice",
                "13800000000", "a@b.com", "avatar.png", 3L, User.STATUS_ENABLED));

        verify(eventPublisher).publish(new UserDomainEvent(USER_ID, ChangeType.STATUS_CHANGED));
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
        verify(eventPublisher).publish(new UserDomainEvent(USER_ID, ChangeType.PASSWORD_CHANGED));
    }

    @Test
    @DisplayName("changePassword：改密成功后清除强制首登改密标记（T-03）")
    void shouldClearMustChangePasswordOnChangePassword() {
        User stored = User.reconstitute(USER_ID, 1L, Username.of("alice_01"), EncryptedPassword.of(CIPHER),
                "Alice", "13800000000", "a@b.com", "avatar.png", 3L, User.STATUS_ENABLED, 1,
                LocalDateTime.of(2026, 1, 1, 8, 0, 0), CREATED, UPDATED);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(stored));
        when(passwordEncoder.encode("resetpwd")).thenReturn(NEW_CIPHER);
        assertTrue(stored.isMustChangePassword());

        userAppService.changePassword(USER_ID, new ChangeUserPasswordCommand("resetpwd"));

        assertFalse(stored.isMustChangePassword());
        verify(userRepository).save(stored);
    }

    @Test
    @DisplayName("update：重置口令成功后同样清除强制首登改密标记（T-03）")
    void shouldClearMustChangePasswordOnUpdatePassword() {
        User stored = User.reconstitute(USER_ID, 1L, Username.of("alice_01"), EncryptedPassword.of(CIPHER),
                "Alice", "13800000000", "a@b.com", "avatar.png", 3L, User.STATUS_ENABLED, 1,
                LocalDateTime.of(2026, 1, 1, 8, 0, 0), CREATED, UPDATED);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(stored));
        when(passwordEncoder.encode("newpwd123")).thenReturn(NEW_CIPHER);

        userAppService.update(USER_ID, new UpdateUserCommand("newpwd123", "Alice",
                "13800000000", "a@b.com", "avatar.png", 3L, null));

        assertFalse(stored.isMustChangePassword());
        assertEquals(NEW_CIPHER, stored.getPassword().value());
        verify(userRepository).save(stored);
    }

    @Test
    @DisplayName("changeStatus：非法状态抛 BizException 且不落库、不发布事件")
    void shouldRejectIllegalStatus() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user("Alice")));

        BizException ex = assertThrows(BizException.class,
                () -> userAppService.changeStatus(USER_ID, new ChangeUserStatusCommand(9)));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
        assertEquals("状态值非法，仅支持 1（启用）/ 0（停用）", ex.getMessage());
        verify(userRepository, never()).save(any(User.class));
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("changeStatus：停用落库并发布 USER_DISABLED 事件")
    void shouldChangeStatusAndPublishEvent() {
        User stored = user("Alice");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(stored));

        userAppService.changeStatus(USER_ID, new ChangeUserStatusCommand(User.STATUS_DISABLED));

        assertEquals(User.STATUS_DISABLED, stored.getStatus());
        verify(userRepository).save(stored);
        verify(eventPublisher).publish(new UserDomainEvent(USER_ID, ChangeType.USER_DISABLED));
    }

    @Test
    @DisplayName("changeStatus：启用落库并发布 STATUS_CHANGED 事件")
    void shouldPublishStatusChangedWhenEnablingStatus() {
        User stored = User.reconstitute(USER_ID, 1L, Username.of("alice_01"),
                EncryptedPassword.of(CIPHER), "Alice", "13800000000", "a@b.com", "avatar.png", 3L,
                User.STATUS_DISABLED, LocalDateTime.of(2026, 1, 1, 8, 0, 0), CREATED, UPDATED);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(stored));

        userAppService.changeStatus(USER_ID, new ChangeUserStatusCommand(User.STATUS_ENABLED));

        assertEquals(User.STATUS_ENABLED, stored.getStatus());
        verify(eventPublisher).publish(new UserDomainEvent(USER_ID, ChangeType.STATUS_CHANGED));
    }

    @Test
    @DisplayName("delete：删除用户并发布 DELETED 事件")
    void shouldDeleteUserAndPublishEvent() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user("Alice")));

        userAppService.delete(USER_ID);

        verify(userRepository).deleteById(USER_ID);
        verify(eventPublisher).publish(new UserDomainEvent(USER_ID, ChangeType.DELETED));
    }

    @Test
    @DisplayName("assignRoles：存在无效角色 ID 时抛 BizException 且不写关联、不发布事件")
    void shouldRejectAssignRolesWhenRoleInvalid() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user("Alice")));
        List<Long> roleIds = List.of(1L, 2L);
        when(roleRepository.countByIds(anyList())).thenReturn(1L);

        BizException ex = assertThrows(BizException.class,
                () -> userAppService.assignRoles(USER_ID, new AssignUserRolesCommand(roleIds)));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
        assertEquals("存在无效的角色ID", ex.getMessage());
        verify(userRepository, never()).replaceRoles(any(), anyList());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("assignRoles：角色全部有效时重建关联并发布 ROLES_ASSIGNED 事件")
    void shouldAssignRolesAndPublishEvent() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user("Alice")));
        List<Long> roleIds = List.of(1L, 2L);
        when(roleRepository.countByIds(anyList())).thenReturn(2L);

        userAppService.assignRoles(USER_ID, new AssignUserRolesCommand(roleIds));

        verify(userRepository).replaceRoles(USER_ID, roleIds);
        verify(eventPublisher).publish(new UserDomainEvent(USER_ID, ChangeType.ROLES_ASSIGNED));
    }

    @Test
    @DisplayName("listRoleIds：委托仓储返回角色 ID 列表")
    void shouldListRoleIds() {
        when(userRepository.listRoleIds(USER_ID)).thenReturn(List.of(1L, 2L));

        List<Long> roleIds = userAppService.listRoleIds(USER_ID);

        assertEquals(List.of(1L, 2L), roleIds);
    }
}
