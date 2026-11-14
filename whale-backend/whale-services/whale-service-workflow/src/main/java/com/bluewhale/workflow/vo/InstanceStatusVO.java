package com.bluewhale.workflow.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 流程实例状态摘要视图对象（批量状态查询用）。
 *
 * @author BlueWhale
 */
@Data
public class InstanceStatusVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 流程实例ID */
    private Long id;

    /** 业务键 */
    private String businessKey;

    /** 状态：0 运行中 / 1 已通过 / 2 已驳回 / 3 已撤销 */
    private Integer status;

    /** 当前节点键 */
    private String currentNodeKey;
}
