package com.bluewhale.form.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 列元数据请求（表定义层 · 动态列详情）。
 *
 * @author BlueWhale
 */
@Data
public class FormColumnMetaDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 列编码（物理列名；由服务层校验白名单） */
    private String columnCode;

    /** 列名称（业务名） */
    private String columnName;

    /** 存储类型：text / textarea / number / date / datetime / select / bool */
    private String dataType;

    /** 长度（text 生效；缺省 255） */
    private Integer length;

    /** 精度（number 生效；缺省 18） */
    private Integer precision;

    /** 小数位（number 生效；缺省 4） */
    private Integer scale;

    /** 能否为空：1 可空 / 0 非空；缺省 1 */
    private Integer nullable;

    /** 是否必填：1 必填 / 0 非必填；缺省 0 */
    private Integer required;

    /** 默认值 */
    private String defaultValue;

    /** 备注 */
    private String remark;

    /** 排序号 */
    private Integer sortNo;

    /** 状态：1 正常 / 0 禁用；缺省 1 */
    private Integer status;
}
