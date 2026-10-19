package com.bluewhale.workflow.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 流程定义视图对象（字段名为冻结的 JSON 契约，不得改名）。
 *
 * @author BlueWhale
 */
@Data
public class ProcessDefinitionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 流程编码 */
    private String code;

    /** 流程名称 */
    private String name;

    /** 流程定义（JSON 文本，节点与流转） */
    private String definitionJson;

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
