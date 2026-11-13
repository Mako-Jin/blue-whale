package com.bluewhale.auth.infrastructure.persistence.assembler;

import com.bluewhale.auth.domain.aggregate.PermissionStrategyAggregate;
import com.bluewhale.auth.domain.model.vo.StrategyCondition;
import com.bluewhale.auth.infrastructure.persistence.po.SysPermissionStrategyPo;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.exception.DomainException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * 权限策略聚合与持久化对象装配器（权限中心扩展 M3）。
 *
 * <p>{@code roleIds}/{@code resourceIds} 以 JSON 数组形式落 TEXT 列，附加条件
 * {@code StrategyCondition} 列表序列化为 {@code condition_json}；JSON 转换失败
 * 统一包装为 {@link DomainException} 参数错误。</p>
 *
 * @author BlueWhale
 */
public final class PermissionStrategyPersistenceAssembler {

    /** JSON 序列化器（静态复用，StrategyCondition 为 record，Jackson 原生支持） */
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();

    /**
     * 工具类禁止实例化
     */
    private PermissionStrategyPersistenceAssembler() {
    }

    /**
     * 聚合根 → 持久化对象（ID 集合与附加条件序列化为 JSON）
     *
     * @param aggregate 策略聚合
     * @return 策略持久化对象
     */
    public static SysPermissionStrategyPo toPo(PermissionStrategyAggregate aggregate) {
        SysPermissionStrategyPo po = new SysPermissionStrategyPo();
        po.setId(aggregate.getId());
        po.setTenantId(aggregate.getTenantId());
        po.setStrategyCode(aggregate.getStrategyCode());
        po.setStrategyName(aggregate.getStrategyName());
        po.setStrategyType(aggregate.getStrategyType());
        po.setRoleIds(serializeIds(aggregate.getRoleIds()));
        po.setResourceIds(serializeIds(aggregate.getResourceIds()));
        po.setConditionJson(serializeConditions(aggregate.getConditions()));
        po.setIsDefault(aggregate.isDefaultStrategy() ? 1 : 0);
        po.setPriority(aggregate.getPriority());
        po.setStatus(aggregate.getStatus());
        po.setDescription(aggregate.getDescription());
        po.setCreateTime(aggregate.getCreateTime());
        po.setUpdateTime(aggregate.getUpdateTime());
        return po;
    }

    /**
     * 持久化对象 → 聚合根（JSON 反序列化失败抛参数错误）
     *
     * @param po 策略持久化对象
     * @return 策略聚合
     */
    public static PermissionStrategyAggregate toDomain(SysPermissionStrategyPo po) {
        return PermissionStrategyAggregate.reconstitute(po.getId(), po.getTenantId(), po.getStrategyCode(),
                po.getStrategyName(), po.getStrategyType(), parseIds(po.getRoleIds()),
                parseIds(po.getResourceIds()), parseConditions(po.getConditionJson()),
                po.getIsDefault() != null && po.getIsDefault() == 1, po.getPriority(), po.getStatus(),
                po.getDescription(), po.getCreateTime(), po.getUpdateTime());
    }

    /**
     * 附加条件列表 → JSON（空列表返回 {@code null}，序列化失败抛参数错误）
     *
     * @param conditions 附加条件列表（可空）
     * @return JSON 字符串；空列表返回 {@code null}
     */
    public static String serializeConditions(List<StrategyCondition> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(conditions);
        } catch (JacksonException ex) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "策略条件序列化失败：" + ex.getMessage());
        }
    }

    /**
     * JSON → 附加条件列表（存储为空时返回空列表，反序列化失败抛参数错误）
     *
     * @param conditionJson 条件 JSON（可空）
     * @return 条件列表
     */
    public static List<StrategyCondition> parseConditions(String conditionJson) {
        if (conditionJson == null || conditionJson.isBlank()) {
            return List.of();
        }
        try {
            List<StrategyCondition> conditions = OBJECT_MAPPER
                    .readValue(conditionJson, new TypeReference<List<StrategyCondition>>() { });
            return conditions == null ? List.of() : conditions;
        } catch (JacksonException ex) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "策略条件反序列化失败：" + ex.getMessage());
        }
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
            throw new DomainException(ErrorCode.PARAM_ERROR, "策略目标集合序列化失败：" + ex.getMessage());
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
            throw new DomainException(ErrorCode.PARAM_ERROR, "策略目标集合反序列化失败：" + ex.getMessage());
        }
    }
}
