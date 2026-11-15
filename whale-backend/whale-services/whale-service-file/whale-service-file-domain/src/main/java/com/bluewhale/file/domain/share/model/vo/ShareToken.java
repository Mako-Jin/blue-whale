package com.bluewhale.file.domain.share.model.vo;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.exception.DomainException;
import com.bluewhale.common.domain.valueobject.ValueObject;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 分享令牌值对象：32 字节密码学随机数的 Base64 URL 无填充编码。
 *
 * <p>令牌由服务端生成并持久化，不可枚举、不携带业务信息；不使用无状态签名令牌，
 * 是因为撤销、下载计数与密码校验都必须依赖服务端状态。</p>
 *
 * @author BlueWhale
 */
public final class ShareToken implements ValueObject {

    /** 随机字节数：256 位熵 */
    private static final int TOKEN_BYTES = 32;

    /** 令牌最短长度（兼容外部导入的旧令牌） */
    private static final int MIN_LENGTH = 16;

    /** 令牌最长长度：与 {@code file_share.token} 列宽一致 */
    private static final int MAX_LENGTH = 64;

    /** 令牌合法字符集：URL 安全，无需转义即可放入链接 */
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[A-Za-z0-9_-]+");

    /** 令牌共享的随机源：SecureRandom 线程安全 */
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 令牌原文 */
    private final String value;

    /**
     * 私有构造器：仅允许通过工厂方法创建合法令牌
     *
     * @param value 令牌原文
     */
    private ShareToken(String value) {
        this.value = value;
    }

    /**
     * 生成新令牌
     *
     * @return 43 字符的 Base64 URL 令牌
     */
    public static ShareToken generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return new ShareToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
    }

    /**
     * 以已有令牌原文重建值对象（访问链接与持久化重建场景）
     *
     * @param raw 令牌原文
     * @return 令牌值对象
     * @throws DomainException 令牌为空、长度不合法或含非法字符时抛出 {@code PARAM_ERROR}
     */
    public static ShareToken of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "分享令牌不能为空");
        }
        String value = raw.trim();
        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH || !TOKEN_PATTERN.matcher(value).matches()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "分享令牌格式非法");
        }
        return new ShareToken(value);
    }

    /**
     * 获取令牌原文
     *
     * @return 令牌原文
     */
    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof ShareToken token && value.equals(token.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
