package com.bluewhale.crm.customer.domain.model;

import java.util.List;
import java.util.Locale;

/**
 * 客户查重归一化工具（M1-4）。
 *
 * <p>归一化只用于比较，不覆盖原始录入值；硬冲突比较名称、税号与域名，
 * 软冲突比较联系人电话与邮箱。</p>
 *
 * @author BlueWhale
 */
public final class CustomerNormalizer {

    /** 需要剔除的标点与空白 */
    private static final String PUNCTUATION = "[\\s\\p{Punct}，。、；：（）【】《》·“”‘’]";

    /** 企业通用后缀（按长度倒序剔除） */
    private static final List<String> SUFFIXES = List.of(
            "股份有限公司", "有限责任公司", "有限公司", "集团", "公司");

    /** 协议前缀 */
    private static final String HTTP = "http://";

    /** 安全协议前缀 */
    private static final String HTTPS = "https://";

    /**
     * 私有构造器：工具类，禁止实例化。
     */
    private CustomerNormalizer() {
    }

    /**
     * 归一化客户名称。
     *
     * @param name 客户名称
     * @return 归一化名称；空值返回 null
     */
    public static String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String text = name.trim().toLowerCase(Locale.ROOT).replaceAll(PUNCTUATION, "");
        for (String suffix : SUFFIXES) {
            if (text.length() > suffix.length() && text.endsWith(suffix)) {
                text = text.substring(0, text.length() - suffix.length());
                break;
            }
        }
        return text.isEmpty() ? null : text;
    }

    /**
     * 归一化官网域名。
     *
     * @param website 官网地址
     * @return 归一化域名；空值返回 null
     */
    public static String normalizeDomain(String website) {
        if (website == null || website.isBlank()) {
            return null;
        }
        String text = website.trim().toLowerCase(Locale.ROOT);
        if (text.startsWith(HTTPS)) {
            text = text.substring(HTTPS.length());
        } else if (text.startsWith(HTTP)) {
            text = text.substring(HTTP.length());
        }
        int slash = text.indexOf('/');
        if (slash >= 0) {
            text = text.substring(0, slash);
        }
        int colon = text.indexOf(':');
        if (colon >= 0) {
            text = text.substring(0, colon);
        }
        if (text.startsWith("www.")) {
            text = text.substring(4);
        }
        return text.isEmpty() ? null : text;
    }

    /**
     * 归一化纳税人识别号。
     *
     * @param taxNo 税号
     * @return 大写去符号税号；空值返回 null
     */
    public static String normalizeTaxNo(String taxNo) {
        if (taxNo == null || taxNo.isBlank()) {
            return null;
        }
        String text = taxNo.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        return text.isEmpty() ? null : text;
    }

    /**
     * 归一化电话。
     *
     * @param phone 电话
     * @return 纯数字电话（去掉国家码 86）；空值返回 null
     */
    public static String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String text = phone.trim().replaceAll("[^0-9]", "");
        if (text.startsWith("0086")) {
            text = text.substring(4);
        } else if (text.startsWith("86") && text.length() > 11) {
            text = text.substring(2);
        }
        return text.isEmpty() ? null : text;
    }

    /**
     * 归一化邮箱。
     *
     * @param email 邮箱
     * @return 小写邮箱；空值返回 null
     */
    public static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
