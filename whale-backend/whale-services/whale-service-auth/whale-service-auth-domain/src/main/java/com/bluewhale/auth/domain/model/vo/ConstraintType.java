package com.bluewhale.auth.domain.model.vo;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.exception.DomainException;

import java.util.Arrays;

/**
 * 角色约束类型值对象（权限中心扩展 M3）。
 *
 * <p>{@code MUTEX} 互斥约束：约束覆盖的角色两两互斥，同一用户不得同时持有；
 * {@code CARDINALITY} 基数约束：约束覆盖的角色集合中，同一用户最多可同时持有
 * {@code maxAssign} 个。</p>
 *
 * @author BlueWhale
 */
public enum ConstraintType {

    /** 互斥约束（≥2 个角色两两互斥） */
    MUTEX,
    /** 基数约束（最多可同时分配 maxAssign 个角色） */
    CARDINALITY;

    /**
     * 解析约束类型字符串
     *
     * @param value 类型字符串（不可空）
     * @return 约束类型枚举
     * @throws DomainException 非法取值时抛出参数错误
     */
    public static ConstraintType of(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "约束类型不能为空");
        }
        return Arrays.stream(values())
                .filter(v -> v.name().equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElseThrow(() -> new DomainException(ErrorCode.PARAM_ERROR,
                        "约束类型非法，仅支持 MUTEX / CARDINALITY"));
    }
}
