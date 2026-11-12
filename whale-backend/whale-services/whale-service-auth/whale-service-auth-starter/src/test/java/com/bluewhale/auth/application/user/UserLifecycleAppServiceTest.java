package com.bluewhale.auth.application.user;

import com.bluewhale.auth.application.event.DomainEventPublisher;
import com.bluewhale.auth.application.model.command.UserLifecycleCommand;
import com.bluewhale.auth.application.service.PasswordPolicyService;
import com.bluewhale.auth.application.service.impl.TemporaryPasswordGenerator;
import com.bluewhale.auth.domain.event.UserDomainEvent;
import com.bluewhale.auth.domain.model.vo.Username;
import com.bluewhale.auth.domain.aggregate.UserAggregate;
import com.bluewhale.auth.domain.repository.UserRepository;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.bluewhale.auth.application.model.vo.UserLifecycleVO;
import com.bluewhale.auth.application.service.UserAppService;
import com.bluewhale.auth.application.service.UserLifecycleAppService;
import com.bluewhale.auth.application.service.impl.UserLifecycleAppServiceImpl;

/**
 * {@link UserLifecycleAppService} 人员生命周期动作测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserLifecycleAppServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserAppService userAppService;

    @Mock
    private TemporaryPasswordGenerator temporaryPasswordGenerator;

    @Mock
    private PasswordPolicyService passwordPolicyService;

    @Mock
    private DomainEventPublisher domainEventPublisher;

    private UserLifecycleAppService userLifecycleAppService;

    @BeforeEach
    void setUp() {
        userLifecycleAppService = new UserLifecycleAppServiceImpl(userRepository, userAppService,
                temporaryPasswordGenerator, passwordPolicyService, domainEventPublisher);
    }

    @Test
    @DisplayName("apply：不支持的动作抛参数异常")
    void shouldRejectUnknownAction() {
        BizException exception = assertThrows(BizException.class,
                () -> userLifecycleAppService.apply(command("UNKNOWN", 100L)));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("apply：离职停用账号并发布 USER_DISABLED")
    void shouldDisableOnLeave() {
        UserAggregate user = enabledUser();
        when(userRepository.findById(100L)).thenReturn(Optional.of(user));

        UserLifecycleVO result = userLifecycleAppService.apply(command("LEAVE", 100L));

        assertEquals(100L, result.userId());
        assertEquals("alice", result.username());
        verify(user).disable();
        verify(userRepository).save(user);
        verify(domainEventPublisher).publish(any(UserDomainEvent.class));
    }

    @Test
    @DisplayName("apply：调岗仅变更部门并发布更新事件")
    void shouldTransferDepartment() {
        UserAggregate user = enabledUser();
        when(user.getNickname()).thenReturn("nick");
        when(user.getPhone()).thenReturn("13800000000");
        when(user.getEmail()).thenReturn("a@b.com");
        when(userRepository.findById(100L)).thenReturn(Optional.of(user));

        userLifecycleAppService.apply(
                new UserLifecycleCommand("TRANSFER", 100L, null, null, 30L, null, null));

        verify(user).changeProfile("nick", "13800000000", "a@b.com", null, 30L);
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("apply：返聘启用账号")
    void shouldEnableOnRehire() {
        UserAggregate user = enabledUser();
        when(userRepository.findById(100L)).thenReturn(Optional.of(user));

        userLifecycleAppService.apply(command("REHIRE", 100L));

        verify(user).enable();
        verify(userRepository).save(user);
    }

    /**
     * 构造生命周期命令。
     *
     * @param action 动作
     * @param userId 用户ID
     * @return 命令
     */
    private static UserLifecycleCommand command(String action, Long userId) {
        return new UserLifecycleCommand(action, userId, null, null, null, null, null);
    }

    /**
     * 构造启用态用户 mock。
     *
     * @return 用户
     */
    private static UserAggregate enabledUser() {
        UserAggregate user = mock(UserAggregate.class);
        when(user.getId()).thenReturn(100L);
        when(user.getUsername()).thenReturn(Username.of("alice"));
        when(user.getStatus()).thenReturn(1);
        when(user.getDeptId()).thenReturn(20L);
        return user;
    }
}
