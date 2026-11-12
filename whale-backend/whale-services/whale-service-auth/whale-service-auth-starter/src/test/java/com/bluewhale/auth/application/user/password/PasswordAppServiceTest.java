package com.bluewhale.auth.application.user.password;

import com.bluewhale.auth.application.model.AuthenticatedUser;
import com.bluewhale.auth.application.port.CurrentUserProvider;
import com.bluewhale.auth.application.event.DomainEventPublisher;
import com.bluewhale.auth.application.user.port.PasswordHasher;
import com.bluewhale.auth.domain.event.AuthDomainEvent.ChangeType;
import com.bluewhale.auth.domain.event.UserDomainEvent;
import com.bluewhale.auth.domain.model.vo.EncryptedPassword;
import com.bluewhale.auth.domain.model.vo.Username;
import com.bluewhale.auth.domain.user.aggregate.User;
import com.bluewhale.auth.domain.user.repository.UserRepository;
import com.bluewhale.common.core.exception.BizException;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PasswordAppService} 单元测试（REQ-SEC-023）：自助改密用例。
 *
 * <p>覆盖用户不存在、旧口令错误、新口令强度/复用校验、改密后清除强制改密标记并发布撤销令牌事件。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class PasswordAppServiceTest {

    private static final long TENANT_ID = 1L;
    private static final long USER_ID = 2L;
    private static final String OLD_RAW = "Old#Pass1";
    private static final String CURRENT_CIPHER = "$2a$10$currentciphervalue";
    private static final String NEW_CIPHER = "$2a$10$newencodedciphervalue";
    private static final String NEW_RAW = "New#Pass2026";

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordHasher passwordHasher;

    @Mock
    private PasswordPolicyService passwordPolicyService;

    @Mock
    private DomainEventPublisher domainEventPublisher;

    @InjectMocks
    private PasswordAppService service;

    /**
     * 构造强制改密状态的当前用户与其账号
     *
     * @return 账号聚合根
     */
    private static User forcedUser() {
        LocalDateTime time = LocalDateTime.of(2026, 1, 1, 0, 0);
        return User.reconstitute(USER_ID, TENANT_ID, Username.of("alice_01"), EncryptedPassword.of(CURRENT_CIPHER),
                "Alice", null, null, null, null, User.STATUS_ENABLED, 1, time, time, time, time);
    }

    private void givenCurrentUser() {
        when(currentUserProvider.current()).thenReturn(new AuthenticatedUser(TENANT_ID, USER_ID, "alice_01", "Alice",
                List.of("tenant_admin"), true, true));
    }

    @Test
    @DisplayName("改密成功：清除强制改密标记、写入历史并发布 PASSWORD_CHANGED 事件")
    void shouldChangeOwnPassword() {
        givenCurrentUser();
        User user = forcedUser();
        when(userRepository.findByIdAcrossTenants(USER_ID)).thenReturn(Optional.of(user));
        when(passwordHasher.matches(OLD_RAW, CURRENT_CIPHER)).thenReturn(true);
        when(passwordPolicyService.encode(NEW_RAW)).thenReturn(NEW_CIPHER);

        service.changeOwnPassword(new ChangeOwnPasswordCommand(OLD_RAW, NEW_RAW));

        assertFalse(user.isMustChangePassword());
        assertEquals(NEW_CIPHER, user.getPassword().value());
        verify(passwordPolicyService).validate(NEW_RAW, "alice_01");
        verify(passwordPolicyService).ensureNotReused(TENANT_ID, USER_ID, CURRENT_CIPHER, NEW_RAW);
        verify(passwordPolicyService).recordHistory(TENANT_ID, USER_ID, NEW_CIPHER);
        verify(userRepository).save(user);
        ArgumentCaptor<UserDomainEvent> event = ArgumentCaptor.forClass(UserDomainEvent.class);
        verify(domainEventPublisher).publish(event.capture());
        assertEquals(ChangeType.PASSWORD_CHANGED, event.getValue().changeType());
        assertEquals(USER_ID, event.getValue().aggregateId());
    }

    @Test
    @DisplayName("旧口令错误：统一提示「原口令不正确」，不写库不发事件")
    void shouldRejectWrongOldPassword() {
        givenCurrentUser();
        when(userRepository.findByIdAcrossTenants(USER_ID)).thenReturn(Optional.of(forcedUser()));
        when(passwordHasher.matches(OLD_RAW, CURRENT_CIPHER)).thenReturn(false);

        BizException ex = assertThrows(BizException.class,
                () -> service.changeOwnPassword(new ChangeOwnPasswordCommand(OLD_RAW, NEW_RAW)));

        assertEquals("原口令不正确", ex.getMessage());
        verify(userRepository, never()).save(any());
        verify(domainEventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("用户不存在：抛出 404 业务异常")
    void shouldRejectMissingUser() {
        givenCurrentUser();
        when(userRepository.findByIdAcrossTenants(USER_ID)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class,
                () -> service.changeOwnPassword(new ChangeOwnPasswordCommand(OLD_RAW, NEW_RAW)));

        assertEquals(404, ex.getCode());
    }
}
