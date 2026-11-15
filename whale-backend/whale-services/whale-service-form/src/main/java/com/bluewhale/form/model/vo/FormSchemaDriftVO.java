package com.bluewhale.form.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 物理表结构漂移检测结果视图对象（字段名为冻结的 JSON 契约，不得改名）。
 *
 * <p>漂移指「列元数据 / 发布快照描述的结构」与「information_schema 中物理表实际结构」不一致，
 * 典型成因是人工改库、实体改动漏改脚本、发布中途失败。</p>
 *
 * @author BlueWhale
 */
@Data
public class FormSchemaDriftVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 检测状态：结构一致 */
    public static final String OK = "OK";

    /** 检测状态：存在漂移 */
    public static final String DRIFT = "DRIFT";

    /** 检测状态：物理表不存在 */
    public static final String TABLE_MISSING = "TABLE_MISSING";

    /** 物理表登记ID */
    private Long registryId;

    /** 物理表名 */
    private String physicalTable;

    /** 检测时间 */
    private LocalDateTime checkedAt;

    /** 检测状态：{@code OK} / {@code DRIFT} / {@code TABLE_MISSING} */
    private String status;

    /** 元数据侧列数（期望结构） */
    private Integer expectedColumnCount;

    /** 物理表实际列数（含公共列） */
    private Integer physicalColumnCount;

    /** 元数据有、物理表缺失的列 */
    private List<String> missingInPhysical;

    /** 物理表有、元数据未声明的列（已排除公共列） */
    private List<String> missingInMetadata;

    /** 列类型不一致明细 */
    private List<TypeMismatch> typeMismatches;

    /**
     * 单列类型不一致明细
     */
    @Data
    public static class TypeMismatch implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 列名 */
        private String column;

        /** 元数据期望的 MySQL 列类型（归一化） */
        private String expected;

        /** 物理表实际的 MySQL 列类型（归一化） */
        private String actual;

        /**
         * 构造类型不一致明细
         *
         * @param column 列名
         * @param expected 期望类型
         * @param actual 实际类型
         */
        public TypeMismatch(String column, String expected, String actual) {
            this.column = column;
            this.expected = expected;
            this.actual = actual;
        }

        /**
         * 无参构造器（供反序列化）
         */
        public TypeMismatch() {
        }
    }
}
