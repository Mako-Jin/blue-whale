package com.bluewhale.job.scheduler.archive;

import com.bluewhale.job.po.JobLogPo;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * {@code job_log} 归档 CSV 文本生成器。
 *
 * <p>输出 UTF-8（含 BOM）文本，首行表头，行尾使用 {@code \n}；表头与数据列严格一致：
 * {@code id,jobId,jobName,handlerBean,startTime,endTime,status,result,errorMsg}。
 * 字符串字段按 CSV 规则转义：含 {@code ,} / {@code "} / 换行时以双引号包裹，内部双引号加倍。</p>
 *
 * @author BlueWhale
 */
public final class JobLogCsvWriter {

    /** UTF-8 BOM，确保以 Excel 打开时正确识别中文编码 */
    private static final String BOM = "\uFEFF";

    /** CSV 行尾 */
    private static final String LINE_SEPARATOR = "\n";

    /** CSV 表头（与数据列一一对应） */
    private static final String HEADER = "id,jobId,jobName,handlerBean,startTime,endTime,status,result,errorMsg";

    /** 字段间分隔符 */
    private static final String DELIMITER = ",";

    /** 时间字段格式 */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 私有构造器：工具类禁止实例化
     */
    private JobLogCsvWriter() {
    }

    /**
     * 生成归档 CSV 文本
     *
     * @param logs 待归档的执行日志（可为空列表）
     * @return CSV 文本（UTF-8 BOM + 表头 + 数据行）
     */
    public static String write(List<JobLogPo> logs) {
        StringBuilder builder = new StringBuilder(BOM).append(HEADER).append(LINE_SEPARATOR);
        for (JobLogPo log : logs) {
            builder.append(line(log)).append(LINE_SEPARATOR);
        }
        return builder.toString();
    }

    /**
     * 生成单行数据记录
     *
     * @param log 执行日志
     * @return 一行 CSV 数据（不含行尾符）
     */
    private static String line(JobLogPo log) {
        return String.join(DELIMITER,
                text(log.getId()),
                text(log.getJobId()),
                escape(log.getJobName()),
                escape(log.getHandlerBean()),
                time(log.getStartTime()),
                time(log.getEndTime()),
                text(log.getStatus()),
                escape(log.getResult()),
                escape(log.getErrorMsg()));
    }

    /**
     * 数值字段转文本
     *
     * @param value 字段值
     * @return 字段文本；空值返回空串
     */
    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    /**
     * 时间字段转文本
     *
     * @param value 时间值
     * @return 格式化后的时间文本；空值返回空串
     */
    private static String time(LocalDateTime value) {
        return value == null ? "" : value.format(TIME_FORMATTER);
    }

    /**
     * 按 CSV 规则转义字符串字段
     *
     * @param value 字段值
     * @return 转义后的字段文本；空值返回空串
     */
    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(DELIMITER) || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
