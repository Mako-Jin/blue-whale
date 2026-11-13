package com.bluewhale.auth.infrastructure.persistence.assembler;

import com.bluewhale.auth.domain.aggregate.UserGroupAggregate;
import com.bluewhale.auth.domain.model.vo.GroupFilters;
import com.bluewhale.auth.infrastructure.persistence.po.SysUserGroupMemberPo;
import com.bluewhale.auth.infrastructure.persistence.po.SysUserGroupPo;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.exception.DomainException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户组聚合与持久化对象装配器（权限中心扩展 M3）。
 *
 * <p>动态组过滤规则 {@code GroupFilters} 与 TEXT 列 JSON 互转由本类承担：
 * 序列化/反序列化失败统一包装为 {@link DomainException} 参数错误。</p>
 *
 * @author BlueWhale
 */
public final class UserGroupPersistenceAssembler {

    /** JSON 序列化器（静态复用，GroupFilters/Condition 为 record，Jackson 原生支持） */
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();

    /**
     * 工具类禁止实例化
     */
    private UserGroupPersistenceAssembler() {
    }

    /**
     * 聚合根 → 持久化对象（动态组 filters 序列化为 JSON）
     *
     * @param aggregate 用户组聚合
     * @return 用户组持久化对象
     */
    public static SysUserGroupPo toPo(UserGroupAggregate aggregate) {
        SysUserGroupPo po = new SysUserGroupPo();
        po.setId(aggregate.getId());
        po.setTenantId(aggregate.getTenantId());
        po.setGroupCode(aggregate.getGroupCode());
        po.setGroupName(aggregate.getGroupName());
        po.setParentId(aggregate.getParentId());
        po.setCategory(aggregate.getCategory());
        po.setFilters(serializeFilters(aggregate.getFilters()));
        po.setDescription(aggregate.getDescription());
        po.setSortNo(aggregate.getSortNo());
        po.setStatus(aggregate.getStatus());
        po.setCreateTime(aggregate.getCreateTime());
        po.setUpdateTime(aggregate.getUpdateTime());
        return po;
    }

    /**
     * 持久化对象 → 聚合根（filters JSON 反序列化失败抛参数错误）
     *
     * @param po 用户组持久化对象
     * @return 用户组聚合
     */
    public static UserGroupAggregate toDomain(SysUserGroupPo po) {
        return UserGroupAggregate.reconstitute(po.getId(), po.getTenantId(), po.getGroupCode(),
                po.getGroupName(), po.getParentId(), po.getCategory(), parseFilters(po.getFilters()),
                po.getDescription(), po.getSortNo(), po.getStatus(), po.getCreateTime(), po.getUpdateTime());
    }

    /**
     * 构建成员关系持久化对象（整体替换时逐条插入用）
     *
     * @param tenantId 租户 ID
     * @param groupId 组 ID
     * @param userId 成员用户 ID
     * @param memberSource 成员来源（STATIC / DYNAMIC）
     * @return 成员关系持久化对象
     */
    public static SysUserGroupMemberPo toMemberPo(Long tenantId, Long groupId, Long userId, String memberSource) {
        LocalDateTime now = LocalDateTime.now();
        SysUserGroupMemberPo po = new SysUserGroupMemberPo();
        po.setTenantId(tenantId);
        po.setGroupId(groupId);
        po.setUserId(userId);
        po.setMemberSource(memberSource);
        po.setCreateTime(now);
        po.setUpdateTime(now);
        return po;
    }

    /**
     * 过滤规则 → JSON 字符串（空规则返回 {@code null}，序列化失败抛参数错误）
     *
     * @param filters 过滤规则值对象（可空）
     * @return JSON 字符串；空规则返回 {@code null}
     */
    public static String serializeFilters(GroupFilters filters) {
        if (filters == null || filters.conditions().isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(filters.conditions());
        } catch (JacksonException ex) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "过滤规则序列化失败：" + ex.getMessage());
        }
    }

    /**
     * JSON 字符串 → 过滤规则（存储为空时返回空规则，反序列化失败抛参数错误）
     *
     * @param filtersJson 过滤规则 JSON（可空）
     * @return 过滤规则值对象
     */
    public static GroupFilters parseFilters(String filtersJson) {
        if (filtersJson == null || filtersJson.isBlank()) {
            return GroupFilters.empty();
        }
        try {
            List<GroupFilters.Condition> conditions = OBJECT_MAPPER
                    .readValue(filtersJson, new TypeReference<List<GroupFilters.Condition>>() { });
            return new GroupFilters(conditions);
        } catch (JacksonException ex) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "过滤规则反序列化失败：" + ex.getMessage());
        }
    }
}
