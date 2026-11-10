package com.bluewhale.oa.analytics.application.support;

import com.bluewhale.oa.analytics.application.view.ReportViews;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 报表 CSV 输出。
 *
 * <p>UTF-8 BOM 保证 Excel 直接打开不乱码；对逗号、引号、换行做标准转义，
 * 并对 {@code =}/{@code @} 开头的单元格做公式前缀中和，避免 CSV 注入。</p>
 *
 * @author BlueWhale
 */
public final class ReportCsvWriter {

    /** Excel 兼容用 UTF-8 BOM */
    private static final String BOM = "\uFEFF";

    /** CSV 换行 */
    private static final String CRLF = "\r\n";

    /** 工具类，禁止实例化。 */
    private ReportCsvWriter() {
    }

    /**
     * 生成 CSV 字节内容。
     *
     * @param result 报表结果
     * @return CSV 内容
     */
    public static byte[] write(ReportViews.ReportResult result) {
        StringBuilder builder = new StringBuilder(BOM);
        for (int index = 0; index < result.getColumns().size(); index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(escape(result.getColumns().get(index).getLabel()));
        }
        builder.append(CRLF);
        for (Map<String, Object> row : result.getRows()) {
            for (int index = 0; index < result.getColumns().size(); index++) {
                if (index > 0) {
                    builder.append(',');
                }
                String key = result.getColumns().get(index).getKey();
                builder.append(escape(format(row.get(key))));
            }
            builder.append(CRLF);
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 转义单元格。
     *
     * @param value 原始值
     * @return CSV 单元格
     */
    static String escape(String value) {
        if (value == null) {
            return "";
        }
        String safe = value;
        if (safe.startsWith("=") || safe.startsWith("@")) {
            safe = "'" + safe;
        }
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    /**
     * 值格式化。
     *
     * @param value 原始值
     * @return 文本
     */
    private static String format(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        return String.valueOf(value);
    }
}
