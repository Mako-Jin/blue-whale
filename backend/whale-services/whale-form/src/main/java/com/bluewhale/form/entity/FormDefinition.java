package com.bluewhale.form.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表单定义实体，对应表 {@code form_definition}。
 *
 * <p>公共字段（id / create_time / update_time / deleted）继承自 {@link BaseEntity}，
 * 其余字段与 {@code db/init.sql} 中的 {@code form_definition} 列逐字段对齐。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("form_definition")
public class FormDefinition extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 表单编码（业务唯一；因逻辑删除不建唯一索引，唯一性由服务层前置校验保证） */
    private String code;

    /** 表单名称 */
    private String name;

    /** 表单结构定义（JSON 文本，最长 64KB，即 65536 字符） */
    private String schemaJson;

    /** 版本号（默认 1，每次结构变更可递增） */
    private Integer version;

    /** 状态：1 正常 / 0 禁用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
