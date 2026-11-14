package com.bluewhale.workflow.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 待办任务实体，对应表 {@code process_task}。
 *
 * <p>公共字段（id / create_time / update_time / deleted）继承自 {@link BaseEntity}，
 * 其余字段与 {@code db/init.sql} 中的 {@code process_task} 列逐字段对齐。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("process_task")
public class ProcessTaskPo extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 所属流程实例 ID */
    private Long instanceId;

    /** 审批节点下标（0 起） */
    private Integer nodeIndex;

    /** 节点名称 */
    private String nodeName;

    /** 建任务时的节点键（规范化模型用 key；legacy 用合成 key {@code n{i+1}}，可空） */
    private String nodeKey;

    /** 节点绑定的表单定义 ID（字符串承载，可空） */
    private String formId;

    /** 节点绑定的表单编码（可空） */
    private String formCode;

    /** 审批人账号 */
    private String assignee;

    /** 状态：0 待办 / 1 已同意 / 2 已驳回 / 3 已取消 */
    private Integer status;

    /** 审批意见 */
    private String comment;

    /** 并行分支父节点键（fork 网关键；非并行任务为空） */
    private String parentNodeKey;

    /** 任务类型：{@code COUNTER_SIGN} 会签 / 空 普通审批 */
    private String signType;

    /** 会签策略快照：{@code ALL} / {@code ANY} / {@code RATIO:n} */
    private String signPolicy;

    /** 原始责任人（该任务首次创建时的审批人，代理改派前；转办、委派后保持不变） */
    private String ownerId;

    /** 委派来源人（委派任务记录原审批人；非委派为空） */
    private String delegateFrom;

    /** 实际完成人用户ID（内网系统完成时记录调用方账号） */
    private Long operatorId;

    /** 操作类型：USER 用户 / SYSTEM 系统或回调 */
    private String operatorType;

    /** 操作来源：ESIGN_CALLBACK / MANUAL / SCHEDULER 等 */
    private String operatorSource;

    /** 最近一次内网完成事件ID（同一事件重复回调时用于幂等） */
    private String lastEventId;
}
