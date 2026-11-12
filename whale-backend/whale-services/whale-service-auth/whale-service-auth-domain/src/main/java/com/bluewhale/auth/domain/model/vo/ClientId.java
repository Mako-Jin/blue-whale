package com.bluewhale.auth.domain.model.vo;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.exception.DomainException;
import com.bluewhale.common.domain.valueobject.ValueObject;

import java.util.regex.Pattern;

/**
 * OAuth2 客户端标识值对象。
 *
 * <p><b>不变量</b>：4–64 位，仅允许字母、数字、下划线、中划线；全局唯一由仓储唯一键保证。</p>
 *
 * @param value 客户端标识字面值（如 {@code bluewhale-web}）
 * @author BlueWhale
 */
public record ClientId(String value) implements ValueObject {

    private static final Pattern PATTERN = Pattern.compile("^[A-Za-z0-9_-]{4,64}$");

    /**
     * 紧凑构造器：校验客户端标识格式
     *
     * @param value 客户端标识字面值
     */
    public ClientId {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new DomainException(ErrorCode.PARAM_ERROR,
                    "客户端标识须为 4–64 位字母、数字、下划线或中划线");
        }
    }

    /**
     * 由字面值创建客户端标识
     *
     * @param value 客户端标识字面值
     * @return 客户端标识值对象
     */
    public static ClientId of(String value) {
        return new ClientId(value);
    }
}
