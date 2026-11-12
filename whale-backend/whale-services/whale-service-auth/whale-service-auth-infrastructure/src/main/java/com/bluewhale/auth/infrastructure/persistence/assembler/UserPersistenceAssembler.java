package com.bluewhale.auth.infrastructure.persistence.assembler;

import com.bluewhale.auth.domain.model.vo.EncryptedPassword;
import com.bluewhale.auth.domain.model.vo.Username;
import com.bluewhale.auth.domain.aggregate.UserAggregate;
import com.bluewhale.auth.domain.model.entity.UserRoleAssignment;
import com.bluewhale.auth.infrastructure.persistence.entity.SysUserPo;
import com.bluewhale.auth.infrastructure.persistence.entity.SysUserRolePo;

/**
 * User 聚合与其持久化对象（PO）的双向转换器（手写 Assembler，REQ-ARCH-001 / T-DDD-8）。
 *
 * <p>选择手写而非 MapStruct：领域对象以值对象（{@link Username}/{@link EncryptedPassword}）承载关键字段，
 * 且需对 {@code status=null} 做归一化，显式转换更可控、可读，并规避 Lombok + MapStruct 绑定与
 * 生成源码的静态检查风险。未映射字段（{@code avatar}/{@code lastLoginTime}/{@code deleted}）不属于
 * 本步 User 聚合关键项，转换时保持 PO 原值不动。</p>
 *
 * @author BlueWhale
 */
public final class UserPersistenceAssembler {

    /**
     * 私有构造器：工具类，禁止实例化
     */
    private UserPersistenceAssembler() {
    }

    /**
     * PO → 领域对象：{@code status=null} 归一化为停用（与既有登录判定一致）；
     * {@code mustChangePassword=null} 由 {@code UserAggregate.reconstitute} 按「否」处理
     * （读侧 null 与 0 语义一致，写侧统一落 0）
     *
     * @param po 用户持久化对象
     * @return 用户聚合根
     */
    public static UserAggregate toDomain(SysUserPo po) {
        int status = po.getStatus() == null ? UserAggregate.STATUS_DISABLED : po.getStatus();
        return UserAggregate.reconstitute(
                po.getId(),
                po.getTenantId(),
                Username.of(po.getUsername()),
                EncryptedPassword.of(po.getPassword()),
                po.getNickname(),
                po.getPhone(),
                po.getEmail(),
                po.getAvatar(),
                po.getDeptId(),
                status,
                po.getMustChangePassword(),
                po.getPasswordUpdatedTime(),
                po.getLastLoginTime(),
                po.getCreateTime(),
                po.getUpdateTime());
    }

    /**
     * 领域对象 → PO：仅转换 User 聚合所承载的字段
     *
     * @param user 用户聚合根
     * @return 用户持久化对象
     */
    public static SysUserPo toPo(UserAggregate user) {
        SysUserPo po = new SysUserPo();
        po.setId(user.getId());
        po.setTenantId(user.getTenantId());
        po.setUsername(user.getUsername().value());
        po.setPassword(user.getPassword().value());
        po.setMustChangePassword(user.isMustChangePassword() ? 1 : 0);
        // 最近改密时间随聚合写回；到期提醒时间由提醒任务独立维护，此处不写（避免覆盖）
        po.setPasswordUpdatedTime(user.getPasswordUpdatedTime());
        po.setNickname(user.getNickname());
        po.setPhone(user.getPhone());
        po.setEmail(user.getEmail());
        po.setAvatar(user.getAvatar());
        po.setDeptId(user.getDeptId());
        po.setStatus(user.getStatus());
        po.setLastLoginTime(user.getLastLoginTime());
        po.setCreateTime(user.getCreateTime());
        po.setUpdateTime(user.getUpdateTime());
        return po;
    }

    /**
     * PO → 领域对象：用户-角色分配
     *
     * @param po 用户-角色关联持久化对象
     * @return 用户-角色分配实体
     */
    public static UserRoleAssignment toAssignment(SysUserRolePo po) {
        return new UserRoleAssignment(po.getId(), po.getUserId(), po.getRoleId());
    }

    /**
     * 领域对象 → PO：用户-角色分配
     *
     * @param assignment 用户-角色分配实体
     * @return 用户-角色关联持久化对象
     */
    public static SysUserRolePo toAssignmentPo(UserRoleAssignment assignment) {
        SysUserRolePo po = new SysUserRolePo();
        po.setId(assignment.id());
        po.setUserId(assignment.userId());
        po.setRoleId(assignment.roleId());
        return po;
    }
}
