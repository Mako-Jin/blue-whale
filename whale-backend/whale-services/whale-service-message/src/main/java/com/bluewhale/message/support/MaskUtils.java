package com.bluewhale.message.support;

import com.bluewhale.message.enums.MessageChannel;
import org.springframework.util.StringUtils;

/**
 * 敏感接收标识脱敏工具。
 *
 * <p>手机号按 {@code 138****1234}、邮箱按 {@code a***@domain} 脱敏；IM/站内信的接收标识
 * 是平台内部用户ID，不属于个人敏感信息，原样返回。</p>
 *
 * @author BlueWhale
 */
public final class MaskUtils {

    /** 手机号最短可脱敏长度 */
    private static final int PHONE_MIN_LENGTH = 7;

    /**
     * 工具类禁止实例化。
     */
    private MaskUtils() {
    }

    /**
     * 按渠道脱敏接收标识。
     *
     * @param channel   渠道编码
     * @param recipient 接收标识
     * @return 脱敏后的值；内部用户ID等非敏感标识原样返回
     */
    public static String maskRecipient(String channel, String recipient) {
        if (!StringUtils.hasText(recipient)) {
            return recipient;
        }
        MessageChannel parsed = MessageChannel.parse(channel);
        if (parsed == MessageChannel.SMS) {
            return maskPhone(recipient.trim());
        }
        if (parsed == MessageChannel.EMAIL) {
            return maskEmail(recipient.trim());
        }
        return recipient;
    }

    /**
     * 手机号脱敏：保留前三后四。
     *
     * @param phone 手机号
     * @return 脱敏值
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < PHONE_MIN_LENGTH) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /**
     * 邮箱脱敏：本地部分只保留首字符。
     *
     * @param email 邮箱地址
     * @return 脱敏值
     */
    public static String maskEmail(String email) {
        if (email == null || email.isEmpty()) {
            return email;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
