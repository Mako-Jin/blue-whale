package com.bluewhale.pdm.document.support;

import com.bluewhale.pdm.document.entity.PdmDocDocument;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 图文档列表 CSV 生成器
 *
 * <p>输出规则：UTF-8 编码、首行表头、行尾统一 {@code \n}、字段按 RFC 4180 转义
 * （含分隔符、双引号或换行时以双引号包裹且内部双引号加倍）；内容前置 UTF-8 BOM，
 * 保证 Excel 直接打开中文不乱码。列固定为十列，不含人员敏感信息。</p>
 *
 * @author BlueWhale
 */
public final class PdmDocCsvWriter {

    /** UTF-8 BOM，置于内容最前，供 Excel 识别编码 */
    private static final String BOM = "\uFEFF";

    /** 字段分隔符 */
    private static final String DELIMITER = ",";

    /** 行分隔符（统一 LF） */
    private static final String LINE_SEPARATOR = "\n";

    /** 时间列格式 */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** CSV 表头（顺序与 {@link #toLine(PdmDocDocument)} 单元格顺序严格一致） */
    private static final String[] HEADERS = {
            "文档编号", "标题", "类型", "状态", "所属目录ID", "零件号", "设计师", "最新版本号", "创建时间", "更新时间"};

    /**
     * 私有构造：工具类禁止实例化
     */
    private PdmDocCsvWriter() {
    }

    /**
     * 将文档列表渲染为 CSV 字节数组（含 BOM 与表头）
     *
     * @param documents 文档列表（可为空，空列表仅输出表头）
     * @return UTF-8 CSV 字节数组（以 BOM 开头）
     */
    public static byte[] toCsvBytes(List<PdmDocDocument> documents) {
        StringBuilder builder = new StringBuilder(BOM);
        builder.append(String.join(DELIMITER, HEADERS)).append(LINE_SEPARATOR);
        for (PdmDocDocument document : documents) {
            builder.append(toLine(document)).append(LINE_SEPARATOR);
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 渲染单条文档为一行 CSV（字段顺序与表头一致）
     *
     * @param document 文档实体
     * @return CSV 行（不含行尾换行）
     */
    private static String toLine(PdmDocDocument document) {
        return String.join(DELIMITER,
                escape(document.getDocNo()),
                escape(document.getTitle()),
                escape(document.getDocType()),
                escape(document.getStatus()),
                escape(document.getFolderId() == null ? null : String.valueOf(document.getFolderId())),
                escape(document.getPartNo()),
                escape(document.getOwnerName()),
                escape(document.getLatestVersionNo()),
                escape(formatTime(document.getCreateTime())),
                escape(formatTime(document.getUpdateTime())));
    }

    /**
     * 按 RFC 4180 转义字段
     *
     * @param value 原始值
     * @return 转义后的字段值；空值返回空串
     */
    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0
                && value.indexOf('\n') < 0 && value.indexOf('\r') < 0) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    /**
     * 格式化时间列
     *
     * @param value 时间
     * @return {@code yyyy-MM-dd HH:mm:ss}；空值返回空串
     */
    private static String formatTime(LocalDateTime value) {
        return value == null ? "" : TIME_FORMAT.format(value);
    }
}
