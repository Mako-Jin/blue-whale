package com.bluewhale.auth.domain.model.vo;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;

import java.util.regex.Pattern;

/**
 * 权限码值对象：冒号分隔的小写段落，至少两段（如 {@code sys:user:list}）。
 *
 * @param value 权限码字面值
 * @author BlueWhale
 */
public record PermCode(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[a-z][a-z0-9]*(?::[a-z0-9]+)+$");

    /**
     * 紧凑构造器：校验权限码符合「冒号分隔的小写段落且至少两段」格式
     *
     * @param value 权限码字面值
     */
    public PermCode {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "权限码须为冒号分隔的小写段落且至少两段，如 sys:user:list");
        }
    }

    /**
     * 由字面值创建权限码值对象
     *
     * @param value 权限码字面值
     * @return 权限码值对象
     */
    public static PermCode of(String value) {
        return new PermCode(value);
    }
}
