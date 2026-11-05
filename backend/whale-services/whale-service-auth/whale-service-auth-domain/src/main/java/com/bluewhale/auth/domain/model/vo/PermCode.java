package com.bluewhale.auth.domain.model.vo;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.exception.DomainException;
import com.bluewhale.common.domain.valueobject.ValueObject;

import java.util.regex.Pattern;

/**
 * 权限码值对象：冒号分隔的小写段落，至少两段（如 {@code sys:user:list}）。
 *
 * <p>段落允许数字、下划线与连字符：既有 ERP/CRM/MES 权限码大量使用
 * {@code price-list}、{@code follow-up}、{@code work-hour} 等连字风格，
 * 统一放开后避免 {@code /auth/me} 组装权限时整体失败。</p>
 *
 * @param value 权限码字面值
 * @author BlueWhale
 */
public record PermCode(String value) implements ValueObject {

    private static final Pattern PATTERN = Pattern.compile("^[a-z][a-z0-9_-]*(?::[a-z0-9_-]+)+$");

    /**
     * 紧凑构造器：校验权限码符合「冒号分隔的小写段落且至少两段」格式
     *
     * @param value 权限码字面值
     */
    public PermCode {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new DomainException(ErrorCode.PARAM_ERROR,
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
