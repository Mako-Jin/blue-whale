package com.bluewhale.form.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 列元数据实体，对应表 {@code form_column_meta}（三层模型的表定义层·动态列详情）。
 *
 * <p>记录表的列：编码、名称、<b>存储类型</b>{@code dataType}、长度、精度、是否可空、是否必填、
 * 默认值、备注、排序。存储类型驱动物理表 DDL 生成；前端控件类型由表单视图层的
 * {@link FormFieldConfig#getWidgetType()} 决定（二者解耦）。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("form_column_meta")
public class FormColumnMeta extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 表定义 ID */
    private Long tableDefinitionId;

    /** 该列所属表版本（发布 {@code version=N} 取 {@code table_version=N} 且 status=1 的列） */
    private Integer tableVersion;

    /** 列编码（= 物理列名；规则 {@code ^[a-z][a-z0-9_]{0,63}$}，不得命中保留列） */
    private String columnCode;

    /** 列名称（业务名） */
    private String columnName;

    /** 存储类型：text / textarea / number / date / datetime / select / bool */
    private String dataType;

    /** 长度（{@code text} 生效；缺省 255，范围 1..2000） */
    private Integer length;

    /** 精度（{@code number} 生效；缺省 18）。列名 {@code precision} 为 MySQL 保留字，故显式加反引号 */
    @TableField("`precision`")
    private Integer precision;

    /** 小数位（{@code number} 生效；缺省 4） */
    private Integer scale;

    /** 能否为空：1 可空 / 0 非空 */
    private Integer nullable;

    /** 是否必填：1 必填 / 0 非必填（服务层校验） */
    private Integer required;

    /** 默认值 */
    private String defaultValue;

    /** 备注 */
    private String remark;

    /** 排序号 */
    private Integer sortNo;

    /** 状态：1 正常 / 0 禁用 */
    private Integer status;
}
