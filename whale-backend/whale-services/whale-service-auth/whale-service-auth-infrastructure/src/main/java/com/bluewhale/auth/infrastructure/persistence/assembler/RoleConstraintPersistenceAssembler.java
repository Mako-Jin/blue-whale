package com.bluewhale.auth.infrastructure.persistence.assembler;

import com.bluewhale.auth.domain.aggregate.RoleConstraintAggregate;
import com.bluewhale.auth.infrastructure.persistence.po.SysRoleConstraintPo;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.exception.DomainException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * 角色约束聚合与持久化对象装配器（权限中心扩展 M3）。
 *
 * <p>{@code roleIds} 以 JSON 数组形式落 TEXT 列；JSON 转换失败统一包装为
 * {@link DomainException} 参数错误。</p>
 *
 * @author BlueWhale
 */
public final class RoleConstraintPersistenceAssembler {

    /** JSON 序列化器（静态复用） */
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();

    /**
     * 工具类禁止实例化
     */
    private RoleConstraintPersistenceAssembler() {
    }

    /**
     * 聚合根 → 持久化对象（roleIds 序列化为 JSON，约束类型枚举取 name）
     *
     * @param aggregate 约束聚合
     * @return 约束持久化对象
     */
    public static SysRoleConstraintPo toPo(RoleConstraintAggregate aggregate) {
        SysRoleConstraintPo po = new SysRoleConstraintPo();
        po.setId(aggregate.getId());
        po.setTenantId(aggregate.getTenantId());
        po.setConstraintCode(aggregate.getConstraintCode());
        po.setConstraintName(aggregate.getConstraintName());
        po.setConstraintType(aggregate.getConstraintType().name());
        po.setRoleIds(serializeIds(aggregate.getRoleIds()));
        po.setMaxAssign(aggregate.getMaxAssign());
        po.setStatus(aggregate.getStatus());
        po.setDescription(aggregate.getDescription());
        po.setCreateTime(aggregate.getCreateTime());
        po.setUpdateTime(aggregate.getUpdateTime());
        return po;
    }

    /**
     * 持久化对象 → 聚合根（roleIds JSON 反序列化失败抛参数错误）
     *
     * @param po 约束持久化对象
     * @return 约束聚合
     */
    public static RoleConstraintAggregate toDomain(SysRoleConstraintPo po) {
        return RoleConstraintAggregate.reconstitute(po.getId(), po.getTenantId(), po.getConstraintCode(),
                po.getConstraintName(), po.getConstraintType(), parseIds(po.getRoleIds()), po.getMaxAssign(),
                po.getStatus(), po.getDescription(), po.getCreateTime(), po.getUpdateTime());
    }

    /**
     * ID 集合 → JSON 数组字符串（集合为空返回 {@code null}）
     *
     * @param ids ID 集合
     * @return JSON 数组字符串
     */
    private static String serializeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(ids);
        } catch (JacksonException ex) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "约束角色集合序列化失败：" + ex.getMessage());
        }
    }

    /**
     * JSON 数组字符串 → ID 集合（存储为空时返回空列表）
     *
     * @param json JSON 数组字符串（可空）
     * @return ID 列表
     */
    private static List<Long> parseIds(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Long> ids = OBJECT_MAPPER.readValue(json, new TypeReference<List<Long>>() { });
            return ids == null ? List.of() : ids;
        } catch (JacksonException ex) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "约束角色集合反序列化失败：" + ex.getMessage());
        }
    }
}
