package com.bluewhale.form.support;

import lombok.Data;

import java.util.List;

/**
 * 表单字段定义值对象（由 {@link FormSchemaParser} 从 {@code schema_json} 解析得到）。
 *
 * <p>除基础字段外，{@code attachment} 使用 {@code multiple/accept/maxCount}，
 * {@code subtable} 使用 {@code children/minRows/maxRows}。</p>
 *
 * @author BlueWhale
 */
@Data
public class FormField {

    /** 字段 key（同时作为物理表列名，需匹配 {@code ^[a-z][a-z0-9_]{0,63}$}） */
    private String key;

    /** 字段显示名（缺省等于 key） */
    private String label;

    /** 字段类型（白名单：text / textarea / number / date / datetime / select / bool / attachment / subtable） */
    private String type;

    /** 是否必填（仅服务层校验，不做 DB NOT NULL） */
    private boolean required;

    /** 文本最大长度（仅 text 生效，1..2000，缺省 255） */
    private Integer maxLength;

    /** 附件是否允许多文件（仅 attachment 生效，缺省 true） */
    private Boolean multiple;

    /** 附件允许的文件类型（仅 attachment 生效，如 {@code .pdf,.jpg}，可空表示不限制） */
    private String accept;

    /** 附件最大数量（仅 attachment 生效，0 表示不限制，缺省 0） */
    private Integer maxCount;

    /** 子字段定义（仅 subtable 生效，子字段仅允许基础类型） */
    private List<FormField> children;

    /** 子表最少行数（仅 subtable 生效，缺省 0） */
    private Integer minRows;

    /** 子表最多行数（仅 subtable 生效，0 表示不限制，缺省 0） */
    private Integer maxRows;

    /** 选项值列表（仅 select / 子字段 select 使用，可空） */
    private List<String> options;

    /**
     * 基础字段构造器（兼容既有 DDL 映射与单测）
     *
     * @param key 字段 key
     * @param label 字段显示名
     * @param type 字段类型
     * @param required 是否必填
     * @param maxLength 文本最大长度
     */
    public FormField(String key, String label, String type, boolean required, Integer maxLength) {
        this.key = key;
        this.label = label;
        this.type = type;
        this.required = required;
        this.maxLength = maxLength;
    }

    /**
     * 无参构造器（供解析器逐属性赋值）
     */
    public FormField() {
    }
}
