package com.bluewhale.auth.application.userpermission;

import com.bluewhale.auth.application.event.DomainEventPublisher;
import com.bluewhale.auth.application.userpermission.command.ReplaceUserAttrsCommand;
import com.bluewhale.auth.domain.event.AuthDomainEvent.ChangeType;
import com.bluewhale.auth.domain.event.UserPermissionDomainEvent;
import com.bluewhale.auth.domain.permission.aggregate.UserPermissionAttribute;
import com.bluewhale.auth.domain.permission.repository.UserPermissionRepository;
import com.bluewhale.auth.application.vo.UserAttrVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link UserPermissionAppService} 单元测试：ABAC 属性查询与全量覆盖（AUTH-17 / T-DDD-9）。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class UserPermissionAppServiceTest {

    private static final long USER_ID = 2L;
    private static final long PERMISSION_ID = 10L;

    @Mock
    private UserPermissionRepository userPermissionRepository;

    @Mock
    private DomainEventPublisher eventPublisher;

    @InjectMocks
    private UserPermissionAppService userPermissionAppService;

    @Test
    @DisplayName("listByUserId：返回用户 ABAC 属性视图列表")
    void shouldListByUserId() {
        when(userPermissionRepository.listByUserId(USER_ID)).thenReturn(List.of(
                UserPermissionAttribute.reconstitute(1L, USER_ID, PERMISSION_ID, "deptId", "100")));

        List<UserAttrVO> attrs = userPermissionAppService.listByUserId(USER_ID);

        assertEquals(1, attrs.size());
        assertEquals("deptId", attrs.get(0).getAttrKey());
        assertEquals("100", attrs.get(0).getAttrValue());
    }

    @Test
    @DisplayName("replaceUserAttrs：空列表时仅以空集合执行全量覆盖（先删不插）")
    void shouldReplaceWithEmptyAttrs() {
        userPermissionAppService.replaceUserAttrs(USER_ID, new ReplaceUserAttrsCommand(List.of()));

        verify(userPermissionRepository).replaceAll(USER_ID, List.of());
        verify(eventPublisher).publish(new UserPermissionDomainEvent(USER_ID, ChangeType.ATTRS_REPLACED));
    }

    @Test
    @DisplayName("replaceUserAttrs：非空列表时构造属性聚合并先删后插")
    void shouldReplaceWithAttrs() {
        List<ReplaceUserAttrsCommand.UserAttrCommand> attrs = List.of(
                new ReplaceUserAttrsCommand.UserAttrCommand(PERMISSION_ID, "deptId", "100"),
                new ReplaceUserAttrsCommand.UserAttrCommand(PERMISSION_ID, "region", "east"));

        userPermissionAppService.replaceUserAttrs(USER_ID, new ReplaceUserAttrsCommand(attrs));

        verify(userPermissionRepository).replaceAll(org.mockito.ArgumentMatchers.eq(USER_ID),
                argThat(list -> list.size() == 2
                        && list.stream().allMatch(attr -> attr.getUserId().equals(USER_ID))
                        && "deptId".equals(list.get(0).getAttrKey())
                        && "east".equals(list.get(1).getAttrValue())));
        verify(eventPublisher).publish(new UserPermissionDomainEvent(USER_ID, ChangeType.ATTRS_REPLACED));
    }
}
