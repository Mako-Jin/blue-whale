package com.bluewhale.auth.domain.model.vo;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;

/**
 * 口令密文值对象：仅承载已加密的口令，禁止承载明文。
 *
 * <p>{@link #toString()} 被覆写为掩码，避免密文随日志或异常信息外泄。</p>
 *
 * @param value 加密后的口令（如 BCrypt 密文）
 * @author BlueWhale
 */
public record EncryptedPassword(String value) {

    public EncryptedPassword {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "口令密文不能为空");
        }
    }

    public static EncryptedPassword of(String value) {
        return new EncryptedPassword(value);
    }

    @Override
    public String toString() {
        return "EncryptedPassword(***)";
    }
}
