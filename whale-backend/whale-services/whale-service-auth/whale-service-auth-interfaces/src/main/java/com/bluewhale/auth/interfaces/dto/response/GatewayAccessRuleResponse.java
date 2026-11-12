package com.bluewhale.auth.interfaces.dto.response;

import java.time.LocalDateTime;

/**
 * 网关访问名单（白名单 / 黑名单）条目响应。
 *
 * @param id 主键
 * @param pathPattern 路径（Ant 通配）
 * @param httpMethod HTTP 方法，{@code *} 表示不限
 * @param scope 作用域
 * @param source 来源（MIGRATION / MANUAL）
 * @param remark 备注
 * @param status 状态（1 启用/生效、0 停用/解除）
 * @param sortNo 排序号
 * @param createTime 创建时间
 * @param updateTime 更新时间
 * @author BlueWhale
 */
public record GatewayAccessRuleResponse(Long id, String pathPattern, String httpMethod, String scope, String source,
                                        String remark, Integer status, Integer sortNo,
                                        LocalDateTime createTime, LocalDateTime updateTime) {
}
