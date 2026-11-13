package com.bluewhale.auth.application.model.vo;

import com.bluewhale.auth.domain.aggregate.UserGroupAggregate;

import java.time.LocalDateTime;

/**
 * 用户组视图对象（权限中心扩展 M3）
 *
 * @param id 主键
 * @param tenantId 租户 ID
 * @param groupCode 组编码
 * @param groupName 组名称
 * @param parentId 父组 ID（0 顶级）
 * @param category 组类别（STATIC / DYNAMIC）
 * @param filtersJson 动态组过滤规则 JSON（静态组为 {@code null}）
 * @param description 描述
 * @param sortNo 排序号
 * @param status 状态（1 启用 / 0 停用）
 * @param createTime 创建时间
 * @param updateTime 更新时间
 * @author BlueWhale
 */
public record UserGroupVO(Long id, Long tenantId, String groupCode, String groupName, Long parentId,
                          String category, String filtersJson, String description, int sortNo, int status,
                          LocalDateTime createTime, LocalDateTime updateTime) {

    /**
     * 由聚合装配视图（filters 以原始 JSON 透传，序列化由仓储端口承担）
     *
     * @param group 用户组聚合
     * @param filtersJson 过滤规则 JSON（可空）
     * @return 视图对象
     */
    public static UserGroupVO from(UserGroupAggregate group, String filtersJson) {
        return new UserGroupVO(group.getId(), group.getTenantId(), group.getGroupCode(), group.getGroupName(),
                group.getParentId(), group.getCategory(), filtersJson, group.getDescription(),
                group.getSortNo(), group.getStatus(), group.getCreateTime(), group.getUpdateTime());
    }
}
