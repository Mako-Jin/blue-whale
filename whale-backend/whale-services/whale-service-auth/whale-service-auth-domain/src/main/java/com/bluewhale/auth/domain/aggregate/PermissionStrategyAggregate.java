package com.bluewhale.auth.domain.aggregate;

import com.bluewhale.auth.domain.model.vo.StrategyCondition;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.exception.DomainException;
import com.bluewhale.common.domain.model.Entity;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 权限策略聚合根（对应 {@code sys_permission_strategy}，权限中心扩展 M3）。
 *
 * <p><b>不变量</b>：① {@code strategyCode} 租户内唯一（仓储前置检查 + DB 唯一约束兜底）；
 * ② {@code roleIds} / {@code resourceIds} 引用的角色、资源必须存在（应用层调用仓储校验）；
 * ③ 同租户仅一条默认策略（应用层编排：先 {@code clearDefault} 再保存）；
 * ④ {@code priority} 数字小者优先；⑤ 生效角色 / 资源集合非空。</p>
 *
 * @author BlueWhale
 */
public class PermissionStrategyAggregate implements Entity<Long> {

    /** 策略类型：授权 */
    public static final String TYPE_GRANT = "GRANT";
    /** 策略类型：拒绝（黑名单语义，命中即拒） */
    public static final String TYPE_DENY = "DENY";
    /** 状态：启用 */
    public static final int STATUS_ENABLED = 1;
    /** 状态：停用 */
    public static final int STATUS_DISABLED = 0;
    /** 生效角色 / 资源数量上限 */
    public static final int MAX_TARGETS = 200;

    private final Long id;
    private final Long tenantId;
    private final String strategyCode;
    private String strategyName;
    private final String strategyType;
    private List<Long> roleIds;
    private List<Long> resourceIds;
    private List<StrategyCondition> conditions;
    private boolean defaultStrategy;
    private int priority;
    private int status;
    private String description;
    private final LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 私有构造器：集中校验聚合不变量
     *
     * @param id 主键（新建时为 {@code null}）
     * @param tenantId 租户 ID
     * @param strategyCode 策略编码
     * @param strategyName 策略名称
     * @param strategyType 策略类型（GRANT / DENY）
     * @param roleIds 生效角色 ID 集合
     * @param resourceIds 生效资源 ID 集合
     * @param conditions 附加条件（可空列表表示无条件）
     * @param defaultStrategy 是否默认策略
     * @param priority 优先级（数字小者优先）
     * @param status 状态（1 启用 / 0 停用）
     * @param description 描述
     * @param createTime 创建时间
     * @param updateTime 更新时间
     */
    private PermissionStrategyAggregate(Long id, Long tenantId, String strategyCode, String strategyName,
                                        String strategyType, List<Long> roleIds, List<Long> resourceIds,
                                        List<StrategyCondition> conditions, boolean defaultStrategy,
                                        int priority, int status, String description,
                                        LocalDateTime createTime, LocalDateTime updateTime) {
        if (tenantId == null) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "策略必须归属租户");
        }
        if (strategyCode == null || strategyCode.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "策略编码不能为空");
        }
        if (strategyName == null || strategyName.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "策略名称不能为空");
        }
        if (!TYPE_GRANT.equals(strategyType) && !TYPE_DENY.equals(strategyType)) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "策略类型非法，仅支持 GRANT / DENY");
        }
        requireNonEmpty(roleIds, "生效角色集合不能为空");
        requireNonEmpty(resourceIds, "生效资源集合不能为空");
        requireSizeLimit(roleIds, "生效角色");
        requireSizeLimit(resourceIds, "生效资源");
        if (priority < 0) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "优先级不能为负数");
        }
        if (status != STATUS_ENABLED && status != STATUS_DISABLED) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "状态值非法，仅支持 1（启用）/ 0（停用）");
        }
        this.id = id;
        this.tenantId = tenantId;
        this.strategyCode = strategyCode.trim();
        this.strategyName = strategyName.trim();
        this.strategyType = strategyType;
        this.roleIds = List.copyOf(roleIds.stream().filter(Objects::nonNull).distinct().toList());
        this.resourceIds = List.copyOf(resourceIds.stream().filter(Objects::nonNull).distinct().toList());
        this.conditions = conditions == null ? List.of() : List.copyOf(conditions);
        this.defaultStrategy = defaultStrategy;
        this.priority = priority;
        this.status = status;
        this.description = description;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }

    /**
     * 工厂方法：新建策略（默认非默认策略、启用、优先级 100）
     *
     * @param tenantId 租户 ID
     * @param strategyCode 策略编码
     * @param strategyName 策略名称
     * @param strategyType 策略类型（GRANT / DENY）
     * @param roleIds 生效角色 ID 集合
     * @param resourceIds 生效资源 ID 集合
     * @param conditions 附加条件（可空）
     * @param isDefault 是否默认策略
     * @param priority 优先级（可空，默认 100）
     * @return 新建策略聚合
     */
    public static PermissionStrategyAggregate create(Long tenantId, String strategyCode, String strategyName,
                                                     String strategyType, List<Long> roleIds,
                                                     List<Long> resourceIds, List<StrategyCondition> conditions,
                                                     boolean isDefault, Integer priority) {
        LocalDateTime now = LocalDateTime.now();
        return new PermissionStrategyAggregate(null, tenantId, strategyCode, strategyName, strategyType,
                roleIds, resourceIds, conditions, isDefault,
                priority == null ? 100 : priority, STATUS_ENABLED, null, now, now);
    }

    /**
     * 由持久化数据重建聚合
     *
     * @param id 主键
     * @param tenantId 租户 ID
     * @param strategyCode 策略编码
     * @param strategyName 策略名称
     * @param strategyType 策略类型
     * @param roleIds 生效角色 ID 集合
     * @param resourceIds 生效资源 ID 集合
     * @param conditions 附加条件
     * @param isDefault 是否默认策略
     * @param priority 优先级
     * @param status 状态
     * @param description 描述
     * @param createTime 创建时间
     * @param updateTime 更新时间
     * @return 策略聚合
     */
    public static PermissionStrategyAggregate reconstitute(Long id, Long tenantId, String strategyCode,
                                                           String strategyName, String strategyType,
                                                           List<Long> roleIds, List<Long> resourceIds,
                                                           List<StrategyCondition> conditions, Boolean isDefault,
                                                           Integer priority, Integer status, String description,
                                                           LocalDateTime createTime, LocalDateTime updateTime) {
        return new PermissionStrategyAggregate(id, tenantId, strategyCode, strategyName, strategyType,
                roleIds, resourceIds, conditions, Boolean.TRUE.equals(isDefault),
                priority == null ? 100 : priority,
                status == null ? STATUS_ENABLED : status, description, createTime, updateTime);
    }

    /**
     * 行为：重命名策略
     *
     * @param strategyName 新策略名称
     */
    public void rename(String strategyName) {
        if (strategyName == null || strategyName.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "策略名称不能为空");
        }
        this.strategyName = strategyName.trim();
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 行为：变更生效目标（角色 / 资源集合，整体替换）
     *
     * @param newRoleIds 新生效角色 ID 集合
     * @param newResourceIds 新生效资源 ID 集合
     * @throws DomainException 集合为空或超量时抛出
     */
    public void changeTargets(List<Long> newRoleIds, List<Long> newResourceIds) {
        requireNonEmpty(newRoleIds, "生效角色集合不能为空");
        requireNonEmpty(newResourceIds, "生效资源集合不能为空");
        requireSizeLimit(newRoleIds, "生效角色");
        requireSizeLimit(newResourceIds, "生效资源");
        this.roleIds = List.copyOf(newRoleIds.stream().filter(Objects::nonNull).distinct().toList());
        this.resourceIds = List.copyOf(newResourceIds.stream().filter(Objects::nonNull).distinct().toList());
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 行为：变更附加条件（整体替换，空列表表示无条件）
     *
     * @param newConditions 新附加条件
     */
    public void changeConditions(List<StrategyCondition> newConditions) {
        this.conditions = newConditions == null ? List.of() : List.copyOf(newConditions);
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 行为：变更优先级
     *
     * @param priority 新优先级（数字小者优先）
     */
    public void changePriority(int priority) {
        if (priority < 0) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "优先级不能为负数");
        }
        this.priority = priority;
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
     * 行为：标记为默认策略（同租户仅一条由应用层编排保证）
     */
    public void markDefault() {
        this.defaultStrategy = true;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 行为：取消默认策略
     */
    public void unmarkDefault() {
        this.defaultStrategy = false;
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
     * 校验 ID 集合非空（null / 空集合 / 全 null 视为空）
     *
     * @param ids 待校验 ID 集合
     * @param message 异常文案
     */
    private void requireNonEmpty(Collection<Long> ids, String message) {
        if (ids == null || ids.isEmpty() || ids.stream().allMatch(Objects::isNull)) {
            throw new DomainException(ErrorCode.PARAM_ERROR, message);
        }
    }

    /**
     * 校验 ID 集合不超过数量上限
     *
     * @param ids 待校验 ID 集合
     * @param name 集合业务名称（用于异常文案）
     */
    private void requireSizeLimit(Collection<Long> ids, String name) {
        if (ids.size() > MAX_TARGETS) {
            throw new DomainException(ErrorCode.PARAM_ERROR, name + "最多 " + MAX_TARGETS + " 个");
        }
    }

    /**
     * 判断是否默认策略
     *
     * @return 默认策略返回 {@code true}
     */
    public boolean isDefaultStrategy() {
        return defaultStrategy;
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
     * 判断是否拒绝型策略
     *
     * @return DENY 类型返回 {@code true}
     */
    public boolean isDeny() {
        return TYPE_DENY.equals(strategyType);
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
     * 获取策略编码
     *
     * @return 策略编码
     */
    public String getStrategyCode() {
        return strategyCode;
    }

    /**
     * 获取策略名称
     *
     * @return 策略名称
     */
    public String getStrategyName() {
        return strategyName;
    }

    /**
     * 获取策略类型
     *
     * @return GRANT / DENY
     */
    public String getStrategyType() {
        return strategyType;
    }

    /**
     * 获取生效角色 ID 集合
     *
     * @return 角色 ID 列表（只读）
     */
    public List<Long> getRoleIds() {
        return roleIds;
    }

    /**
     * 获取生效资源 ID 集合
     *
     * @return 资源 ID 列表（只读）
     */
    public List<Long> getResourceIds() {
        return resourceIds;
    }

    /**
     * 获取附加条件
     *
     * @return 条件列表（只读）
     */
    public List<StrategyCondition> getConditions() {
        return conditions;
    }

    /**
     * 获取优先级
     *
     * @return 优先级
     */
    public int getPriority() {
        return priority;
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
