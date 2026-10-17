package com.bluewhale.auth.domain.model.vo;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;

import java.util.regex.Pattern;

/**
 * 用户名值对象：不可变，构造即校验（非空、不超过 64 位、仅字母/数字/下划线/点/连字符）。
 *
 * @param value 用户名字面值
 * @author BlueWhale
 */
public record Username(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[A-Za-z0-9_.-]{1,64}$");

    public Username {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "用户名须为 1-64 位的字母、数字、下划线、点或连字符");
        }
    }

    public static Username of(String value) {
        return new Username(value);
    }
}
