package com.bluewhale.auth.domain.aggregate;

import com.bluewhale.auth.domain.model.vo.ConstraintType;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.exception.DomainException;
import com.bluewhale.common.domain.model.Entity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 角色约束聚合根（对应 {@code sys_role_constraint}，权限中心扩展 M3）。
 *
 * <p><b>不变量</b>：① {@code constraintCode} 租户内唯一（仓储前置检查 + DB 唯一约束兜底）；
 * ② {@code MUTEX} 互斥约束需 ≥2 个角色（两两互斥）；③ {@code CARDINALITY} 基数约束的
 * {@code maxAssign} 必须 &gt; 0 且不超过覆盖角色数；④ 分配角色时必须校验全部启用约束
 * （由 {@code RoleConstraintChecker} 承担）。</p>
 *
 * @author BlueWhale
 */
public class RoleConstraintAggregate implements Entity<Long> {

    /** 状态：启用 */
    public static final int STATUS_ENABLED = 1;
    /** 状态：停用 */
    public static final int STATUS_DISABLED = 0;

    private final Long id;
    private final Long tenantId;
    private final String constraintCode;
    private String constraintName;
    private final ConstraintType constraintType;
    private List<Long> roleIds;
    private Integer maxAssign;
    private int status;
    private String description;
    private final LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 私有构造器：集中校验聚合不变量（含约束类型特有校验）
     *
     * @param id 主键（新建时为 {@code null}）
     * @param tenantId 租户 ID
     * @param constraintCode 约束编码
     * @param constraintName 约束名称
     * @param constraintType 约束类型
     * @param roleIds 约束覆盖的角色 ID 集合
     * @param maxAssign 最多可同时分配的角色数（仅 CARDINALITY）
     * @param status 状态（1 启用 / 0 停用）
     * @param description 描述
     * @param createTime 创建时间
     * @param updateTime 更新时间
     */
    private RoleConstraintAggregate(Long id, Long tenantId, String constraintCode, String constraintName,
                                    ConstraintType constraintType, List<Long> roleIds, Integer maxAssign,
                                    int status, String description, LocalDateTime createTime,
                                    LocalDateTime updateTime) {
        if (tenantId == null) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "约束必须归属租户");
        }
        if (constraintCode == null || constraintCode.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "约束编码不能为空");
        }
        if (constraintName == null || constraintName.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "约束名称不能为空");
        }
        if (constraintType == null) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "约束类型不能为空");
        }
        requireDistinctRoles(roleIds);
        List<Long> distinct = roleIds.stream().filter(Objects::nonNull).distinct().toList();
        if (constraintType == ConstraintType.MUTEX && distinct.size() < 2) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "互斥约束至少需要 2 个角色");
        }
        if (constraintType == ConstraintType.CARDINALITY) {
            if (maxAssign == null || maxAssign <= 0) {
                throw new DomainException(ErrorCode.PARAM_ERROR, "基数约束的 max_assign 必须 > 0");
            }
            if (maxAssign > distinct.size()) {
                throw new DomainException(ErrorCode.PARAM_ERROR,
                        "基数约束的 max_assign 不能超过覆盖角色数 " + distinct.size());
            }
        }
        if (status != STATUS_ENABLED && status != STATUS_DISABLED) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "状态值非法，仅支持 1（启用）/ 0（停用）");
        }
        this.id = id;
        this.tenantId = tenantId;
        this.constraintCode = constraintCode.trim();
        this.constraintName = constraintName.trim();
        this.constraintType = constraintType;
        this.roleIds = distinct;
        this.maxAssign = maxAssign;
        this.status = status;
        this.description = description;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }

    /**
     * 工厂方法：新建约束
     *
     * @param tenantId 租户 ID
     * @param constraintCode 约束编码
     * @param constraintName 约束名称
     * @param constraintType 约束类型字符串（MUTEX / CARDINALITY）
     * @param roleIds 约束覆盖的角色 ID 集合
     * @param maxAssign 最多可同时分配的角色数（仅 CARDINALITY，可空）
     * @return 新建约束聚合
     */
    public static RoleConstraintAggregate create(Long tenantId, String constraintCode, String constraintName,
                                                 String constraintType, List<Long> roleIds, Integer maxAssign) {
        LocalDateTime now = LocalDateTime.now();
        return new RoleConstraintAggregate(null, tenantId, constraintCode, constraintName,
                ConstraintType.of(constraintType), roleIds, maxAssign, STATUS_ENABLED, null, now, now);
    }

    /**
     * 由持久化数据重建聚合
     *
     * @param id 主键
     * @param tenantId 租户 ID
     * @param constraintCode 约束编码
     * @param constraintName 约束名称
     * @param constraintType 约束类型字符串
     * @param roleIds 约束覆盖的角色 ID 集合
     * @param maxAssign 最多可同时分配的角色数
     * @param status 状态
     * @param description 描述
     * @param createTime 创建时间
     * @param updateTime 更新时间
     * @return 约束聚合
     */
    public static RoleConstraintAggregate reconstitute(Long id, Long tenantId, String constraintCode,
                                                       String constraintName, String constraintType,
                                                       List<Long> roleIds, Integer maxAssign, Integer status,
                                                       String description, LocalDateTime createTime,
                                                       LocalDateTime updateTime) {
        return new RoleConstraintAggregate(id, tenantId, constraintCode, constraintName,
                ConstraintType.of(constraintType), roleIds, maxAssign,
                status == null ? STATUS_ENABLED : status, description, createTime, updateTime);
    }

    /**
     * 行为：重命名约束
     *
     * @param constraintName 新约束名称
     */
    public void rename(String constraintName) {
        if (constraintName == null || constraintName.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "约束名称不能为空");
        }
        this.constraintName = constraintName.trim();
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 行为：变更覆盖角色集合（整体替换，按当前约束类型重新校验）
     *
     * @param newRoleIds 新覆盖角色 ID 集合
     * @throws DomainException 违反当前约束类型不变量时抛出
     */
    public void changeRoles(List<Long> newRoleIds) {
        requireDistinctRoles(newRoleIds);
        List<Long> distinct = newRoleIds.stream().filter(Objects::nonNull).distinct().toList();
        if (constraintType == ConstraintType.MUTEX && distinct.size() < 2) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "互斥约束至少需要 2 个角色");
        }
        if (constraintType == ConstraintType.CARDINALITY) {
            if (maxAssign == null || maxAssign <= 0) {
                throw new DomainException(ErrorCode.PARAM_ERROR, "基数约束的 max_assign 必须 > 0");
            }
            if (maxAssign > distinct.size()) {
                throw new DomainException(ErrorCode.PARAM_ERROR,
                        "基数约束的 max_assign 不能超过覆盖角色数 " + distinct.size());
            }
        }
        this.roleIds = distinct;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 行为：变更基数上限（仅 CARDINALITY）
     *
     * @param newMaxAssign 新上限（必须 &gt; 0 且不超过覆盖角色数）
     * @throws DomainException 类型不是 CARDINALITY 或取值非法时抛出
     */
    public void changeMaxAssign(Integer newMaxAssign) {
        if (constraintType != ConstraintType.CARDINALITY) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "仅基数约束可设置 max_assign");
        }
        if (newMaxAssign == null || newMaxAssign <= 0) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "基数约束的 max_assign 必须 > 0");
        }
        if (newMaxAssign > roleIds.size()) {
            throw new DomainException(ErrorCode.PARAM_ERROR,
                    "基数约束的 max_assign 不能超过覆盖角色数 " + roleIds.size());
        }
        this.maxAssign = newMaxAssign;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 行为：变更描述
     *
     * @param description 新描述
     */
    public void changeDescription(String description) {
        this.description = description;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 行为：变更状态
     *
     * @param status 新状态（1 启用 / 0 停用）
     */
    public void changeStatus(int status) {
        if (status != STATUS_ENABLED && status != STATUS_DISABLED) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "状态值非法，仅支持 1（启用）/ 0（停用）");
        }
        this.status = status;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 校验约束覆盖的角色集合非空（null / 空集合 / 全 null 视为空）
     *
     * @param roleIds 待校验角色 ID 集合
     */
    private void requireDistinctRoles(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty() || roleIds.stream().allMatch(Objects::isNull)) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "约束覆盖的角色集合不能为空");
        }
    }

    /**
     * 判断是否启用
     *
     * @return 启用返回 {@code true}
     */
    public boolean isEnabled() {
        return status == STATUS_ENABLED;
    }

    /**
     * 获取主键
     *
     * @return 主键（新建时为 {@code null}）
     */
    @Override
    public Long getId() {
        return id;
    }

    /**
     * 获取租户 ID
     *
     * @return 租户 ID
     */
    public Long getTenantId() {
        return tenantId;
    }

    /**
     * 获取约束编码
     *
     * @return 约束编码
     */
    public String getConstraintCode() {
        return constraintCode;
    }

    /**
     * 获取约束名称
     *
     * @return 约束名称
     */
    public String getConstraintName() {
        return constraintName;
    }

    /**
     * 获取约束类型
     *
     * @return 约束类型枚举
     */
    public ConstraintType getConstraintType() {
        return constraintType;
    }

    /**
     * 获取覆盖角色 ID 集合
     *
     * @return 角色 ID 列表（去重）
     */
    public List<Long> getRoleIds() {
        return roleIds;
    }

    /**
     * 获取基数上限
     *
     * @return 上限（仅 CARDINALITY 非空）
     */
    public Integer getMaxAssign() {
        return maxAssign;
    }

    /**
     * 获取状态
     *
     * @return 状态（1 启用 / 0 停用）
     */
    public int getStatus() {
        return status;
    }

    /**
     * 获取描述
     *
     * @return 描述（可空）
     */
    public String getDescription() {
        return description;
    }

    /**
     * 获取创建时间
     *
     * @return 创建时间
     */
    public LocalDateTime getCreateTime() {
        return createTime;
    }

    /**
     * 获取更新时间
     *
     * @return 更新时间
     */
    public LocalDateTime getUpdateTime() {
        return updateTime;
    }
}
