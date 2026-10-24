package com.bluewhale.workflow.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 已通过流程实例摘要视图对象（档案同步内网接口契约）。
 *
 * <p>{@code finishedAt} 取实例 {@code updateTime}：审批通过时实例状态与更新时间同事务落库，
 * 平台未单独维护办结时间字段，故以此作为办结时刻，档案侧仅用于计算保管到期日。</p>
 *
 * @author BlueWhale
 */
@Data
public class ApprovedInstanceVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 实例ID */
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

    /** 发起人账号 */
    private String initiator;

    /** 实例状态（固定为 1 已通过） */
    private Integer status;

    /** 办结时间（取实例更新时间） */
    private LocalDateTime finishedAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
