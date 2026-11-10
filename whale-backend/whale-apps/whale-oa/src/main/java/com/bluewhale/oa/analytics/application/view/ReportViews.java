package com.bluewhale.oa.analytics.application.view;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 数据报表视图集合。
 *
 * @author BlueWhale
 */
public final class ReportViews {

    /** 工具类，禁止实例化。 */
    private ReportViews() {
    }

    /**
     * 报表列定义。
     */
    @Data
    public static class Column {

        /** 字段键（对应行 Map 的 key） */
        private String key;

        /** 列标题 */
        private String label;

        /**
         * 构造列定义。
         *
         * @param key 字段键
         * @param label 列标题
         */
        public Column(String key, String label) {
            this.key = key;
            this.label = label;
        }
    }

    /**
     * 报表目录项。
     */
    @Data
    public static class CatalogItem {

        /** 报表编码 */
        private String code;

        /** 报表名称 */
        private String title;

        /** 报表说明 */
        private String description;

        /** 列定义 */
        private List<Column> columns;
    }

    /**
     * 报表运行结果（前端按列定义渲染通用表格）。
     */
    @Data
    public static class ReportResult {

        /** 报表编码 */
        private String code;

        /** 报表名称 */
        private String title;

        /** 起始日期 */
        private LocalDate from;

        /** 截止日期 */
        private LocalDate to;

        /** 额度年份（仅假期余额） */
        private Integer year;

        /** 列定义 */
        private List<Column> columns;

        /** 数据行 */
        private List<Map<String, Object>> rows;

        /** 数据行数 */
        private Integer rowCount;

        /** 生成时间 */
        private LocalDateTime generatedAt;
    }

    /**
     * 报表快照元信息。
     */
    @Data
    public static class SnapshotView {

        /** 快照ID */
        private Long id;

        /** 报表编码 */
        private String reportCode;

        /** 报表名称 */
        private String reportName;

        /** 统计期间（YYYY-MM） */
        private String period;

        /** 起始日期 */
        private LocalDate fromDate;

        /** 截止日期 */
        private LocalDate toDate;

        /** 数据行数 */
        private Integer rowCount;

        /** 生成人用户ID */
        private Long generatedBy;

        /** 生成人姓名 */
        private String generatedByName;

        /** 生成时间 */
        private LocalDateTime generatedAt;
    }

    /**
     * 报表导出文件。
     */
    @Data
    public static class ExportFile {

        /** 下载文件名 */
        private String filename;

        /** CSV 内容 */
        private byte[] content;

        /** 数据行数 */
        private Integer rowCount;
    }
}
