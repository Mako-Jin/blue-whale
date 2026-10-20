package com.bluewhale.workflow.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 待办任务视图对象（字段名为冻结的 JSON 契约，不得改名）。
 *
 * @author BlueWhale
 */
@Data
public class ProcessTaskVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 所属流程实例 ID */
    private Long instanceId;

    /** 审批节点下标（0 起） */
    private Integer nodeIndex;

    /** 节点名称 */
    private String nodeName;

    /** 建任务时的节点键（规范化模型用 key；legacy 用合成 key {@code n{i+1}}） */
    private String nodeKey;

    /** 节点绑定的表单定义 ID（字符串承载，待办/审批页据此回显表单） */
    private String formId;

    /** 节点绑定的表单编码 */
    private String formCode;

    /** 审批人账号 */
    private String assignee;

    /** 状态：0 待办 / 1 已同意 / 2 已驳回 / 3 已取消 */
    private Integer status;

    /** 审批意见 */
    private String comment;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
