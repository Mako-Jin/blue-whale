package com.bluewhale.crm.common;

/**
 * CRM 敏感字段脱敏工具（REQ-SEC-034）。
 *
 * <p>手机号保留前 3 位与后 4 位，邮箱保留首字符与域名，税号保留前后各 4 位；
 * 无权限查看明文的调用方一律经本工具输出。</p>
 *
 * @author BlueWhale
 */
public final class CrmMasking {

    /** 脱敏掩码 */
    private static final String MASK = "****";

    /**
     * 私有构造器：工具类，禁止实例化。
     */
    private CrmMasking() {
    }

    /**
     * 手机号脱敏。
     *
     * @param phone 手机号
     * @return 脱敏手机号；空值原样返回
     */
    public static String phone(String phone) {
        if (phone == null || phone.isBlank()) {
            return phone;
        }
        String text = phone.trim();
        if (text.length() <= 4) {
            return MASK;
        }
        if (text.length() <= 7) {
            return text.charAt(0) + MASK;
        }
        return text.substring(0, 3) + MASK + text.substring(text.length() - 4);
    }

    /**
     * 邮箱脱敏。
     *
     * @param email 邮箱
     * @return 脱敏邮箱；空值原样返回
     */
    public static String email(String email) {
        if (email == null || email.isBlank()) {
            return email;
        }
        String text = email.trim();
        int at = text.indexOf('@');
        if (at <= 0) {
            return MASK;
        }
        return text.charAt(0) + MASK + text.substring(at);
    }

    /**
     * 税号脱敏。
     *
     * @param taxNo 纳税人识别号
     * @return 脱敏税号；空值原样返回
     */
    public static String taxNo(String taxNo) {
        if (taxNo == null || taxNo.isBlank()) {
            return taxNo;
        }
        String text = taxNo.trim();
        if (text.length() <= 8) {
            return MASK;
        }
        return text.substring(0, 4) + MASK + text.substring(text.length() - 4);
    }

    /**
     * 按权限决定是否脱敏手机号。
     *
     * @param phone 手机号
     * @param allowPlain 是否允许明文
     * @return 明文或脱敏值
     */
    public static String phone(String phone, boolean allowPlain) {
        return allowPlain ? phone : phone(phone);
    }

    /**
     * 按权限决定是否脱敏邮箱。
     *
     * @param email 邮箱
     * @param allowPlain 是否允许明文
     * @return 明文或脱敏值
     */
    public static String email(String email, boolean allowPlain) {
        return allowPlain ? email : email(email);
    }

    /**
     * 按权限决定是否脱敏税号。
     *
     * @param taxNo 税号
     * @param allowPlain 是否允许明文
     * @return 明文或脱敏值
     */
    public static String taxNo(String taxNo, boolean allowPlain) {
        return allowPlain ? taxNo : taxNo(taxNo);
    }

    /**
     * 微信号脱敏：保留前 2 位与后 2 位。
     *
     * @param wechat 微信号
     * @param allowPlain 是否允许明文
     * @return 明文或脱敏值；空值原样返回
     */
    public static String wechat(String wechat, boolean allowPlain) {
        if (allowPlain || wechat == null || wechat.isBlank()) {
            return wechat;
        }
        String text = wechat.trim();
        if (text.length() <= 4) {
            return MASK;
        }
        return text.substring(0, 2) + MASK + text.substring(text.length() - 2);
    }

    /**
     * 长文本备注脱敏：无权限时整体隐藏。
     *
     * @param text 备注
     * @param allowPlain 是否允许明文
     * @return 明文或固定掩码；空值原样返回
     */
    public static String note(String text, boolean allowPlain) {
        if (text == null || text.isBlank()) {
            return text;
        }
        return allowPlain ? text : "******";
    }
}
