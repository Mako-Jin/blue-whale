package com.bluewhale.mail.support;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 邮件地址工具：地址列表与逗号分隔字符串互转。
 *
 * <p>入库统一小写、去空白、去重并保持输入顺序；展示层不做二次排序，
 * 避免收件人顺序与用户输入不一致引发困惑。</p>
 *
 * @author BlueWhale
 */
public final class MailAddressUtils {

    /**
     * 私有构造器：工具类，禁止实例化
     */
    private MailAddressUtils() {
    }

    /**
     * 地址列表转逗号分隔字符串
     *
     * @param addresses 地址列表
     * @return 逗号分隔字符串；空列表返回 {@code null}
     */
    public static String join(List<String> addresses) {
        if (addresses == null || addresses.isEmpty()) {
            return null;
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String address : addresses) {
            if (address != null && !address.isBlank()) {
                unique.add(address.trim().toLowerCase(Locale.ROOT));
            }
        }
        return unique.isEmpty() ? null : String.join(",", unique);
    }

    /**
     * 逗号分隔字符串转地址列表
     *
     * @param text 逗号分隔字符串
     * @return 地址列表；空值返回空列表
     */
    public static List<String> split(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String part : text.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /**
     * 提取邮箱域名
     *
     * @param email 邮箱地址
     * @return 域名；无法解析时返回 {@code null}
     */
    public static String domainOf(String email) {
        if (email == null) {
            return null;
        }
        int at = email.lastIndexOf('@');
        if (at <= 0 || at == email.length() - 1) {
            return null;
        }
        return email.substring(at + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 规范化邮箱地址（去空白 + 小写）
     *
     * @param email 原始地址
     * @return 规范化地址
     */
    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
