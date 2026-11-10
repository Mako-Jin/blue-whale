package com.bluewhale.form.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 表单物理表登记视图对象（字段名为冻结的 JSON 契约，不得改名）。
 *
 * @author BlueWhale
 */
@Data
public class FormTableRegistryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 表定义 ID（元数据模型；{@code null} 为旧模型） */
    private Long tableDefinitionId;

    /** 表编码（元数据模型；旧模型为空） */
    private String tableCode;

    /** 表版本（元数据模型；旧模型为空） */
    private Integer tableVersion;

    /** 表单定义 ID */
    private Long definitionId;

    /** 表单定义编码 */
    private String definitionCode;

    /** 表单定义版本 */
    private Integer definitionVersion;

    /** 物理表名 */
    private String physicalTable;

    /** 动态字段数量 */
    private Integer fieldCount;

    /** 状态：1 生效 / 0 废弃 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
