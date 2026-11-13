package com.bluewhale.auth.domain.model.vo;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.exception.DomainException;

import java.util.List;

/**
 * 权限策略附加条件值对象（权限中心扩展 M3）。
 *
 * <p>以 JSON 数组形式持久化（TEXT 列），形如
 * {@code [{"field":"attr.deptId","op":"eq","value":10}]}；语义为「角色-资源」授权/拒绝的
 * 附加生效条件，为空表示无条件。</p>
 *
 * @param field 字段名（用户基础字段或 ABAC 属性键）
 * @param op 操作符（eq / ne / in / like）
 * @param value 比较值（in 时为数组）
 * @author BlueWhale
 */
public record StrategyCondition(String field, String op, Object value) {

    /**
     * 构造并校验条件项
     *
     * @param field 字段名（用户基础字段或 ABAC 属性键，不可空）
     * @param op 操作符（eq / ne / in / like）
     * @param value 比较值（不可空）
     * @throws DomainException 字段、操作符或比较值非法时抛出
     */
    public StrategyCondition {
        if (field == null || field.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "策略条件字段名不能为空");
        }
        if (op == null || op.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "策略条件操作符不能为空");
        }
        if (value == null) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "策略条件比较值不能为空");
        }
        field = field.trim();
        op = op.trim().toLowerCase(java.util.Locale.ROOT);
        if (!List.of("eq", "ne", "in", "like").contains(op)) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "策略条件操作符非法，仅支持 eq / ne / in / like");
        }
    }
}
