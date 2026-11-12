package com.bluewhale.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.auth.domain.model.vo.EncryptedPassword;
import com.bluewhale.auth.domain.model.vo.Username;
import com.bluewhale.auth.domain.aggregate.UserAggregate;
import com.bluewhale.auth.domain.repository.UserRepository;
import com.bluewhale.auth.infrastructure.persistence.entity.SysUser;
import com.bluewhale.auth.infrastructure.persistence.entity.SysUserRole;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysUserMapper;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysUserRoleMapper;
import com.bluewhale.common.tenant.TenantContext;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link UserRepositoryImpl} 单元测试（REQ-ARCH-001 / T-DDD-8）：仓储读写与 PO↔领域转换链路。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class UserRepositoryImplTest {

    private static final long USER_ID = 5L;
    private static final String CIPHER = "$2a$10$abcdefghijklmnopqrstuv";

    @Mock
    private SysUserMapper userMapper;

    @Mock
    private SysUserRoleMapper userRoleMapper;

    @InjectMocks
    private UserRepositoryImpl repository;

    @BeforeEach
    void bindTenant() {
        TenantContext.set(1L);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    /**
     * 注册实体元信息缓存：{@code LambdaQueryWrapper#eq} 按实体解析列名，纯单测需手动初始化 TableInfo。
     */
    @BeforeAll
    static void initTableInfoCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysUser.class);
        TableInfoHelper.initTableInfo(assistant, SysUserRole.class);
    }

    private static SysUser po() {
        SysUser po = new SysUser();
        po.setId(USER_ID);
        po.setTenantId(1L);
        po.setUsername("bob_02");
        po.setPassword(CIPHER);
        po.setNickname("Bob");
        po.setStatus(1);
        return po;
    }

    @Test
    @DisplayName("findById：命中返回领域聚合")
    void shouldFindById() {
        when(userMapper.selectOne(any())).thenReturn(po());

        Optional<UserAggregate> result = repository.findById(USER_ID);

        assertTrue(result.isPresent());
        assertEquals("bob_02", result.get().getUsername().value());
    }

    @Test
    @DisplayName("findById：未命中返回空")
    void shouldReturnEmptyWhenFindByIdMissing() {
        when(userMapper.selectOne(any())).thenReturn(null);

        assertFalse(repository.findById(USER_ID).isPresent());
    }

    @Test
    @DisplayName("findByUsername：命中返回领域聚合")
    void shouldFindByUsername() {
        when(userMapper.selectOne(any())).thenReturn(po());

        assertTrue(repository.findByUsername("bob_02").isPresent());
    }

    @Test
    @DisplayName("findByUsername：未命中返回空")
    void shouldReturnEmptyWhenUsernameMissing() {
        when(userMapper.selectOne(any())).thenReturn(null);

        assertFalse(repository.findByUsername("ghost").isPresent());
    }

    @Test
    @DisplayName("existsByUsername：计数大于 0 判定为存在")
    void shouldReportUsernameExists() {
        when(userMapper.selectCount(any())).thenReturn(1L);

        assertTrue(repository.existsByUsername(1L, "bob_02"));
    }

    @Test
    @DisplayName("existsByUsername：计数为 0 或空判定为不存在")
    void shouldReportUsernameAbsent() {
        when(userMapper.selectCount(any())).thenReturn(0L);

        assertFalse(repository.existsByUsername(1L, "nobody"));
    }

    @Test
    @DisplayName("save：无 ID 走新增")
    void shouldInsertWhenIdAbsent() {
        repository.save(UserAggregate.create(1L, Username.of("bob_02"), EncryptedPassword.of(CIPHER),
                "Bob", null, null, null, 1));

        verify(userMapper).insert(any(SysUser.class));
        verify(userMapper, never()).updateById(any(SysUser.class));
    }

    @Test
    @DisplayName("save：有 ID 走按主键更新")
    void shouldUpdateWhenIdPresent() {
        repository.save(UserAggregate.reconstitute(USER_ID, 1L, Username.of("bob_02"), EncryptedPassword.of(CIPHER),
                "Bob", null, null, null, 1, null, null));

        verify(userMapper).updateById(any(SysUser.class));
        verify(userMapper, never()).insert(any(SysUser.class));
    }

    @Test
    @DisplayName("replaceRoles：先清空关联再逐条重建")
    void shouldReplaceRoles() {
        repository.replaceRoles(USER_ID, List.of(1L, 2L));

        verify(userRoleMapper).delete(any());
        verify(userRoleMapper, times(2)).insert(any(SysUserRole.class));
    }

    @Test
    @DisplayName("page：关键字与分页参数透传，结果映射为领域聚合")
    void shouldPageWithKeywordAndPagination() {
        Page<SysUser> page = new Page<>(2, 5);
        page.setRecords(List.of(po()));
        page.setTotal(6L);
        when(userMapper.selectPage(ArgumentMatchers.<Page<SysUser>>any(),
                ArgumentMatchers.<Wrapper<SysUser>>any())).thenReturn(page);

        UserRepository.UserPage result = repository.page(2, 5, "bob");

        assertEquals(1, result.records().size());
        assertEquals("bob_02", result.records().get(0).getUsername().value());
        assertEquals(6L, result.total());
        ArgumentCaptor<Page> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(userMapper).selectPage(pageCaptor.capture(), ArgumentMatchers.<Wrapper<SysUser>>any());
        assertEquals(2L, pageCaptor.getValue().getCurrent());
        assertEquals(5L, pageCaptor.getValue().getSize());
    }

    @Test
    @DisplayName("deleteById：删除用户并物理清理用户-角色关联")
    void shouldDeleteUserAndCleanRelations() {
        repository.deleteById(USER_ID);

        verify(userMapper).delete(ArgumentMatchers.<Wrapper<SysUser>>any());
        verify(userRoleMapper).delete(ArgumentMatchers.<Wrapper<SysUserRole>>any());
    }

    @Test
    @DisplayName("removeRolesInTenant：只按目标租户角色清理关联行")
    void shouldRemoveRolesInTenant() {
        repository.removeRolesInTenant(USER_ID, 2L);

        verify(userRoleMapper).deleteByUserAndRoleTenant(USER_ID, 2L);
    }

    @Test
    @DisplayName("removeRolesInTenant：参数缺失时跳过删除")
    void shouldSkipRemoveRolesInTenantWhenArgumentsMissing() {
        repository.removeRolesInTenant(null, 2L);
        repository.removeRolesInTenant(USER_ID, null);

        verifyNoInteractions(userRoleMapper);
    }

    @Test
    @DisplayName("listRoleIds：返回用户关联的角色 ID 列表")
    void shouldListRoleIds() {
        SysUserRole first = new SysUserRole();
        first.setUserId(USER_ID);
        first.setRoleId(1L);
        SysUserRole second = new SysUserRole();
        second.setUserId(USER_ID);
        second.setRoleId(2L);
        when(userRoleMapper.selectList(ArgumentMatchers.<Wrapper<SysUserRole>>any()))
                .thenReturn(List.of(first, second));

        List<Long> roleIds = repository.listRoleIds(USER_ID);

        assertEquals(List.of(1L, 2L), roleIds);
    }
}
