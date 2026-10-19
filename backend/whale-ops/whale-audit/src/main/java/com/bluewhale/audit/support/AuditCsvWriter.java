package com.bluewhale.audit.support;

import com.bluewhale.common.audit.entity.AuditLogRecord;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 审计日志 CSV 生成器（AUTH-08 导出/归档共用）。
 *
 * <p>输出规则（冻结）：UTF-8 编码、首行表头、行尾统一 {@code \n}、字段按 RFC 4180 做 CSV 转义
 * （含分隔符 {@code ,}、双引号或换行时以双引号包裹，内部双引号加倍）；内容前置 UTF-8 BOM
 * （{@code \uFEFF}），保证 Excel 等工具直接打开中文不乱码。</p>
 *
 * <p>无状态工具类，不持有业务状态。</p>
 *
 * @author BlueWhale
 */
public final class AuditCsvWriter {

    /** UTF-8 BOM，置于内容最前，供 Excel 识别编码 */
    private static final String BOM = "\uFEFF";

    /** 字段分隔符 */
    private static final String DELIMITER = ",";

    /** 行分隔符（统一 LF） */
    private static final String LINE_SEPARATOR = "\n";

    /** 时间列格式 */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 归档文件名时间戳格式 */
    private static final DateTimeFormatter FILE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 归档文件名前缀 */
    private static final String FILE_NAME_PREFIX = "auditLog-archive-";

    /** CSV 表头（顺序与 {@link #toLine(AuditLogRecord)} 单元格顺序严格一致） */
    private static final String[] HEADERS = {
            "id", "traceId", "userId", "tenantId", "username", "module", "operation", "operateType",
            "method", "requestUri", "httpMethod", "targetType", "targetId", "requestParams", "resultBody",
            "responseCode", "bizStatus", "errorMsg", "ip", "userAgent", "costMs", "createTime"};

    /**
     * 私有构造：工具类禁止实例化
     */
    private AuditCsvWriter() {
    }

    /**
     * 将审计记录列表渲染为 CSV 字节数组（含 BOM 与表头）
     *
     * @param records 审计记录列表（可为空，空列表仅输出表头）
     * @return UTF-8 CSV 字节数组（以 BOM 开头）
     */
    public static byte[] toCsvBytes(List<AuditLogRecord> records) {
        StringBuilder builder = new StringBuilder(BOM);
        builder.append(String.join(DELIMITER, HEADERS)).append(LINE_SEPARATOR);
        for (AuditLogRecord record : records) {
            builder.append(toLine(record)).append(LINE_SEPARATOR);
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 生成归档文件名：{@code auditLog-archive-{yyyyMMddHHmmss}.csv}
     *
     * @return 归档 CSV 文件名
     */
    public static String archiveFileName() {
        return FILE_NAME_PREFIX + FILE_TIME_FORMAT.format(LocalDateTime.now()) + ".csv";
    }

    /**
     * 将单条审计记录渲染为一行 CSV（字段顺序与 {@link #HEADERS} 一致）
     *
     * @param record 审计记录
     * @return 转义后的单行 CSV 文本（不含行尾分隔符）
     */
    private static String toLine(AuditLogRecord record) {
        String[] cells = {
                text(record.getId()), text(record.getTraceId()), text(record.getUserId()), text(record.getTenantId()),
                text(record.getUsername()), text(record.getModule()), text(record.getOperation()),
                text(record.getOperateType()), text(record.getMethod()), text(record.getRequestUri()),
                text(record.getHttpMethod()), text(record.getTargetType()), text(record.getTargetId()),
                text(record.getRequestParams()), text(record.getResultBody()), text(record.getResponseCode()),
                text(record.getBizStatus()), text(record.getErrorMsg()), text(record.getIp()),
                text(record.getUserAgent()), text(record.getCostMs()), text(record.getCreateTime())};
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                line.append(DELIMITER);
            }
            line.append(escape(cells[i]));
        }
        return line.toString();
    }

    /**
     * 空值安全转字符串（{@code null} 转为空串；{@code LocalDateTime} 按固定格式输出）
     *
     * @param value 原始值
     * @return 字符串表示；{@code null} 返回空串
     */
    private static String text(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof LocalDateTime dateTime) {
            return TIME_FORMAT.format(dateTime);
        }
        return value.toString();
    }

    /**
     * CSV 字段转义：含分隔符、双引号或换行时以双引号包裹并将内部双引号加倍
     *
     * @param value 待转义字段值（非空）
     * @return 转义后的字段值
     */
    private static String escape(String value) {
        if (value.indexOf('"') >= 0 || value.indexOf(',') >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }
}
