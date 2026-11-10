package com.bluewhale.auth.domain.model.vo;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.exception.DomainException;
import com.bluewhale.common.domain.valueobject.ValueObject;

/**
 * 口令密文值对象：仅承载已加密的口令，禁止承载明文。
 *
 * <p>{@link #toString()} 被覆写为掩码，避免密文随日志或异常信息外泄。</p>
 *
 * @param value 加密后的口令（如 BCrypt 密文）
 * @author BlueWhale
 */
public record EncryptedPassword(String value) implements ValueObject {

    /**
     * 紧凑构造器：校验口令密文非空，空值直接抛出参数异常
     *
     * @param value 加密后的口令（如 BCrypt 密文）
     */
    public EncryptedPassword {
        if (value == null || value.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "口令密文不能为空");
        }
    }

    /**
     * 由密文创建口令密文值对象
     *
     * @param value 加密后的口令（如 BCrypt 密文）
     * @return 口令密文值对象
     */
    public static EncryptedPassword of(String value) {
        return new EncryptedPassword(value);
    }

    @Override
    public String toString() {
        return "EncryptedPassword(***)";
    }
}
