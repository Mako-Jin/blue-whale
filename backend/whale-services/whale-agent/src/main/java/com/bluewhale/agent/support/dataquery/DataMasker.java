package com.bluewhale.agent.support.dataquery;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 只读数据脱敏：敏感字段整体遮蔽，手机号与身份证按固定位置掩码。
 *
 * <p>字段名命中 {@code phone/mobile/idCard/secret/token/password/apiKey} 等敏感词时，
 * 值统一替换为 {@code ***}；普通字符串值仅做手机号、身份证模式掩码，
 * 保留可读的统计与业务字段。</p>
 *
 * @author BlueWhale
 */
public final class DataMasker {

    /** 敏感字段名 */
    private static final Pattern SENSITIVE_KEY = Pattern.compile(
            "(?i).*(phone|mobile|tel|id_?card|cert_?no|secret|token|password|passwd|api_?key|access_?key).*");

    /** 手机号：保留前 3 后 4 */
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(1[3-9]\\d)(\\d{4})(\\d{4})(?!\\d)");

    /** 身份证：保留前 6 后 4 */
    private static final Pattern ID_CARD = Pattern.compile("(?<!\\d)(\\d{6})(\\d{8})(\\d{3}[\\dXx])(?!\\d)");

    /** 脱敏占位 */
    private static final String PLACEHOLDER = "***";

    /** 工具类不允许实例化 */
    private DataMasker() {
    }

    /**
     * 脱敏单行结果。
     *
     * @param row 原始行（列名 → 值）
     * @return 新行，不修改入参
     */
    public static Map<String, Object> maskRow(Map<String, Object> row) {
        Map<String, Object> masked = new LinkedHashMap<>();
        if (row == null) {
            return masked;
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            Object value = entry.getValue();
            if (SENSITIVE_KEY.matcher(String.valueOf(entry.getKey())).matches() && value != null) {
                masked.put(entry.getKey(), PLACEHOLDER);
            } else if (value instanceof String text) {
                masked.put(entry.getKey(), maskText(text));
            } else {
                masked.put(entry.getKey(), value);
            }
        }
        return masked;
    }

    /**
     * 脱敏多行结果。
     *
     * @param rows 原始结果
     * @return 新结果列表
     */
    public static List<Map<String, Object>> maskRows(List<Map<String, Object>> rows) {
        List<Map<String, Object>> masked = new ArrayList<>();
        if (rows == null) {
            return masked;
        }
        for (Map<String, Object> row : rows) {
            masked.add(maskRow(row));
        }
        return masked;
    }

    /**
     * 对文本值做手机号与身份证掩码（先身份证，避免被手机号规则拆解）。
     *
     * @param text 原始文本
     * @return 脱敏文本
     */
    private static String maskText(String text) {
        Matcher idCard = ID_CARD.matcher(text);
        String result = idCard.replaceAll("$1********$3");
        Matcher phone = PHONE.matcher(result);
        return phone.replaceAll("$1****$3");
    }
}
