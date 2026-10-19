package com.bluewhale.form.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 表单定义视图对象（字段名为冻结的 JSON 契约，不得改名）。
 *
 * @author BlueWhale
 */
@Data
public class FormDefinitionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 表单编码 */
    private String code;

    /** 表单名称 */
    private String name;

    /** 表单结构定义（JSON 文本） */
    private String schemaJson;

    /** 版本号 */
    private Integer version;

    /** 状态：1 正常 / 0 禁用 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
