package com.bluewhale.auth.application.model.vo;

import com.bluewhale.auth.domain.aggregate.GatewayBlacklistAggregate;
import com.bluewhale.auth.domain.aggregate.GatewayWhitelistAggregate;

import java.time.LocalDateTime;

/**
 * 网关访问名单管理视图对象：管理端列表展示与编辑回显使用。
 *
 * @param id 主键
 * @param pathPattern 路径（Ant 通配）
 * @param httpMethod HTTP 方法，{@code *} 表示不限
 * @param scope 作用域
 * @param tenantId 租户 ID（白名单：0 平台级 / &gt;0 绑定租户；黑名单恒为 0）
 * @param source 来源（MIGRATION / MANUAL）
 * @param remark 备注
 * @param status 状态（1 启用/生效、0 停用/解除）
 * @param sortNo 排序号
 * @param createTime 创建时间
 * @param updateTime 更新时间
 * @author BlueWhale
 */
public record GatewayAccessRuleVO(Long id, String pathPattern, String httpMethod, String scope, Long tenantId,
                                  String source, String remark, Integer status, Integer sortNo,
                                  LocalDateTime createTime, LocalDateTime updateTime) {

    /**
     * 由白名单聚合转换为视图对象
     *
     * @param rule 白名单聚合
     * @return 访问名单管理视图对象
     */
    public static GatewayAccessRuleVO from(GatewayWhitelistAggregate rule) {
        return new GatewayAccessRuleVO(rule.getId(), rule.getPathPattern(), rule.getHttpMethod(), rule.getScope(),
                rule.getTenantId(), rule.getSource(), rule.getRemark(), rule.getStatus(), rule.getSortNo(),
                rule.getCreateTime(), rule.getUpdateTime());
    }

    /**
     * 由黑名单聚合转换为视图对象（黑名单无租户维度，租户 ID 恒为 0）
     *
     * @param rule 黑名单聚合
     * @return 访问名单管理视图对象
     */
    public static GatewayAccessRuleVO from(GatewayBlacklistAggregate rule) {
        return new GatewayAccessRuleVO(rule.getId(), rule.getPathPattern(), rule.getHttpMethod(), rule.getScope(),
                0L, rule.getSource(), rule.getRemark(), rule.getStatus(), rule.getSortNo(),
                rule.getCreateTime(), rule.getUpdateTime());
    }
}
