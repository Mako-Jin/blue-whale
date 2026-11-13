package com.bluewhale.auth.application.port;

import com.bluewhale.auth.domain.model.vo.GroupFilters;

import java.util.List;

/**
 * 动态组成员求值端口（权限中心扩展 M3）。
 *
 * <p>按 {@link GroupFilters} 规则在租户内求值命中用户集合（字段口径：用户基础字段与
 * ABAC 属性键）；由 infrastructure 适配实现（读 {@code sys_user} / 用户属性后内存过滤）。</p>
 *
 * @author BlueWhale
 */
public interface GroupMemberEvaluator {

    /**
     * 求值：返回规则命中的用户 ID 集合（去重）
     *
     * @param tenantId 租户 ID
     * @param filters 过滤规则
     * @return 命中用户 ID 列表；无命中时返回空列表
     */
    List<Long> evaluate(Long tenantId, GroupFilters filters);
}
