package com.bluewhale.form.support;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 表单字段定义值对象（由 {@link FormSchemaParser} 从 {@code schema_json} 解析得到）。
 *
 * @author BlueWhale
 */
@Data
@AllArgsConstructor
public class FormField {

    /** 字段 key（同时作为物理表列名，需匹配 {@code ^[a-z][a-z0-9_]{0,63}$}） */
    private String key;

    /** 字段显示名（缺省等于 key） */
    private String label;

    /** 字段类型（白名单：text / textarea / number / date / datetime / select / bool） */
    private String type;

    /** 是否必填（仅服务层校验，不做 DB NOT NULL） */
    private boolean required;

    /** 文本最大长度（仅 text 生效，1..2000，缺省 255） */
    private Integer maxLength;
}
