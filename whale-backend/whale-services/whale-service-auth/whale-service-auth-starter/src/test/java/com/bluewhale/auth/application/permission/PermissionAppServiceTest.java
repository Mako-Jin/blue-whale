package com.bluewhale.auth.application.permission;

import com.bluewhale.auth.application.event.DomainEventPublisher;
import com.bluewhale.auth.application.permission.command.SavePermissionCommand;
import com.bluewhale.auth.application.permission.query.PermissionPageQuery;
import com.bluewhale.auth.domain.event.AuthDomainEvent.ChangeType;
import com.bluewhale.auth.domain.event.PermissionDomainEvent;
import com.bluewhale.auth.domain.model.vo.PermCode;
import com.bluewhale.auth.domain.permission.aggregate.Permission;
import com.bluewhale.auth.domain.permission.repository.PermissionRepository;
import com.bluewhale.auth.application.vo.PermissionVO;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
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

/**
 * {@link PermissionAppService} 单元测试：权限点分页/全量码/详情/增删改与缓存失效（AUTH-17 / T-DDD-9）。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class PermissionAppServiceTest {

    private static final long PERM_ID = 10L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 1, 9, 0, 0);

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private DomainEventPublisher eventPublisher;

    @InjectMocks
    private PermissionAppService permissionAppService;

    private static Permission permission(Long id, String code, String name) {
        return Permission.reconstitute(id, Permission.ROOT_PARENT_ID, PermCode.of(code), name, "API",
                "/api/user", "GET", 1, Permission.STATUS_ENABLED, NOW, NOW);
    }

    @Test
    @DisplayName("page：透传关键字与类型过滤并映射为 PermissionVO")
    void shouldPagePermissions() {
        when(permissionRepository.page(1L, 10L, "user", "API"))
                .thenReturn(new PermissionRepository.PermissionPage(
                        List.of(permission(PERM_ID, "sys:user:list", "用户列表")), 1L));

        PageResult<PermissionVO> result = permissionAppService.page(
                new PermissionPageQuery(1L, 10L, "user", "API"));

        assertEquals(1, result.getRecords().size());
        assertEquals(1L, result.getTotal());
        assertEquals("sys:user:list", result.getRecords().get(0).getPermCode());
    }

    @Test
    @DisplayName("listAllCodes：返回启用权限码并去重")
    void shouldListAllCodesDistinct() {
        when(permissionRepository.findEnabledAll()).thenReturn(List.of(
                permission(1L, "sys:user:list", "用户列表"),
                permission(2L, "sys:user:list", "用户列表副本"),
                permission(3L, "sys:role:list", "角色列表")));

        List<String> codes = permissionAppService.listAllCodes();

        assertEquals(List.of("sys:user:list", "sys:role:list"), codes);
    }

    @Test
    @DisplayName("getDetail：不存在时抛 BizException(NOT_FOUND)")
    void shouldRejectGetDetailWhenMissing() {
        when(permissionRepository.findById(PERM_ID)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class, () -> permissionAppService.getDetail(PERM_ID));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("create：权限码已存在时抛 BizException 且不落库")
    void shouldRejectCreateWhenCodeExists() {
        when(permissionRepository.existsByPermCode("sys:user:list", null)).thenReturn(true);

        BizException ex = assertThrows(BizException.class, () -> permissionAppService.create(
                new SavePermissionCommand(null, "sys:user:list", "用户列表", "API", null, null, null, null)));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
        assertEquals("权限编码已存在", ex.getMessage());
        verify(permissionRepository, never()).save(any(Permission.class));
    }

    @Test
    @DisplayName("create：权限码可用时落库并取默认父节点/排序/状态")
    void shouldCreateWhenCodeAvailable() {
        when(permissionRepository.existsByPermCode("sys:user:list", null)).thenReturn(false);

        permissionAppService.create(new SavePermissionCommand(null, "sys:user:list", "用户列表", "API",
                null, null, null, null));

        ArgumentCaptor<Permission> captor = ArgumentCaptor.forClass(Permission.class);
        verify(permissionRepository).save(captor.capture());
        Permission saved = captor.getValue();
        assertEquals(Permission.ROOT_PARENT_ID, saved.getParentId());
        assertEquals(0, saved.getSortNo());
        assertEquals(Permission.STATUS_ENABLED, saved.getStatus());
        verify(eventPublisher).publish(new PermissionDomainEvent(null, ChangeType.CREATED));
    }

    @Test
    @DisplayName("update：不存在时抛 BizException(NOT_FOUND) 且不落库、不失效")
    void shouldRejectUpdateWhenMissing() {
        when(permissionRepository.findById(PERM_ID)).thenReturn(Optional.empty());

        assertThrows(BizException.class, () -> permissionAppService.update(PERM_ID,
                new SavePermissionCommand(null, "sys:user:edit", "用户编辑", "API", null, null, null, null)));
        verify(permissionRepository, never()).save(any(Permission.class));
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("update：权限码被占用时抛 BizException 且不落库、不失效")
    void shouldRejectUpdateWhenCodeDuplicated() {
        when(permissionRepository.findById(PERM_ID))
                .thenReturn(Optional.of(permission(PERM_ID, "sys:user:list", "用户列表")));
        when(permissionRepository.existsByPermCode("sys:user:edit", PERM_ID)).thenReturn(true);

        BizException ex = assertThrows(BizException.class, () -> permissionAppService.update(PERM_ID,
                new SavePermissionCommand(null, "sys:user:edit", "用户编辑", "API", null, null, null, null)));

        assertEquals("权限编码已存在", ex.getMessage());
        verify(permissionRepository, never()).save(any(Permission.class));
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("update：编码不冲突时应用字段落库并失效权限缓存")
    void shouldUpdateAndEvictByPermission() {
        Permission stored = permission(PERM_ID, "sys:user:list", "旧名");
        when(permissionRepository.findById(PERM_ID)).thenReturn(Optional.of(stored));
        when(permissionRepository.existsByPermCode("sys:user:edit", PERM_ID)).thenReturn(false);

        permissionAppService.update(PERM_ID, new SavePermissionCommand(3L, "sys:user:edit", "新名", "BUTTON",
                "/api/user/edit", "POST", 9, Permission.STATUS_DISABLED));

        ArgumentCaptor<Permission> captor = ArgumentCaptor.forClass(Permission.class);
        verify(permissionRepository).save(captor.capture());
        Permission saved = captor.getValue();
        assertEquals(3L, saved.getParentId());
        assertEquals("sys:user:edit", saved.getPermCode().value());
        assertEquals("新名", saved.getPermName());
        assertEquals("BUTTON", saved.getPermType());
        assertEquals(9, saved.getSortNo());
        assertEquals(Permission.STATUS_DISABLED, saved.getStatus());
        verify(eventPublisher).publish(new PermissionDomainEvent(PERM_ID, ChangeType.UPDATED));
    }

    @Test
    @DisplayName("delete：存在子权限时抛 BizException 且不删除、不失效")
    void shouldRejectDeleteWhenHasChildren() {
        when(permissionRepository.countChildren(PERM_ID)).thenReturn(1L);

        BizException ex = assertThrows(BizException.class, () -> permissionAppService.delete(PERM_ID));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
        assertEquals("存在子权限，不允许删除", ex.getMessage());
        verify(permissionRepository, never()).deleteById(anyLong());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("delete：无子权限时逻辑删除并失效权限缓存")
    void shouldDeleteAndEvictByPermission() {
        when(permissionRepository.countChildren(PERM_ID)).thenReturn(0L);

        permissionAppService.delete(PERM_ID);

        verify(permissionRepository).deleteById(PERM_ID);
        verify(eventPublisher).publish(new PermissionDomainEvent(PERM_ID, ChangeType.DELETED));
    }
}
