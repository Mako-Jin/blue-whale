package com.bluewhale.auth.domain.model.vo;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.exception.DomainException;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 用户组过滤规则值对象（权限中心扩展 M3）。
 *
 * <p>仅 DYNAMIC（动态）组使用：以 JSON 数组形式持久化（TEXT 列），形如
 * {@code [{"field":"deptId","op":"eq","value":10}]}；{@code op} 限定为
 * {@code eq / ne / in / like} 四种，{@code field} 为用户基础字段或 ABAC 属性键。</p>
 *
 * @param conditions 条件列表（可为空列表，表示不筛选任何用户）
 * @author BlueWhale
 */
public record GroupFilters(List<Condition> conditions) {

    /** 允许的条件操作符 */
    private static final Set<String> ALLOWED_OPS = Set.of("eq", "ne", "in", "like");
    /** 单组条件数量上限 */
    public static final int MAX_CONDITIONS = 20;

    /**
     * 条件项
     *
     * @param field 字段名（用户基础字段或 ABAC 属性键）
     * @param op 操作符（eq / ne / in / like）
     * @param value 比较值（in 时为数组）
     */
    public record Condition(String field, String op, Object value) {
    }

    /**
     * 构造并校验过滤规则
     *
     * @param conditions 条件列表（可为空列表，表示不筛选任何用户）
     * @throws DomainException 条件为 {@code null}、超量或操作符非法时抛出
     */
    public GroupFilters {
        if (conditions == null) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "过滤规则不能为 null");
        }
        if (conditions.size() > MAX_CONDITIONS) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "过滤规则最多 " + MAX_CONDITIONS + " 条");
        }
        for (Condition c : conditions) {
            if (c.field() == null || c.field().isBlank()) {
                throw new DomainException(ErrorCode.PARAM_ERROR, "过滤规则字段名不能为空");
            }
            if (c.op() == null || !ALLOWED_OPS.contains(c.op().toLowerCase(Locale.ROOT))) {
                throw new DomainException(ErrorCode.PARAM_ERROR, "过滤规则操作符非法，仅支持 eq / ne / in / like");
            }
            if (c.value() == null) {
                throw new DomainException(ErrorCode.PARAM_ERROR, "过滤规则比较值不能为空");
            }
        }
        conditions = List.copyOf(conditions);
    }

    /**
     * 工厂方法：由条件列表构建过滤规则
     *
     * @param conditions 条件列表
     * @return 过滤规则值对象
     */
    public static GroupFilters of(List<Condition> conditions) {
        return new GroupFilters(conditions);
    }

    /**
     * 空规则（不筛选任何用户）
     *
     * @return 空过滤规则
     */
    public static GroupFilters empty() {
        return new GroupFilters(List.of());
    }
}
