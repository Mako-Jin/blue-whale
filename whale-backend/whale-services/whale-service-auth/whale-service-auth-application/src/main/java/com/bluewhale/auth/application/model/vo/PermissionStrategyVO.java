package com.bluewhale.auth.application.model.vo;

import com.bluewhale.auth.domain.aggregate.PermissionStrategyAggregate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 权限策略视图对象（权限中心扩展 M3）
 *
 * @param id 主键
 * @param tenantId 租户 ID
 * @param strategyCode 策略编码
 * @param strategyName 策略名称
 * @param strategyType 策略类型（GRANT / DENY）
 * @param roleIds 生效角色 ID 集合
 * @param resourceIds 生效资源 ID 集合
 * @param conditionJson 附加条件 JSON（可空）
 * @param defaultStrategy 是否默认策略
 * @param priority 优先级
 * @param status 状态（1 启用 / 0 停用）
 * @param description 描述
 * @param createTime 创建时间
 * @param updateTime 更新时间
 * @author BlueWhale
 */
public record PermissionStrategyVO(Long id, Long tenantId, String strategyCode, String strategyName,
                                   String strategyType, List<Long> roleIds, List<Long> resourceIds,
                                   String conditionJson, boolean defaultStrategy, int priority, int status,
                                   String description, LocalDateTime createTime, LocalDateTime updateTime) {

    /**
     * 由聚合装配视图（conditions 以原始 JSON 透传，序列化由仓储端口承担）
     *
     * @param strategy 策略聚合
     * @param conditionJson 附加条件 JSON（可空）
     * @return 视图对象
     */
    public static PermissionStrategyVO from(PermissionStrategyAggregate strategy, String conditionJson) {
        return new PermissionStrategyVO(strategy.getId(), strategy.getTenantId(), strategy.getStrategyCode(),
                strategy.getStrategyName(), strategy.getStrategyType(), strategy.getRoleIds(),
                strategy.getResourceIds(), conditionJson, strategy.isDefaultStrategy(),
                strategy.getPriority(), strategy.getStatus(), strategy.getDescription(),
                strategy.getCreateTime(), strategy.getUpdateTime());
    }
}
