package com.bluewhale.form.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 列元数据视图对象（表定义层 · 动态列详情）。
 *
 * @author BlueWhale
 */
@Data
public class FormColumnMetaVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 表定义ID */
    private Long tableDefinitionId;

    /** 该列所属表版本 */
    private Integer tableVersion;

    /** 列编码（物理列名） */
    private String columnCode;

    /** 列名称 */
    private String columnName;

    /** 存储类型 */
    private String dataType;

    /** 长度 */
    private Integer length;

    /** 精度 */
    private Integer precision;

    /** 小数位 */
    private Integer scale;

    /** 能否为空：1 可空 / 0 非空 */
    private Integer nullable;

    /** 是否必填：1 必填 / 0 非必填 */
    private Integer required;

    /** 默认值 */
    private String defaultValue;

    /** 备注 */
    private String remark;

    /** 排序号 */
    private Integer sortNo;

    /** 状态：1 正常 / 0 禁用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
