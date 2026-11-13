package com.bluewhale.auth.domain.model.vo;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.exception.DomainException;

import java.util.Arrays;

/**
 * 角色继承策略值对象（权限中心扩展 M3）。
 *
 * <p>{@code FULL} 全量继承父角色权限；{@code INCREMENT} 增量继承（仅新增父角色新增权限）；
 * {@code OVERRIDE} 覆盖继承（以本角色权限为准，父角色仅作层级展示）；{@code NONE} 不继承。</p>
 *
 * @author BlueWhale
 */
public enum InheritStrategy {

    /** 全量继承父角色权限 */
    FULL,
    /** 增量继承：仅自动获得父角色后续新增的权限 */
    INCREMENT,
    /** 覆盖继承：以本角色自身权限为准 */
    OVERRIDE,
    /** 不继承 */
    NONE;

    /**
     * 解析继承策略字符串（空值回落 {@link #NONE}）
     *
     * @param value 策略字符串（可空）
     * @return 继承策略枚举
     * @throws DomainException 非法取值时抛出参数错误
     */
    public static InheritStrategy of(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }
        return Arrays.stream(values())
                .filter(v -> v.name().equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElseThrow(() -> new DomainException(ErrorCode.PARAM_ERROR,
                        "继承策略非法，仅支持 FULL / INCREMENT / OVERRIDE / NONE"));
    }
}
