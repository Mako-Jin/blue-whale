package com.bluewhale.auth.domain.model.vo;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;

import java.util.regex.Pattern;

/**
 * 角色编码值对象：小写字母开头，仅含小写字母/数字/下划线，长度 2-64。
 *
 * @param value 角色编码字面值（如 {@code super_admin}）
 * @author BlueWhale
 */
public record RoleCode(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[a-z][a-z0-9_]{1,63}$");

    public RoleCode {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "角色编码须为 2-64 位，小写字母开头，仅含小写字母、数字与下划线");
        }
    }

    public static RoleCode of(String value) {
        return new RoleCode(value);
    }
}
