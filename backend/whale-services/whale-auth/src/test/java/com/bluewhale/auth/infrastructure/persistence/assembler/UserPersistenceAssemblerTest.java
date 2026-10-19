package com.bluewhale.auth.infrastructure.persistence.assembler;

import com.bluewhale.auth.domain.model.vo.EncryptedPassword;
import com.bluewhale.auth.domain.model.vo.Username;
import com.bluewhale.auth.domain.user.aggregate.User;
import com.bluewhale.auth.domain.user.entity.UserRoleAssignment;
import com.bluewhale.auth.entity.SysUser;
import com.bluewhale.auth.entity.SysUserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link UserPersistenceAssembler} 逐字段比对单元测试（REQ-ARCH-001 / T-DDD-8）：
 * 防止 PO ↔ 领域对象转换漏字段。
 *
 * @author BlueWhale
 */
class UserPersistenceAssemblerTest {

    private static final String CIPHER = "$2a$10$abcdefghijklmnopqrstuv";
    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 1, 1, 9, 0, 0);
    private static final LocalDateTime UPDATED = LocalDateTime.of(2026, 1, 2, 9, 0, 0);

    private static SysUser fullPo() {
        SysUser po = new SysUser();
        po.setId(5L);
        po.setUsername("bob_02");
        po.setPassword(CIPHER);
        po.setNickname("Bob");
        po.setPhone("13900000000");
        po.setEmail("b@c.com");
        po.setAvatar("avatar.png");
        po.setDeptId(4L);
        po.setStatus(User.STATUS_ENABLED);
        po.setLastLoginTime(LocalDateTime.of(2026, 2, 2, 10, 0, 0));
        po.setDeleted(0);
        po.setCreateTime(CREATED);
        po.setUpdateTime(UPDATED);
        return po;
    }

    private static User fullDomain() {
        return User.reconstitute(5L, Username.of("bob_02"), EncryptedPassword.of(CIPHER), "Bob",
                "13900000000", "b@c.com", 4L, User.STATUS_ENABLED, CREATED, UPDATED);
    }

    @Test
    @DisplayName("PO → 领域：User 聚合全部关键字段逐项一致")
    void shouldMapPoToDomainFieldByField() {
        User user = UserPersistenceAssembler.toDomain(fullPo());

        assertEquals(5L, user.getId());
        assertEquals("bob_02", user.getUsername().value());
        assertEquals(CIPHER, user.getPassword().value());
        assertEquals("Bob", user.getNickname());
        assertEquals("13900000000", user.getPhone());
        assertEquals("b@c.com", user.getEmail());
        assertEquals(4L, user.getDeptId());
        assertEquals(User.STATUS_ENABLED, user.getStatus());
        assertTrue(user.isEnabled());
        assertEquals(CREATED, user.getCreateTime());
        assertEquals(UPDATED, user.getUpdateTime());
    }

    @Test
    @DisplayName("领域 → PO：SysUser 各字段逐项一致，未纳入聚合的字段保持为空")
    void shouldMapDomainToPoFieldByField() {
        SysUser po = UserPersistenceAssembler.toPo(fullDomain());

        assertEquals(5L, po.getId());
        assertEquals("bob_02", po.getUsername());
        assertEquals(CIPHER, po.getPassword());
        assertEquals("Bob", po.getNickname());
        assertEquals("13900000000", po.getPhone());
        assertEquals("b@c.com", po.getEmail());
        assertEquals(4L, po.getDeptId());
        assertEquals(User.STATUS_ENABLED, po.getStatus());
        assertEquals(CREATED, po.getCreateTime());
        assertEquals(UPDATED, po.getUpdateTime());
        // avatar / lastLoginTime / deleted 不属本步 User 聚合关键项，转换不携带
        assertNull(po.getAvatar());
        assertNull(po.getLastLoginTime());
        assertNull(po.getDeleted());
    }

    @Test
    @DisplayName("PO → 领域：status 为空归一化为停用")
    void shouldNormalizeNullStatusToDisabled() {
        SysUser po = fullPo();
        po.setStatus(null);

        User user = UserPersistenceAssembler.toDomain(po);

        assertEquals(User.STATUS_DISABLED, user.getStatus());
        assertFalse(user.isEnabled());
    }

    @Test
    @DisplayName("用户-角色分配：PO ↔ 领域双向字段一致")
    void shouldMapRoleAssignmentBothWays() {
        SysUserRole po = new SysUserRole();
        po.setId(8L);
        po.setUserId(5L);
        po.setRoleId(6L);

        UserRoleAssignment assignment = UserPersistenceAssembler.toAssignment(po);

        assertEquals(8L, assignment.id());
        assertEquals(5L, assignment.userId());
        assertEquals(6L, assignment.roleId());

        SysUserRole converted = UserPersistenceAssembler.toAssignmentPo(UserRoleAssignment.of(5L, 6L));
        assertEquals(5L, converted.getUserId());
        assertEquals(6L, converted.getRoleId());
        assertNull(converted.getId());
    }
}
