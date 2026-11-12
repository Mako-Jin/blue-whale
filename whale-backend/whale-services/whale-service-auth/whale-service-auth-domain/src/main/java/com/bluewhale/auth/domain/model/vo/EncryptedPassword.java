package com.bluewhale.auth.domain.model.vo;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.exception.DomainException;
import com.bluewhale.common.domain.valueobject.ValueObject;

import java.util.regex.Pattern;

/**
 * 口令密文值对象：仅承载已加密的口令，禁止承载明文。
 *
 * <p><b>不变量</b>：密文必须为 BCrypt 格式（以 {@code $2a$/$2b$/$2y$} 起始且带盐与散列内容）。
 * 格式校验在本值对象构造时一次性完成，聚合根不再重复校验；加密动作本身由 application 层
 * 调用 {@code PasswordEncoder.encode} 保证，本对象只接受密文。</p>
 *
 * <p>{@link #toString()} 被覆写为掩码，避免密文随日志或异常信息外泄。</p>
 *
 * @param value 加密后的口令（BCrypt 密文）
 * @author BlueWhale
 */
public record EncryptedPassword(String value) implements ValueObject {

    /** BCrypt 密文形态：以 {@code $2a$/$2b$/$2y$} 起始且带盐与散列内容 */
    private static final Pattern BCRYPT_PATTERN = Pattern.compile("^\\$2[aby]\\$.+$");

    /**
     * 紧凑构造器：校验密文非空且为 BCrypt 格式，非法输入直接抛出参数异常
     *
     * @param value 加密后的口令（BCrypt 密文）
     */
    public EncryptedPassword {
        if (value == null || value.isBlank() || !BCRYPT_PATTERN.matcher(value).matches()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "口令必须为 BCrypt 密文");
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
