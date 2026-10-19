package com.bluewhale.workflow.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 流程实例视图对象（字段名为冻结的 JSON 契约，不得改名）。
 *
 * @author BlueWhale
 */
@Data
public class ProcessInstanceVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 流程定义 ID */
    private Long definitionId;

    /** 流程定义编码 */
    private String definitionCode;

    /** 流程定义版本 */
    private Integer definitionVersion;

    /** 实例标题 */
    private String title;

    /** 业务键 */
    private String businessKey;

    /** 状态：0 运行中 / 1 已通过 / 2 已驳回 / 3 已撤销 */
    private Integer status;

    /** 当前审批节点下标（0 起） */
    private Integer currentIndex;

    /** 发起人账号 */
    private String initiator;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 该实例的任务列表（仅详情接口填充） */
    private List<ProcessTaskVO> tasks;
}
